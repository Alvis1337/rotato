package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsSettingsScreen(
    malViewModel: MalViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val malLoggedIn by malViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val malUsername by malViewModel.username.collectAsStateWithLifecycle()
    val malAnimeCount by malViewModel.animeCount.collectAsStateWithLifecycle()
    val malLoading by malViewModel.loading.collectAsStateWithLifecycle()
    val malError by malViewModel.error.collectAsStateWithLifecycle()
    val malFilterStatuses by malViewModel.filterStatuses.collectAsStateWithLifecycle()
    val malFilterMinScore by malViewModel.filterMinScore.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Integrations", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSection(title = "MyAnimeList") {
                        if (malLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (malLoggedIn) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Connected as $malUsername",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (malAnimeCount > 0) {
                                        Text(
                                            "$malAnimeCount anime in filtered list",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    MalErrorBanner(malError, onDismiss = { malViewModel.clearError() })
                                }

                                HorizontalDivider()

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MalStatusFilter(
                                        selected = malFilterStatuses,
                                        onToggle = { status ->
                                            val updated = if (status in malFilterStatuses)
                                                malFilterStatuses - status else malFilterStatuses + status
                                            malViewModel.setFilterStatuses(updated)
                                        }
                                    )

                                    MalMinScoreFilter(
                                        minScore = malFilterMinScore,
                                        onSelect = { malViewModel.setFilterMinScore(it) }
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { malViewModel.refresh() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Refresh List") }
                                        OutlinedButton(
                                            onClick = { malViewModel.logout() },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) { Text("Disconnect") }
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MalErrorBanner(malError, onDismiss = { malViewModel.clearError() })
                                Text(
                                    "Connect your MAL account to use your anime watch list as discover queries.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { malViewModel.login(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Connect MyAnimeList") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val MAL_STATUS_OPTIONS = listOf(
    "watching"      to "Watching",
    "completed"     to "Completed",
    "on_hold"       to "On Hold",
    "dropped"       to "Dropped",
    "plan_to_watch" to "Plan to Watch"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MalStatusFilter(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Watch statuses",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MAL_STATUS_OPTIONS.forEach { (key, label) ->
                FilterChip(
                    selected = key in selected,
                    onClick = { onToggle(key) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun MalMinScoreFilter(
    minScore: Int,
    onSelect: (Int) -> Unit
) {
    SettingsDropdown(
        label = "Minimum score",
        items = listOf(0) + (1..10).toList(),
        selectedLabel = if (minScore == 0) "Any rating" else "Rated $minScore+",
        itemLabel = { if (it == 0) "Any rating" else "Rated $it+" },
        onSelect = onSelect,
    )
}
