package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.SourceHealth
import com.chrisalvis.rotato.data.SourceHealthTracker
import com.chrisalvis.rotato.data.plugins.FieldType
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.PluginExecutor
import com.chrisalvis.rotato.data.plugins.PluginManifest
import com.chrisalvis.rotato.data.plugins.PluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class InstallPluginState { IDLE, BUSY, SUCCESS, ERROR }

class LocalSourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = LocalSourcesPreferences(app)
    private val pluginRepository = PluginRepository(app)
    private val rotaPrefs = RotatoPreferences(app)

    val sources: StateFlow<List<LocalSource>> = prefs.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val health = SourceHealthTracker.health

    val manifests: StateFlow<List<PluginManifest>> = pluginRepository.manifests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installedManifests: StateFlow<List<PluginManifest>> = pluginRepository.installedManifests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val httpClient = OkHttpClient()

    private val _keyValidationState = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val keyValidationState: StateFlow<Map<String, Boolean?>> = _keyValidationState.asStateFlow()
    private val _keyNetworkError = MutableStateFlow(false)
    val keyNetworkError: StateFlow<Boolean> = _keyNetworkError.asStateFlow()
    private val latestWallhavenKey = MutableStateFlow("")

    // Key format: "${pluginId}:${instanceId}" — unique per source instance
    private val _testingSource = MutableStateFlow<String?>(null)
    val testingSource: StateFlow<String?> = _testingSource.asStateFlow()

    private val _installState = MutableStateFlow(InstallPluginState.IDLE)
    val installState: StateFlow<InstallPluginState> = _installState.asStateFlow()
    private val _installError = MutableStateFlow<String?>(null)
    val installError: StateFlow<String?> = _installError.asStateFlow()

    /** True if the one-time plugin-system intro banner should be shown. */
    val showMigrationNotice: StateFlow<Boolean> = rotaPrefs.pluginSystemIntroShown
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissMigrationNotice() {
        viewModelScope.launch { rotaPrefs.dismissPluginSystemIntro() }
    }

    fun setEnabled(pluginId: String, instanceId: String = "", enabled: Boolean) {
        viewModelScope.launch { prefs.update(pluginId, instanceId, enabled = enabled) }
    }

    fun setCredentials(pluginId: String, instanceId: String = "", apiKey: String, apiUser: String) {
        viewModelScope.launch { prefs.update(pluginId, instanceId, apiKey = apiKey, apiUser = apiUser) }
    }

    fun setTags(pluginId: String, instanceId: String = "", tags: String) {
        viewModelScope.launch { prefs.update(pluginId, instanceId, tags = tags) }
    }

    fun setWallhavenPurity(pluginId: String, instanceId: String = "", purity: String) {
        viewModelScope.launch { prefs.update(pluginId, instanceId, wallhavenPurity = purity) }
    }

    fun setBaseUrl(pluginId: String, instanceId: String = "", baseUrl: String) {
        viewModelScope.launch { prefs.update(pluginId, instanceId, baseUrl = baseUrl) }
    }

    fun setExtraConfig(pluginId: String, instanceId: String = "", key: String, value: String) {
        viewModelScope.launch {
            val current = prefs.sources.first()
                .firstOrNull { it.pluginId == pluginId && it.instanceId == instanceId }
                ?: return@launch
            prefs.upsertSource(current.copy(extraConfig = current.extraConfig + (key to value)))
        }
    }

    fun addRedditInstance(subreddit: String) {
        val trimmed = subreddit.trim().removePrefix("r/").trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { prefs.addInstance("REDDIT", trimmed) }
    }

    fun removeInstance(pluginId: String, instanceId: String) {
        viewModelScope.launch { prefs.removeInstance(pluginId, instanceId) }
    }

    fun installPlugin(url: String) {
        viewModelScope.launch {
            _installState.update { InstallPluginState.BUSY }
            _installError.update { null }
            try {
                val manifest = pluginRepository.installFromUrl(url.trim())
                val currentSources = prefs.sources.first()
                if (currentSources.none { it.pluginId == manifest.id }) {
                    prefs.upsertSource(LocalSource(pluginId = manifest.id, enabled = false))
                }
                _installState.update { InstallPluginState.SUCCESS }
            } catch (e: Exception) {
                _installError.update { e.message ?: "Failed to install plugin" }
                _installState.update { InstallPluginState.ERROR }
            }
            delay(3_000)
            _installState.update { InstallPluginState.IDLE }
        }
    }

    fun uninstallPlugin(id: String) {
        viewModelScope.launch {
            prefs.sources.first()
                .filter { it.pluginId == id }
                .forEach { prefs.removeInstance(id, it.instanceId) }
            pluginRepository.uninstall(id)
        }
    }

    fun validateWallhavenKey(apiKey: String) {
        val trimmedKey = apiKey.trim()
        latestWallhavenKey.value = trimmedKey
        _keyNetworkError.update { false }
        _keyValidationState.update { it + ("WALLHAVEN" to null) }
        if (trimmedKey.isBlank()) return

        viewModelScope.launch {
            try {
                val isValid = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://wallhaven.cc/api/v1/settings?apikey=$trimmedKey")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        when (response.code) {
                            200 -> true
                            401, 403 -> false
                            else -> null
                        }
                    }
                }
                if (isValid != null && latestWallhavenKey.value == trimmedKey) {
                    _keyNetworkError.update { false }
                    _keyValidationState.update { it + ("WALLHAVEN" to isValid) }
                }
            } catch (_: Exception) {
                _keyNetworkError.update { true }
                _keyValidationState.update { it + ("WALLHAVEN" to false) }
            }
        }
    }

    fun testSource(source: LocalSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "${source.pluginId}:${source.instanceId}"
            _testingSource.update { key }
            try {
                val manifest = pluginRepository.getManifest(source.pluginId) ?: error("No manifest for ${source.pluginId}")
                val nsfw = rotaPrefs.nsfwMode.first()
                val results = PluginExecutor.fetch(manifest, source, "", emptyList(), nsfw, BrainrotFilters())
                if (results != null) SourceHealthTracker.recordSuccess(source.pluginId)
                else throw Exception("No results returned")
            } catch (e: Exception) {
                SourceHealthTracker.recordError(source.pluginId, e.message ?: "Unknown error")
            } finally {
                _testingSource.update { null }
            }
        }
    }

    fun disableAllSources() {
        viewModelScope.launch { prefs.disableAll() }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch { prefs.setPluginEnabled(pluginId, enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSourcesScreen(onNavigateBack: () -> Unit, onNavigateToPluginStore: () -> Unit) {
    val vm: LocalSourcesViewModel = viewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val healthMap by vm.health.collectAsStateWithLifecycle()
    val keyValidationState by vm.keyValidationState.collectAsStateWithLifecycle()
    val keyNetworkError by vm.keyNetworkError.collectAsStateWithLifecycle()
    val testingSource by vm.testingSource.collectAsStateWithLifecycle()
    val manifests by vm.manifests.collectAsStateWithLifecycle()
    val installedManifests by vm.installedManifests.collectAsStateWithLifecycle()
    val showMigrationNotice by vm.showMigrationNotice.collectAsStateWithLifecycle()

    val manifestMap = remember(manifests) { manifests.associateBy { it.id } }
    val sourcesByPlugin = remember(sources) { sources.groupBy { it.pluginId } }
    val activeSources = remember(sources) { sources.filter { it.enabled } }
    val disabledSources = remember(sources) { sources.filterNot { it.enabled } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onSaved = { scope.launch { snackbarHostState.showSnackbar("Source updated") }; Unit }

    var showDisableAllConfirm by remember { mutableStateOf(false) }
    var showAddRedditDialog by remember { mutableStateOf(false) }
    var newSubreddit by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showDisabledSources by remember { mutableStateOf(false) }

    if (showAddRedditDialog) {
        val redditFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(100)
            runCatching { redditFocus.requestFocus() }
        }
        val doAdd = {
            vm.addRedditInstance(newSubreddit)
            showAddRedditDialog = false
            newSubreddit = ""
        }
        AlertDialog(
            onDismissRequest = { showAddRedditDialog = false; newSubreddit = "" },
            title = { Text("Add subreddit") },
            text = {
                OutlinedTextField(
                    value = newSubreddit,
                    onValueChange = { newSubreddit = it },
                    label = { Text("Subreddit") },
                    placeholder = { Text("e.g. wallpapers") },
                    prefix = { Text("r/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (newSubreddit.trim().isNotBlank()) doAdd() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(redditFocus),
                )
            },
            confirmButton = {
                TextButton(onClick = doAdd, enabled = newSubreddit.trim().isNotBlank()) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRedditDialog = false; newSubreddit = "" }) { Text("Cancel") }
            }
        )
    }
    if (showDisableAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableAllConfirm = false },
            title = { Text("Disable all sources?") },
            text = { Text("This will disable every source. You can re-enable them individually at any time.") },
            confirmButton = {
                TextButton(onClick = { vm.disableAllSources(); showDisableAllConfirm = false }) {
                    Text("Disable all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableAllConfirm = false }) { Text("Cancel") }
            }
        )
    }
    confirmRemove?.let { (pluginId, instanceId) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove source?") },
            text = { Text("Remove this source? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeInstance(pluginId, instanceId)
                    confirmRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Sources", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToPluginStore) {
                        Text("Plugin Store")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Enable image sources for local discovery. Sources with API credentials fetch higher-quality or restricted content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showMigrationNotice) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Welcome to the new plugin system!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "Your existing sources have been automatically migrated. You can now install additional plugins from the Plugin Store.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { vm.dismissMigrationNotice() }) {
                                        Text("Got it", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    TextButton(onClick = { vm.dismissMigrationNotice(); onNavigateToPluginStore() }) {
                                        Text("Browse plugins", color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = installedManifests.size.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "plugin${if (installedManifests.size == 1) "" else "s"} installed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        VerticalDivider(modifier = Modifier.height(48.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSources.size.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "source${if (activeSources.size == 1) "" else "s"} active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "INSTALLED PLUGINS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (installedManifests.isEmpty()) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No plugins installed yet", fontWeight = FontWeight.Medium)
                            Text(
                                "Install a plugin from the Plugin Store to add local sources.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(installedManifests, key = { "installed:${it.id}" }) { manifest ->
                    val pluginSources = sourcesByPlugin[manifest.id].orEmpty()
                    val sourceCount = pluginSources.size
                    val activeCount = pluginSources.count { it.enabled }
                    val hasActiveSources = activeCount > 0
                    val unlocked = !manifest.isPremium || PluginEntitlement.isUnlocked(manifest)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(manifest.name, fontWeight = FontWeight.Medium)
                                    val description = buildString {
                                        append("$activeCount of $sourceCount active")
                                        if (manifest.description.isNotBlank()) {
                                            append(" • ")
                                            append(manifest.description)
                                        }
                                    }
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SuggestionChip(
                                            onClick = {},
                                            enabled = false,
                                            label = { Text("$sourceCount source${if (sourceCount == 1) "" else "s"}") }
                                        )
                                        if (manifest.isPremium) {
                                            SuggestionChip(
                                                onClick = {},
                                                enabled = false,
                                                label = { Text(if (unlocked) "Premium" else "Locked") },
                                                icon = if (!unlocked) {
                                                    { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                                Switch(
                                    checked = hasActiveSources,
                                    onCheckedChange = { vm.setPluginEnabled(manifest.id, it) },
                                    enabled = sourceCount > 0 && unlocked
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { vm.setPluginEnabled(manifest.id, activeCount != sourceCount) },
                                    enabled = sourceCount > 0 && unlocked
                                ) {
                                    Text(if (activeCount == sourceCount && sourceCount > 0) "Disable all" else "Enable all")
                                }
                                if (manifest.id == "REDDIT") {
                                    OutlinedButton(onClick = { showAddRedditDialog = true }) {
                                        Text("Add subreddit")
                                    }
                                }
                                TextButton(
                                    onClick = { vm.uninstallPlugin(manifest.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Uninstall")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "ACTIVE SOURCES (${activeSources.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (activeSources.isEmpty()) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No active sources yet. Enable a source below to start fetching images.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(activeSources, key = { "${it.pluginId}:${it.instanceId}" }) { source ->
                    val manifest = manifestMap[source.pluginId]
                    val isPremium = manifest?.isPremium == true
                    val unlocked = manifest == null || !isPremium || PluginEntitlement.isUnlocked(manifest)
                    SourceCard(
                        source = source,
                        manifest = manifest,
                        health = healthMap[source.pluginId],
                        keyValidation = if (source.pluginId == "REDDIT") null else keyValidationState[source.pluginId],
                        keyNetworkError = keyNetworkError,
                        isPremium = isPremium,
                        isLocked = !unlocked,
                        isTesting = testingSource == "${source.pluginId}:${source.instanceId}",
                        onToggle = { vm.setEnabled(source.pluginId, source.instanceId, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.pluginId, source.instanceId, key, user) },
                        onSaveTags = { vm.setTags(source.pluginId, source.instanceId, it) },
                        onSaveWallhavenPurity = { vm.setWallhavenPurity(source.pluginId, source.instanceId, it) },
                        onSaveBaseUrl = { vm.setBaseUrl(source.pluginId, source.instanceId, it) },
                        onSaveExtraConfig = { key, value -> vm.setExtraConfig(source.pluginId, source.instanceId, key, value) },
                        onValidateWallhavenKey = if (source.pluginId == "REDDIT") ({ _ -> }) else vm::validateWallhavenKey,
                        onTest = { if (unlocked) vm.testSource(source) },
                        onRemove = if (source.pluginId == "REDDIT") {
                            { confirmRemove = source.pluginId to source.instanceId }
                        } else null,
                        onSaved = onSaved,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "DISABLED SOURCES (${disabledSources.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (disabledSources.isNotEmpty()) {
                        TextButton(onClick = { showDisabledSources = !showDisabledSources }) {
                            Text(if (showDisabledSources) "Collapse" else "Expand")
                        }
                    }
                }
            }

            when {
                disabledSources.isEmpty() -> item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No disabled sources.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                !showDisabledSources -> item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Disabled sources are hidden until you expand this section.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> items(disabledSources, key = { "disabled:${it.pluginId}:${it.instanceId}" }) { source ->
                    val manifest = manifestMap[source.pluginId]
                    val isPremium = manifest?.isPremium == true
                    val unlocked = manifest == null || !isPremium || PluginEntitlement.isUnlocked(manifest)
                    SourceCard(
                        source = source,
                        manifest = manifest,
                        health = healthMap[source.pluginId],
                        keyValidation = if (source.pluginId == "REDDIT") null else keyValidationState[source.pluginId],
                        keyNetworkError = keyNetworkError,
                        isPremium = isPremium,
                        isLocked = !unlocked,
                        isTesting = testingSource == "${source.pluginId}:${source.instanceId}",
                        onToggle = { vm.setEnabled(source.pluginId, source.instanceId, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.pluginId, source.instanceId, key, user) },
                        onSaveTags = { vm.setTags(source.pluginId, source.instanceId, it) },
                        onSaveWallhavenPurity = { vm.setWallhavenPurity(source.pluginId, source.instanceId, it) },
                        onSaveBaseUrl = { vm.setBaseUrl(source.pluginId, source.instanceId, it) },
                        onSaveExtraConfig = { key, value -> vm.setExtraConfig(source.pluginId, source.instanceId, key, value) },
                        onValidateWallhavenKey = if (source.pluginId == "REDDIT") ({ _ -> }) else vm::validateWallhavenKey,
                        onTest = { if (unlocked) vm.testSource(source) },
                        onRemove = if (source.pluginId == "REDDIT") {
                            { confirmRemove = source.pluginId to source.instanceId }
                        } else null,
                        onSaved = onSaved,
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                if (activeSources.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showDisableAllConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disable all sources")
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: LocalSource,
    manifest: PluginManifest? = null,
    health: SourceHealth? = null,
    keyValidation: Boolean? = null,
    keyNetworkError: Boolean = false,
    isPremium: Boolean = false,
    isLocked: Boolean = false,
    isTesting: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onSaveTags: (String) -> Unit,
    onSaveWallhavenPurity: (String) -> Unit = {},
    onSaveBaseUrl: (String) -> Unit = {},
    onSaveExtraConfig: (String, String) -> Unit = { _, _ -> },
    onValidateWallhavenKey: (String) -> Unit = {},
    onTest: () -> Unit = {},
    onRemove: (() -> Unit)? = null,
    onSaved: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKey by remember(source) { mutableStateOf(source.apiKey) }
    var apiUser by remember(source) { mutableStateOf(source.apiUser) }
    var tags by remember(source) { mutableStateOf(source.tags) }
    var baseUrl by remember(source) { mutableStateOf(source.baseUrl) }
    val extraConfig = remember(source, manifest?.configFields) {
        mutableStateMapOf<String, String>().apply {
            manifest?.configFields?.forEach { field ->
                put(
                    field.key,
                    source.extraConfig[field.key] ?: if (field.type == FieldType.TOGGLE) "false" else "",
                )
            }
        }
    }
    // Wallhaven purity bitmask: index 0=SFW, 1=Sketchy, 2=NSFW
    var puritySfw by remember(source) { mutableStateOf(source.wallhavenPurity.getOrElse(0) { '1' } == '1') }
    var puritySketchy by remember(source) { mutableStateOf(source.wallhavenPurity.getOrElse(1) { '1' } == '1') }
    var purityNsfw by remember(source) { mutableStateOf(source.wallhavenPurity.getOrElse(2) { '0' } == '1') }
    var showKey by remember { mutableStateOf(false) }
    var showHealthDetail by remember { mutableStateOf(false) }

    var showPremiumInfo by remember { mutableStateOf(false) }

    LaunchedEffect(source.pluginId, expanded, apiKey) {
        if (source.pluginId == "WALLHAVEN" && expanded) {
            delay(800)
            if (apiKey.isNotBlank()) onValidateWallhavenKey(apiKey)
        }
    }

    if (showPremiumInfo) {
        AlertDialog(
            onDismissRequest = { showPremiumInfo = false },
            title = { Text("Premium source") },
            text = { Text("This source is part of the premium tier. In-app purchases are coming in a future update.") },
            confirmButton = { TextButton(onClick = { showPremiumInfo = false }) { Text("OK") } }
        )
    }

    // Health dot color: grey = untested, green = last fetch ok, red = last fetch errored
    val healthDotColor = when {
        health == null || !health.hasData -> Color.Gray.copy(alpha = 0.4f)
        health.isHealthy -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val displayName = if (source.pluginId == "REDDIT" && source.instanceId.isNotBlank())
                            "r/${source.instanceId}" else manifest?.name ?: source.pluginId.lowercase().replaceFirstChar { it.uppercase() }
                        Text(displayName, fontWeight = FontWeight.Medium)
                        if (isPremium) {
                            SuggestionChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Premium", style = MaterialTheme.typography.labelSmall) },
                                icon = if (isLocked) {
                                    { Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(12.dp)) }
                                } else null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            )
                        }
                    }
                    val description = manifest?.description?.ifBlank { null } ?: run {
                        if (manifest?.safeContent == false) "May include adult content"
                        else if (manifest?.needsApiKey == false && manifest?.needsApiUser == false) "Works without credentials"
                        else null
                    }
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (manifest?.safeContent == false) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.outline
                        )
                    }
                    // Last error message (tappable)
                    if (source.enabled && !isLocked && health != null && health.hasData && !health.isHealthy) {
                        TextButton(
                            onClick = { showHealthDetail = !showHealthDetail },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                if (showHealthDetail) health.lastError else "Last fetch failed — tap for details",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (source.tags.isNotBlank()) {
                        Text("Tags: ${source.tags}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    // Warn when a source that strictly requires credentials is enabled without them
                    if (source.enabled && manifest?.requiresCredentials == true &&
                        (source.apiKey.isBlank() || (manifest?.needsApiUser == true && source.apiUser.isBlank()))) {
                        Text(
                            "⚠ Credentials required — this source won't fetch until configured",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val healthDesc = when {
                        health == null || !health.hasData -> "Status: never fetched"
                        health.isHealthy -> "Status: healthy"
                        else -> "Status: error"
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(healthDotColor, CircleShape)
                            .semantics { contentDescription = healthDesc }
                    )
                    if (!isLocked) {
                        if (source.enabled) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = onTest) { Text("Test") }
                            }
                        }
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Close" else "Configure")
                        }
                    } else {
                        Text(
                            "Coming soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = onToggle,
                        enabled = !isLocked
                    )
                }
            }

            if (expanded && !isLocked) {
                HorizontalDivider()
                val isReddit = source.pluginId == "REDDIT"
                if (!isReddit) {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags / Query") },
                        placeholder = { Text("e.g. scenery landscape (leave blank for global search)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Space-separated tags used only for this source") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                if (manifest?.instanceUrlConfigurable == true) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Instance URL") },
                        placeholder = { Text(manifest?.defaultBaseUrl ?: "https://...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Override the default API endpoint") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                    )
                }
                manifest?.configFields?.forEach { field ->
                    when (field.type) {
                        FieldType.TOGGLE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(field.label, style = MaterialTheme.typography.bodyMedium)
                                        if (field.hint.isNotBlank()) {
                                            Text(
                                                field.hint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = extraConfig[field.key]?.toBooleanStrictOrNull() ?: false,
                                        onCheckedChange = { extraConfig[field.key] = it.toString() },
                                    )
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = extraConfig[field.key].orEmpty(),
                                onValueChange = { extraConfig[field.key] = it },
                                label = { Text(field.label) },
                                placeholder = { if (field.placeholder.isNotBlank()) Text(field.placeholder) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = field.type != FieldType.TAGS,
                                supportingText = {
                                    val helper = buildList {
                                        if (field.required) add("Required")
                                        if (field.hint.isNotBlank()) add(field.hint)
                                    }.joinToString(" • ")
                                    if (helper.isNotBlank()) Text(helper)
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (field.type) {
                                        FieldType.URL -> KeyboardType.Uri
                                        FieldType.PASSWORD -> KeyboardType.Password
                                        else -> KeyboardType.Text
                                    },
                                    imeAction = ImeAction.Done,
                                ),
                                visualTransformation = if (field.type == FieldType.PASSWORD) {
                                    PasswordVisualTransformation()
                                } else {
                                    VisualTransformation.None
                                },
                            )
                        }
                    }
                }
                if (manifest?.needsApiUser == true) {
                    OutlinedTextField(
                        value = apiUser,
                        onValueChange = { apiUser = it },
                        label = { Text(manifest?.apiUserLabel ?: "Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = if (manifest?.needsApiKey == true) ImeAction.Next else ImeAction.Done)
                    )
                }
                if (manifest?.needsApiKey == true) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(manifest?.apiKeyLabel ?: "API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    if (source.pluginId == "WALLHAVEN" && apiKey.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when (keyValidation) {
                                true -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Key valid",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Key valid",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                                false -> {
                                    val isNetworkErr = keyNetworkError
                                    Icon(
                                        imageVector = if (isNetworkErr) Icons.Default.Warning else Icons.Default.Close,
                                        contentDescription = if (isNetworkErr) "Network error" else "Invalid key",
                                        tint = if (isNetworkErr) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        if (isNetworkErr) "Can't verify — check connection" else "Invalid key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isNetworkErr) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                    )
                                }
                                null -> {
                                    Text(
                                        "Verifying...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                // Wallhaven-specific purity picker
                if (source.pluginId == "WALLHAVEN") {
                    Text("Content purity", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = puritySfw, onCheckedChange = { puritySfw = it })
                            Text("SFW", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = puritySketchy, onCheckedChange = { puritySketchy = it })
                            Text("Sketchy", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = purityNsfw, onCheckedChange = { purityNsfw = it })
                            Text("NSFW", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "NSFW requires a Wallhaven API key. The global NSFW toggle can override this.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (onRemove != null) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onRemove != null) {
                        TextButton(
                            onClick = onRemove,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove")
                        }
                    }
                    OutlinedButton(onClick = {
                        onSaveTags(tags.trim())
                        onSaveCredentials(apiKey, apiUser)
                        if (source.pluginId == "WALLHAVEN") {
                            if (apiKey.isNotBlank()) onValidateWallhavenKey(apiKey)
                            val p = "${if (puritySfw) '1' else '0'}${if (puritySketchy) '1' else '0'}${if (purityNsfw) '1' else '0'}"
                            onSaveWallhavenPurity(if (p == "000") "100" else p)
                        }
                        onSaveBaseUrl(baseUrl.trim())
                        manifest?.configFields?.forEach { field ->
                            onSaveExtraConfig(field.key, extraConfig[field.key]?.trim().orEmpty())
                        }
                        expanded = false
                        onSaved()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

