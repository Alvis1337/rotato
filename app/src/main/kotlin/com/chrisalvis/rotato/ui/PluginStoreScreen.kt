package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.data.plugins.PluginStoreEntry
import com.chrisalvis.rotato.data.plugins.PluginManifest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StoreLoadingState { Loading, Loaded, Error }

class PluginStoreViewModel(app: Application) : AndroidViewModel(app) {
    private val pluginRepository = PluginRepository(app)
    private val prefs = LocalSourcesPreferences(app)

    companion object {
        val BUNDLED_IDS = setOf(
            "GELBOORU", "DANBOORU", "RULE34", "SAFEBOORU",
            "WALLHAVEN", "KONACHAN", "YANDERE", "ZEROCHAN", "REDDIT"
        )
    }

    private val _loadingState = MutableStateFlow(StoreLoadingState.Loading)
    val loadingState: StateFlow<StoreLoadingState> = _loadingState.asStateFlow()

    private val _storeEntries = MutableStateFlow<List<PluginStoreEntry>>(emptyList())
    val storeEntries: StateFlow<List<PluginStoreEntry>> = _storeEntries.asStateFlow()

    val installedManifests: StateFlow<List<PluginManifest>> = pluginRepository.installedManifests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _updateAvailable = MutableStateFlow<Set<String>>(emptySet())
    val updateAvailable: StateFlow<Set<String>> = _updateAvailable.asStateFlow()

    // Per-plugin install state: id -> state
    private val _installStates = MutableStateFlow<Map<String, InstallPluginState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallPluginState>> = _installStates.asStateFlow()

    private val _installErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val installErrors: StateFlow<Map<String, String>> = _installErrors.asStateFlow()

    // Install-from-URL state
    private val _urlInstallState = MutableStateFlow(InstallPluginState.IDLE)
    val urlInstallState: StateFlow<InstallPluginState> = _urlInstallState.asStateFlow()
    private val _urlInstallError = MutableStateFlow<String?>(null)
    val urlInstallError: StateFlow<String?> = _urlInstallError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loadingState.update { StoreLoadingState.Loading }
            try {
                val entries = pluginRepository.fetchStoreIndex()
                _storeEntries.update { entries }
                _updateAvailable.update { pluginRepository.checkForUpdates(entries) }
                _loadingState.update { StoreLoadingState.Loaded }
            } catch (_: Exception) {
                _loadingState.update { StoreLoadingState.Error }
            }
        }
    }

    fun install(entry: PluginStoreEntry) {
        viewModelScope.launch {
            _installStates.update { it + (entry.id to InstallPluginState.BUSY) }
            _installErrors.update { it - entry.id }
            try {
                val manifest = pluginRepository.installFromUrl(entry.manifestUrl)
                val currentSources = prefs.sources.first()
                if (currentSources.none { it.pluginId == manifest.id }) {
                    prefs.upsertSource(LocalSource(pluginId = manifest.id, enabled = false))
                }
                _updateAvailable.update { it - entry.id }
                _installStates.update { it + (entry.id to InstallPluginState.SUCCESS) }
            } catch (e: Exception) {
                _installErrors.update { it + (entry.id to (e.message ?: "Install failed")) }
                _installStates.update { it + (entry.id to InstallPluginState.ERROR) }
            }
            delay(3_000)
            _installStates.update { it - entry.id }
            _installErrors.update { it - entry.id }
        }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            _urlInstallState.update { InstallPluginState.BUSY }
            _urlInstallError.update { null }
            try {
                val manifest = pluginRepository.installFromUrl(url.trim())
                val currentSources = prefs.sources.first()
                if (currentSources.none { it.pluginId == manifest.id }) {
                    prefs.upsertSource(LocalSource(pluginId = manifest.id, enabled = false))
                }
                _urlInstallState.update { InstallPluginState.SUCCESS }
                // Refresh to show newly installed plugin
                refresh()
            } catch (e: Exception) {
                _urlInstallError.update { e.message ?: "Failed to install plugin" }
                _urlInstallState.update { InstallPluginState.ERROR }
            }
            delay(3_000)
            _urlInstallState.update { InstallPluginState.IDLE }
        }
    }

    fun uninstall(id: String) {
        viewModelScope.launch {
            prefs.sources.first()
                .filter { it.pluginId == id }
                .forEach { prefs.removeInstance(id, it.instanceId) }
            pluginRepository.uninstall(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(onNavigateBack: () -> Unit) {
    val vm: PluginStoreViewModel = viewModel()
    val loadingState by vm.loadingState.collectAsStateWithLifecycle()
    val storeEntries by vm.storeEntries.collectAsStateWithLifecycle()
    val installedManifests by vm.installedManifests.collectAsStateWithLifecycle()
    val updateAvailable by vm.updateAvailable.collectAsStateWithLifecycle()
    val installStates by vm.installStates.collectAsStateWithLifecycle()
    val installErrors by vm.installErrors.collectAsStateWithLifecycle()
    val urlInstallState by vm.urlInstallState.collectAsStateWithLifecycle()
    val urlInstallError by vm.urlInstallError.collectAsStateWithLifecycle()

    val installedIds = remember(installedManifests) { installedManifests.map { it.id }.toSet() }

    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    // Auto-dismiss dialog on successful URL install
    LaunchedEffect(urlInstallState) {
        if (urlInstallState == InstallPluginState.SUCCESS) {
            delay(1_000)
            showUrlDialog = false
            urlInput = ""
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = {
                if (urlInstallState != InstallPluginState.BUSY) {
                    showUrlDialog = false
                    urlInput = ""
                }
            },
            title = { Text("Install Plugin from URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the URL of a Rotato plugin manifest JSON file.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Manifest URL") },
                        placeholder = { Text("https://example.com/plugin.json") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = urlInstallState != InstallPluginState.BUSY,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    if (urlInstallState == InstallPluginState.ERROR && urlInstallError != null) {
                        Text(
                            urlInstallError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.installFromUrl(urlInput) },
                    enabled = urlInput.isNotBlank() && urlInstallState == InstallPluginState.IDLE
                ) {
                    if (urlInstallState == InstallPluginState.BUSY) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (urlInstallState == InstallPluginState.SUCCESS) "Installed!" else "Install")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrlDialog = false; urlInput = "" },
                    enabled = urlInstallState != InstallPluginState.BUSY
                ) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Store", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.refresh() }) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Install from URL card
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Install from URL", fontWeight = FontWeight.Medium)
                        Text(
                            "Have a plugin manifest URL? Install it directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showUrlDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Install from URL")
                        }
                    }
                }
            }

            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search plugins") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }

            when (loadingState) {
                StoreLoadingState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                StoreLoadingState.Error -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Failed to load plugin store", color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { vm.refresh() }) { Text("Retry") }
                        }
                    }
                }
                StoreLoadingState.Loaded -> {
                    val filteredEntries = if (searchQuery.isBlank()) storeEntries
                    else storeEntries.filter { entry ->
                        entry.name.contains(searchQuery, ignoreCase = true) ||
                            entry.description.contains(searchQuery, ignoreCase = true) ||
                            entry.tags.any { it.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredEntries.isEmpty()) {
                        item {
                            Text(
                                "No plugins found.",
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(filteredEntries, key = { it.id }) { entry ->
                        val isBundled = entry.isBundled || entry.id in PluginStoreViewModel.BUNDLED_IDS
                        val isInstalled = entry.id in installedIds
                        val hasUpdate = entry.id in updateAvailable
                        val installState = installStates[entry.id] ?: InstallPluginState.IDLE
                        val installError = installErrors[entry.id]

                        PluginStoreCard(
                            entry = entry,
                            isBundled = isBundled,
                            isInstalled = isInstalled,
                            hasUpdate = hasUpdate,
                            installState = installState,
                            installError = installError,
                            onInstall = { vm.install(entry) },
                            onUninstall = { vm.uninstall(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginStoreCard(
    entry: PluginStoreEntry,
    isBundled: Boolean,
    isInstalled: Boolean,
    hasUpdate: Boolean,
    installState: InstallPluginState,
    installError: String?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(entry.name, fontWeight = FontWeight.Medium)
                        if (isBundled) {
                            SuggestionChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Built-in", style = MaterialTheme.typography.labelSmall) },
                            )
                        } else if (hasUpdate) {
                            SuggestionChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Update", style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            )
                        } else if (isInstalled) {
                            SuggestionChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Installed", style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            )
                        }
                    }
                    if (entry.description.isNotBlank()) {
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.tags.isNotEmpty()) {
                        Text(
                            entry.tags.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        "by ${entry.author} · v${entry.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Action button
                if (!isBundled) {
                    when {
                        installState == InstallPluginState.BUSY -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        isInstalled && hasUpdate -> {
                            TextButton(onClick = onInstall) { Text("Update") }
                        }
                        isInstalled -> {
                            TextButton(
                                onClick = onUninstall,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Remove") }
                        }
                        else -> {
                            FilledTonalButton(onClick = onInstall) {
                                Text(if (installState == InstallPluginState.SUCCESS) "Installed!" else "Install")
                            }
                        }
                    }
                }
            }
            if (installError != null && installState == InstallPluginState.ERROR) {
                Text(
                    installError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
