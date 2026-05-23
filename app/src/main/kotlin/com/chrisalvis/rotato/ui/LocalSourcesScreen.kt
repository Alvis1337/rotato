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
import com.chrisalvis.rotato.data.SourceType
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import com.chrisalvis.rotato.data.plugins.SourcePluginRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LocalSourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = LocalSourcesPreferences(app)
    private val rotaPrefs = RotatoPreferences(app)

    val sources: StateFlow<List<LocalSource>> = prefs.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val health = SourceHealthTracker.health

    private val httpClient = OkHttpClient()

    private val _keyValidationState = MutableStateFlow<Map<SourceType, Boolean?>>(emptyMap())
    val keyValidationState: StateFlow<Map<SourceType, Boolean?>> = _keyValidationState.asStateFlow()
    private val latestWallhavenKey = MutableStateFlow("")

    // Key format: "${type.name}:${instanceId}" — unique per source instance
    private val _testingSource = MutableStateFlow<String?>(null)
    val testingSource: StateFlow<String?> = _testingSource.asStateFlow()

    fun setEnabled(type: SourceType, instanceId: String = "", enabled: Boolean) {
        viewModelScope.launch { prefs.update(type, instanceId, enabled = enabled) }
    }

    fun setCredentials(type: SourceType, instanceId: String = "", apiKey: String, apiUser: String) {
        viewModelScope.launch { prefs.update(type, instanceId, apiKey = apiKey, apiUser = apiUser) }
    }

    fun setTags(type: SourceType, instanceId: String = "", tags: String) {
        viewModelScope.launch { prefs.update(type, instanceId, tags = tags) }
    }

    fun setWallhavenPurity(type: SourceType, instanceId: String = "", purity: String) {
        viewModelScope.launch { prefs.update(type, instanceId, wallhavenPurity = purity) }
    }

    fun addRedditInstance(subreddit: String) {
        val trimmed = subreddit.trim().removePrefix("r/").trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { prefs.addInstance(SourceType.REDDIT, trimmed) }
    }

    fun removeInstance(type: SourceType, instanceId: String) {
        viewModelScope.launch { prefs.removeInstance(type, instanceId) }
    }

    fun validateWallhavenKey(apiKey: String) {
        val trimmedKey = apiKey.trim()
        latestWallhavenKey.value = trimmedKey
        _keyValidationState.update { it + (SourceType.WALLHAVEN to null) }
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
                    _keyValidationState.update { it + (SourceType.WALLHAVEN to isValid) }
                }
            } catch (_: Exception) {
                _keyValidationState.update { it + (SourceType.WALLHAVEN to false) }
            }
        }
    }

    fun testSource(source: LocalSource) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "${source.type.name}:${source.instanceId}"
            _testingSource.update { key }
            try {
                val plugin = SourcePluginRegistry.forType(source.type) ?: error("No plugin")
                val nsfw = rotaPrefs.nsfwMode.first()
                val results = plugin.fetch(source, "", emptyList(), nsfw, BrainrotFilters())
                if (results != null) SourceHealthTracker.recordSuccess(source.type)
                else throw Exception("No results returned")
            } catch (e: Exception) {
                SourceHealthTracker.recordError(source.type, e.message ?: "Unknown error")
            } finally {
                _testingSource.update { null }
            }
        }
    }

    fun disableAllSources() {
        viewModelScope.launch { prefs.disableAll() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSourcesScreen(onNavigateBack: () -> Unit) {
    val vm: LocalSourcesViewModel = viewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val healthMap by vm.health.collectAsStateWithLifecycle()
    val keyValidationState by vm.keyValidationState.collectAsStateWithLifecycle()
    val testingSource by vm.testingSource.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onSaved = { scope.launch { snackbarHostState.showSnackbar("Source updated") }; Unit }

    var showDisableAllConfirm by remember { mutableStateOf(false) }
    var showAddRedditDialog by remember { mutableStateOf(false) }
    var newSubreddit by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<Pair<SourceType, String>?>(null) }

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
    confirmRemove?.let { (type, instanceId) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove source?") },
            text = { Text("Remove this ${type.name.lowercase().replaceFirstChar { it.uppercase() }} source? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeInstance(type, instanceId)
                    confirmRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            }
        )
    }

    // Partition: non-Reddit free, Reddit instances, then premium
    val freeSources = sources.filter { !it.type.isPremium && it.type != SourceType.REDDIT }
    val redditSources = sources.filter { it.type == SourceType.REDDIT }
    val premiumSources = sources.filter { it.type.isPremium }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Sources", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            if (freeSources.isNotEmpty()) {
                item {
                    Text(
                        "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(freeSources, key = { "${it.type.name}:${it.instanceId}" }) { source ->
                    SourceCard(
                        source = source,
                        health = healthMap[source.type],
                        keyValidation = keyValidationState[source.type],
                        isTesting = testingSource == "${source.type.name}:${source.instanceId}",
                        onToggle = { vm.setEnabled(source.type, source.instanceId, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.type, source.instanceId, key, user) },
                        onSaveTags = { vm.setTags(source.type, source.instanceId, it) },
                        onSaveWallhavenPurity = { vm.setWallhavenPurity(source.type, source.instanceId, it) },
                        onValidateWallhavenKey = vm::validateWallhavenKey,
                        onTest = { vm.testSource(source) },
                        onSaved = onSaved,
                    )
                }
            }

            // Reddit multi-instance section
            item {
                Text(
                    "REDDIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(redditSources, key = { "${it.type.name}:${it.instanceId}" }) { source ->
                SourceCard(
                    source = source,
                    health = healthMap[source.type],
                    keyValidation = null,
                    isTesting = testingSource == "${source.type.name}:${source.instanceId}",
                    onToggle = { vm.setEnabled(source.type, source.instanceId, it) },
                    onSaveCredentials = { _, _ -> },
                    onSaveTags = { vm.setTags(source.type, source.instanceId, it) },
                    onSaveWallhavenPurity = {},
                    onValidateWallhavenKey = {},
                    onTest = { vm.testSource(source) },
                    onRemove = { confirmRemove = source.type to source.instanceId },
                    onSaved = onSaved,
                )
            }
            item {
                OutlinedButton(
                    onClick = { showAddRedditDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add subreddit")
                }
            }

            if (premiumSources.isNotEmpty()) {
                item {
                    Text(
                        "PREMIUM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(premiumSources, key = { "${it.type.name}:${it.instanceId}" }) { source ->
                    val plugin = source.type.plugin
                    val unlocked = plugin == null || PluginEntitlement.isUnlocked(plugin)
                    SourceCard(
                        source = source,
                        health = healthMap[source.type],
                        keyValidation = keyValidationState[source.type],
                        isPremium = true,
                        isLocked = !unlocked,
                        isTesting = testingSource == "${source.type.name}:${source.instanceId}",
                        onToggle = { if (unlocked) vm.setEnabled(source.type, source.instanceId, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.type, source.instanceId, key, user) },
                        onSaveTags = { vm.setTags(source.type, source.instanceId, it) },
                        onSaveWallhavenPurity = { vm.setWallhavenPurity(source.type, source.instanceId, it) },
                        onValidateWallhavenKey = vm::validateWallhavenKey,
                        onTest = { if (unlocked) vm.testSource(source) },
                        onSaved = onSaved,
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                if (sources.any { it.enabled }) {
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
    health: SourceHealth? = null,
    keyValidation: Boolean? = null,
    isPremium: Boolean = false,
    isLocked: Boolean = false,
    isTesting: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onSaveTags: (String) -> Unit,
    onSaveWallhavenPurity: (String) -> Unit = {},
    onValidateWallhavenKey: (String) -> Unit = {},
    onTest: () -> Unit = {},
    onRemove: (() -> Unit)? = null,
    onSaved: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKey by remember(source) { mutableStateOf(source.apiKey) }
    var apiUser by remember(source) { mutableStateOf(source.apiUser) }
    var tags by remember(source) { mutableStateOf(source.tags) }
    // Wallhaven purity bitmask: index 0=SFW, 1=Sketchy, 2=NSFW
    var puritySfw by remember(source) { mutableStateOf(source.wallhavenPurity.getOrElse(0) { '1' } == '1') }
    var puritySketchy by remember(source) { mutableStateOf(source.wallhavenPurity.getOrElse(1) { '1' } == '1') }
    var purityNsfw by remember(source) { mutableStateOf(source.wallhavenPurity.getOrElse(2) { '0' } == '1') }
    var showKey by remember { mutableStateOf(false) }
    var showHealthDetail by remember { mutableStateOf(false) }

    var showPremiumInfo by remember { mutableStateOf(false) }

    LaunchedEffect(source.type, expanded, apiKey) {
        if (source.type == SourceType.WALLHAVEN && expanded) {
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
                        val displayName = if (source.type == SourceType.REDDIT && source.instanceId.isNotBlank())
                            "r/${source.instanceId}" else source.type.displayName
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
                    val description = source.type.plugin?.description ?: run {
                        if (!source.type.safeContent) "May include adult content"
                        else if (!source.type.needsApiKey && !source.type.needsApiUser) "Works without credentials"
                        else null
                    }
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!source.type.safeContent) MaterialTheme.colorScheme.error
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
                    val plugin = source.type.plugin
                    if (source.enabled && plugin?.requiresCredentials == true &&
                        (source.apiKey.isBlank() || (plugin.needsApiUser && source.apiUser.isBlank()))) {
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
                val isReddit = source.type == SourceType.REDDIT
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
                if (source.type.needsApiUser) {
                    OutlinedTextField(
                        value = apiUser,
                        onValueChange = { apiUser = it },
                        label = { Text(source.type.apiUserLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = if (source.type.needsApiKey) ImeAction.Next else ImeAction.Done)
                    )
                }
                if (source.type.needsApiKey) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(source.type.apiKeyLabel) },
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
                    if (source.type == SourceType.WALLHAVEN && apiKey.isNotBlank()) {
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
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Invalid key",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Invalid key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
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
                if (source.type == SourceType.WALLHAVEN) {
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
                        if (source.type == SourceType.WALLHAVEN) {
                            if (apiKey.isNotBlank()) onValidateWallhavenKey(apiKey)
                            val p = "${if (puritySfw) '1' else '0'}${if (puritySketchy) '1' else '0'}${if (purityNsfw) '1' else '0'}"
                            onSaveWallhavenPurity(if (p == "000") "100" else p)
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

