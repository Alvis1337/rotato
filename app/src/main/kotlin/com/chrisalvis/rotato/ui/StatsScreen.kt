package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray

data class SourceStat(val source: String, val count: Int)
data class TagStat(val tag: String, val count: Int)

data class StatsData(
    val totalRotations: Long = 0,
    val totalImagesSaved: Int = 0,
    val totalCollections: Int = 0,
    val historyCount: Int = 0,
    val sourceBreakdown: List<SourceStat> = emptyList(),
    val topTags: List<TagStat> = emptyList()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RotatoPreferences(application)
    private val localLists = LocalListsPreferences(application)

    val stats: StateFlow<StatsData> = combine(
        prefs.settings,
        prefs.totalRotations,
        prefs.historyJson,
        localLists.lists,
        localLists.allWallpapers
    ) { _, totalRotations, historyJson, lists, wallpapers ->
        val sourceBreakdown = wallpapers
            .groupingBy { it.source.ifBlank { "unknown" } }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { SourceStat(source = it.key, count = it.value) }

        val topTags = wallpapers
            .flatMap { it.tags }
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(10)
            .map { TagStat(tag = it.key, count = it.value) }
            .toList()

        StatsData(
            totalRotations = totalRotations,
            totalImagesSaved = wallpapers.size,
            totalCollections = lists.size,
            historyCount = runCatching { JSONArray(historyJson).length() }.getOrDefault(0),
            sourceBreakdown = sourceBreakdown,
            topTags = topTags
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsData())
}

@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = viewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatsSectionCard(title = "Rotation") {
                    StatRow(label = "Total rotations", value = stats.totalRotations.toString())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    StatRow(label = "History items", value = stats.historyCount.toString())
                }
            }
            item {
                StatsSectionCard(title = "Collections") {
                    StatRow(label = "Collections", value = stats.totalCollections.toString())
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    StatRow(label = "Images saved", value = stats.totalImagesSaved.toString())
                }
            }
            item {
                StatsSectionCard(title = "By Source") {
                    if (stats.sourceBreakdown.isEmpty()) {
                        EmptyStatsText("No saved images yet")
                    } else {
                        stats.sourceBreakdown.forEachIndexed { index, stat ->
                            StatRow(label = "${sourceEmoji(stat.source)} ${stat.source}", value = stat.count.toString())
                            if (index != stats.sourceBreakdown.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            }
                        }
                    }
                }
            }
            item {
                StatsSectionCard(title = "Top Tags") {
                    if (stats.topTags.isEmpty()) {
                        EmptyStatsText("No tags available yet")
                    } else {
                        TopTags(stats.topTags)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun StatsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyStatsText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopTags(tags: List<TagStat>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = {},
                label = { Text("#${tag.tag} · ${tag.count}") }
            )
        }
    }
}

private fun sourceEmoji(source: String): String = when (source.lowercase()) {
    "danbooru" -> "🐾"
    "gelbooru" -> "💠"
    "wallhaven" -> "🧱"
    "device" -> "📱"
    else -> "🖼️"
}
