package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.SourceType
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHealthScreen(
    brainrotViewModel: BrainrotViewModel,
    onBack: () -> Unit,
) {
    val healthMap = brainrotViewModel.sourceHealth.collectAsStateWithLifecycle().value
    val sources = brainrotViewModel.sources.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Health", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No sources configured. Add sources from the Discover tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sources, key = ::sourceKey) { source ->
                    val sourceId = sourceKey(source)
                    val health = healthMap[sourceId]
                    SourceHealthCard(
                        sourceName = health?.sourceName ?: sourceLabel(source),
                        health = health,
                        onTest = { brainrotViewModel.testSource(sourceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceHealthCard(
    sourceName: String,
    health: SourceHealth?,
    onTest: () -> Unit,
) {
    val status = when {
        (health?.lastSuccess ?: 0L) > 0L -> "✅"
        health?.lastError != null -> "❌"
        else -> "⚠️"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(status)
            }
            Text(
                text = "Last fetch: ${formatLastFetch(health?.lastSuccess ?: 0L)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if ((health?.totalFetches ?: 0) > 0) {
                Text(
                    text = "${health?.successCount ?: 0}/${health?.totalFetches ?: 0} fetches succeeded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!health?.lastError.isNullOrBlank()) {
                Text(
                    text = health?.lastError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = onTest,
                enabled = health?.isTesting != true,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (health?.isTesting == true) "Testing…" else "Test")
            }
        }
    }
}

private fun formatLastFetch(lastSuccess: Long): String {
    if (lastSuccess <= 0L) return "Never"
    val minutesAgo = max(1, ((System.currentTimeMillis() - lastSuccess) / 60_000L).toInt())
    return "$minutesAgo min ago"
}

private fun sourceKey(source: LocalSource): String =
    if (source.instanceId.isBlank()) source.type.name else "${source.type.name}:${source.instanceId}"

private fun sourceLabel(source: LocalSource): String =
    if (source.type == SourceType.REDDIT && source.instanceId.isNotBlank()) {
        "r/${source.instanceId}"
    } else {
        source.type.displayName
    }
