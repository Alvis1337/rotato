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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSourcesScreen(onNavigateBack: () -> Unit) {
    val vm: LocalSourcesViewModel = viewModel()
    val sources by vm.sources.collectAsStateWithLifecycle()

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
            items(sources, key = { it.type.name }) { source ->
                SourceCard(
                    source = source,
                    onToggle = { vm.setEnabled(source.type, it) },
                    onSaveCredentials = { key, user -> vm.setCredentials(source.type, key, user) }
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: LocalSource,
    onToggle: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKey by remember(source) { mutableStateOf(source.apiKey) }
    var apiUser by remember(source) { mutableStateOf(source.apiUser) }
    var showKey by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(source.type.displayName, fontWeight = FontWeight.Medium)
                    if (!source.type.safeContent) {
                        Text("May include adult content", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    } else if (!source.type.needsApiKey && !source.type.needsApiUser) {
                        Text("Works without credentials", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (source.type.needsApiKey || source.type.needsApiUser) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Close" else "Credentials")
                        }
                    }
                    Switch(checked = source.enabled, onCheckedChange = onToggle)
                }
            }

            if (expanded) {
                HorizontalDivider()
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
                    FilledTonalButton(onClick = { onSaveCredentials(apiKey, apiUser); expanded = false }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
