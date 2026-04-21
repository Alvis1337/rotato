package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    fun setEnabled(type: SourceType, enabled: Boolean) {
        viewModelScope.launch { prefs.update(type, enabled = enabled) }
    }

    fun setCredentials(type: SourceType, apiKey: String, apiUser: String) {
        viewModelScope.launch { prefs.update(type, apiKey = apiKey, apiUser = apiUser) }
    }

    fun setTags(type: SourceType, tags: String) {
        viewModelScope.launch { prefs.update(type, tags = tags) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSourcesScreen(onNavigateBack: () -> Unit) {
    val vm: LocalSourcesViewModel = viewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()

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
                        isPremium = true,
                        isLocked = !unlocked,
                        onToggle = { if (unlocked) vm.setEnabled(source.type, it) },
                        onSaveCredentials = { key, user -> vm.setCredentials(source.type, key, user) },
                        onSaveTags = { vm.setTags(source.type, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: LocalSource,
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
                    // Description from plugin, or fallback to old safeContent hint
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
                    if (source.tags.isNotBlank()) {
                        Text("Tags: ${source.tags}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isLocked) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Close" else "Configure")
                        }
                    } else {
                        TextButton(onClick = { /* TODO: launch IAP purchase flow */ }) {
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

