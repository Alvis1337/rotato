package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalSourcesPreferences
import com.chrisalvis.rotato.data.SourceHealth
import com.chrisalvis.rotato.data.SourceHealthTracker
import com.chrisalvis.rotato.data.SourceType
import com.chrisalvis.rotato.data.plugins.PluginEntitlement
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalSourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = LocalSourcesPreferences(app)

    val sources: StateFlow<List<LocalSource>> = prefs.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val health = SourceHealthTracker.health

    fun setEnabled(type: SourceType, enabled: Boolean) {
        viewModelScope.launch { prefs.update(type, enabled = enabled) }
    }

    fun setCredentials(type: SourceType, apiKey: String, apiUser: String) {
        viewModelScope.launch { prefs.update(type, apiKey = apiKey, apiUser = apiUser) }
    }

    fun setTags(type: SourceType, tags: String) {
        viewModelScope.launch { prefs.update(type, tags = tags) }
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

    var showDisableAllConfirm by remember { mutableStateOf(false) }
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

    // Partition: free first, then premium
    val freeSources = sources.filter { !it.type.isPremium }
    val premiumSources = sources.filter { it.type.isPremium }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
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
                items(freeSources, key = { it.type.name }) { source ->
                    SourceCard(
                        source = source,
                        health = healthMap[source.type],
                        onToggle = { vm.setEnabled(source.type, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.type, key, user) },
                        onSaveTags = { vm.setTags(source.type, it) }
                    )
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
                items(premiumSources, key = { it.type.name }) { source ->
                    val plugin = source.type.plugin
                    val unlocked = plugin == null || PluginEntitlement.isUnlocked(plugin)
                    SourceCard(
                        source = source,
                        health = healthMap[source.type],
                        isPremium = true,
                        isLocked = !unlocked,
                        onToggle = { if (unlocked) vm.setEnabled(source.type, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.type, key, user) },
                        onSaveTags = { vm.setTags(source.type, it) }
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
    isPremium: Boolean = false,
    isLocked: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onSaveTags: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKey by remember(source) { mutableStateOf(source.apiKey) }
    var apiUser by remember(source) { mutableStateOf(source.apiUser) }
    var tags by remember(source) { mutableStateOf(source.tags) }
    var showKey by remember { mutableStateOf(false) }
    var showHealthDetail by remember { mutableStateOf(false) }

    var showPremiumInfo by remember { mutableStateOf(false) }
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
        else -> Color(0xFFF44336)
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
                        // Health dot — only shown for enabled sources
                        if (source.enabled && !isLocked) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(healthDotColor, CircleShape)
                            )
                        }
                        Text(source.type.displayName, fontWeight = FontWeight.Medium)
                        if (isPremium) {
                            SuggestionChip(
                                onClick = {},
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
                    if (!isLocked) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Close" else "Configure")
                        }
                    } else {
                        TextButton(onClick = { showPremiumInfo = true }) {
                            Text("Unlock")
                        }
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
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags / Query") },
                    placeholder = { Text("e.g. scenery landscape (leave blank for global search)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Space-separated tags used only for this source") }
                )
                if (source.type.needsApiUser) {
                    OutlinedTextField(
                        value = apiUser,
                        onValueChange = { apiUser = it },
                        label = { Text(source.type.apiUserLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (source.type.needsApiKey) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(source.type.apiKeyLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }
                // (Purity is fully controlled by the global NSFW toggle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = {
                        onSaveTags(tags.trim())
                        onSaveCredentials(apiKey, apiUser)
                        expanded = false
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

