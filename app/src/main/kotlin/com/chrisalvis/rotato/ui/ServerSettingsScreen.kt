package com.chrisalvis.rotato.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisalvis.rotato.data.ServerConfig
import com.chrisalvis.rotato.data.ServerFeed
import com.chrisalvis.rotato.data.SourceRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onNavigateBack: () -> Unit,
    vm: ServerSettingsViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snackMessage by vm.snackMessage.collectAsStateWithLifecycle()
    val testResults by vm.testResults.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importBackup(context, it) } }

    LaunchedEffect(Unit) {
        vm.load()
    }

    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Default.Upload, contentDescription = "Import backup")
                    }
                    IconButton(onClick = { exportLauncher.launch("rotato-backup.json") }) {
                        Icon(Icons.Default.Download, contentDescription = "Export backup")
                    }
                    if (state is ServerSettingsState.Loaded || state is ServerSettingsState.Error) {
                        IconButton(onClick = { vm.load() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AnimatedContent(
            targetState = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "server-settings-content"
        ) { currentState ->
            when (currentState) {
                is ServerSettingsState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ServerSettingsState.NoFeed -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No server connected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Add an animebacks server feed in the Discover tab first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ServerSettingsState.Error -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text("Failed to connect", style = MaterialTheme.typography.titleMedium)
                            Text(
                                currentState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { vm.load() }) { Text("Retry") }
                        }
                    }
                }
                is ServerSettingsState.Loaded -> {
                    ServerSettingsContent(
                        loaded = currentState,
                        saving = saving,
                        testResults = testResults,
                        onSave = { config, sources -> vm.saveAll(config, sources) },
                        onGenerateApiKey = { vm.generateApiKey() },
                        onTestSource = { name, key, user -> vm.testSource(name, key, user) },
                        onCreateFeed = { slug, name -> vm.createFeed(slug, name) },
                        onDeleteFeed = { vm.deleteFeed(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSettingsContent(
    loaded: ServerSettingsState.Loaded,
    saving: Boolean,
    testResults: Map<String, Boolean?>,
    onSave: (ServerConfig, List<SourceRow>) -> Unit,
    onGenerateApiKey: () -> Unit,
    onTestSource: (String, String, String) -> Unit,
    onCreateFeed: (String, String) -> Unit,
    onDeleteFeed: (Int) -> Unit
) {
    // Initialize once per screen visit; don't re-key on every field change.
    // The feedApiKey is synced separately so generating a new key doesn't wipe unsaved edits.
    var config by remember { mutableStateOf(loaded.config) }
    var sources by remember { mutableStateOf(loaded.sources) }

    // Only sync feedApiKey when it's externally updated (e.g. after generateApiKey())
    LaunchedEffect(loaded.config.feedApiKey) {
        if (config.feedApiKey != loaded.config.feedApiKey) {
            config = config.copy(feedApiKey = loaded.config.feedApiKey)
        }
    }

    val clipboardManager = LocalClipboardManager.current
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showCreateFeed by remember { mutableStateOf(false) }
    var newFeedSlug by remember { mutableStateOf("") }
    var newFeedName by remember { mutableStateOf("") }
    var feedToDelete by remember { mutableStateOf<ServerFeed?>(null) }

    if (showCreateFeed) {
        AlertDialog(
            onDismissRequest = { showCreateFeed = false },
            title = { Text("New Feed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newFeedName,
                        onValueChange = { newFeedName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newFeedSlug,
                        onValueChange = { newFeedSlug = it.lowercase().replace(" ", "-") },
                        label = { Text("Slug (URL path)") },
                        placeholder = { Text("e.g. my-feed") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateFeed(newFeedSlug, newFeedName)
                        newFeedSlug = ""
                        newFeedName = ""
                        showCreateFeed = false
                    },
                    enabled = newFeedSlug.isNotBlank() && newFeedName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFeed = false }) { Text("Cancel") }
            }
        )
    }

    feedToDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title = { Text("Delete feed?") },
            text = { Text("\"${feed.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteFeed(feed.id); feedToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { feedToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Connection info
        ServerSection(title = "Connected Server") {
            Text(
                loaded.serverUrl,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // API Key
        ServerSection(title = "API Key") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (config.feedApiKey.isBlank()) "(none)" else config.feedApiKey,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    label = { Text("Feed API Key") }
                )
                if (config.feedApiKey.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(config.feedApiKey))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
            OutlinedButton(
                onClick = onGenerateApiKey,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (config.feedApiKey.isBlank()) "Generate API Key" else "Regenerate API Key")
            }
            if (config.feedApiKey.isBlank()) {
                Text(
                    "Without an API key, anyone who can reach your server can modify settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        // Sources
        ServerSection(title = "Sources") {
            sources.forEachIndexed { idx, source ->
                SourceEditor(
                    source = source,
                    testResult = testResults[source.name],
                    onToggle = { enabled ->
                        sources = sources.toMutableList().also { it[idx] = it[idx].copy(enabled = enabled) }
                    },
                    onApiKeyChange = { key ->
                        sources = sources.toMutableList().also { it[idx] = it[idx].copy(apiKey = key) }
                    },
                    onApiUserChange = { user ->
                        sources = sources.toMutableList().also { it[idx] = it[idx].copy(apiUser = user) }
                    },
                    onTest = { onTestSource(source.name, source.apiKey, source.apiUser) }
                )
                if (idx < sources.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }

        HorizontalDivider()

        // Content filters
        ServerSection(title = "Content Filters") {
            // Sorting
            var sortExpanded by remember { mutableStateOf(false) }
            val sortOptions = listOf(
                "relevance" to "Relevance",
                "date_added" to "Date Added",
                "views" to "Most Viewed",
                "favorites" to "Most Favorited",
                "toplist" to "Top List",
                "random" to "Random"
            )
            ExposedDropdownMenuBox(
                expanded = sortExpanded,
                onExpandedChange = { sortExpanded = it }
            ) {
                OutlinedTextField(
                    value = sortOptions.firstOrNull { it.first == config.sorting }?.second ?: config.sorting,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sort By") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    sortOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { config = config.copy(sorting = value); sortExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Min resolution
            var resExpanded by remember { mutableStateOf(false) }
            val resOptions = listOf(
                "1280x720" to "1280×720 (720p)",
                "1920x1080" to "1920×1080 (1080p)",
                "2560x1440" to "2560×1440 (1440p)",
                "3840x2160" to "3840×2160 (4K)"
            )
            ExposedDropdownMenuBox(
                expanded = resExpanded,
                onExpandedChange = { resExpanded = it }
            ) {
                OutlinedTextField(
                    value = resOptions.firstOrNull { it.first == config.minResolution }?.second ?: config.minResolution,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Min Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = resExpanded, onDismissRequest = { resExpanded = false }) {
                    resOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { config = config.copy(minResolution = value); resExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Aspect ratio
            var aspectExpanded by remember { mutableStateOf(false) }
            val aspectOptions = listOf(
                "" to "Any",
                "16x9" to "16:9",
                "16x10" to "16:10",
                "21x9" to "21:9 Ultrawide",
                "32x9" to "32:9 Super Ultrawide",
                "mobile" to "Mobile (portrait)"
            )
            ExposedDropdownMenuBox(
                expanded = aspectExpanded,
                onExpandedChange = { aspectExpanded = it }
            ) {
                OutlinedTextField(
                    value = aspectOptions.firstOrNull { it.first == config.aspectRatio }?.second ?: "Any",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Aspect Ratio") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aspectExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = aspectExpanded, onDismissRequest = { aspectExpanded = false }) {
                    aspectOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { config = config.copy(aspectRatio = value); aspectExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search suffix
            var suffixExpanded by remember { mutableStateOf(false) }
            val suffixOptions = listOf(
                "" to "None",
                "landscape" to "+ landscape",
                "scenery" to "+ scenery",
                "wallpaper" to "+ wallpaper",
                "background" to "+ background",
                "art" to "+ art"
            )
            ExposedDropdownMenuBox(
                expanded = suffixExpanded,
                onExpandedChange = { suffixExpanded = it }
            ) {
                OutlinedTextField(
                    value = suffixOptions.firstOrNull { it.first == config.searchSuffix }?.second ?: config.searchSuffix,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Search Suffix") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = suffixExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = suffixExpanded, onDismissRequest = { suffixExpanded = false }) {
                    suffixOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { config = config.copy(searchSuffix = value); suffixExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // NSFW toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("NSFW Content")
                    Text(
                        "Include adult-rated images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.nsfwMode,
                    onCheckedChange = { config = config.copy(nsfwMode = it) }
                )
            }
        }

        HorizontalDivider()

        // MAL Settings
        ServerSection(title = "MyAnimeList OAuth") {
            OutlinedTextField(
                value = config.malClientId,
                onValueChange = { config = config.copy(malClientId = it) },
                label = { Text("Client ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.malClientSecret,
                onValueChange = { config = config.copy(malClientSecret = it) },
                label = { Text("Client Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.redirectUri,
                onValueChange = { config = config.copy(redirectUri = it) },
                label = { Text("Redirect URI") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
        }

        HorizontalDivider()

        // Feeds
        ServerSection(title = "Feeds") {
            loaded.feeds.forEach { feed ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feed.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "/${feed.slug}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { feedToDelete = feed }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete feed",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            OutlinedButton(
                onClick = { showCreateFeed = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Feed")
            }
        }

        HorizontalDivider()

        // Save button
        Button(
            onClick = { onSave(config, sources) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Save Settings")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SourceEditor(
    source: SourceRow,
    testResult: Boolean?,
    onToggle: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiUserChange: (String) -> Unit,
    onTest: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = source.enabled, onCheckedChange = onToggle)
                    Text(
                        source.name.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall
                    )
                    testResult?.let {
                        Icon(
                            if (it) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                }
            }

            if (expanded) {
                if (source.name == "wallhaven" || source.name == "danbooru") {
                    OutlinedTextField(
                        value = source.apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
                if (source.name == "danbooru") {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = source.apiUser,
                        onValueChange = onApiUserChange,
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Connection")
                }
            }
        }
    }
}

@Composable
private fun ServerSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
