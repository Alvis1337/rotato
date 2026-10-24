package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.live.LiveWallpaper
import com.chrisalvis.rotato.worker.AutomationReceiver
import java.io.File

/**
 * Now: what's on screen, quick actions on it, what comes next and what came before. Rotation's
 * status lives here instead of above the Library grid.
 */
@Composable
fun NowScreen(
    viewModel: HomeViewModel,
    collections: List<LocalList>,
    modifier: Modifier = Modifier,
) {
    val now by viewModel.nowShowing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastRotationMs by viewModel.lastRotationMs.collectAsStateWithLifecycle()
    val pool by viewModel.images.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val errors by viewModel.rotationErrors.collectAsStateWithLifecycle()
    val linked = remember(collections) { collections.filter { it.useAsRotation } }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NowShowingCard(
                item = now,
                onPrevious = { viewModel.automation(AutomationReceiver.ACTION_PREVIOUS) },
                onNext = { viewModel.automation(AutomationReceiver.ACTION_NEXT) },
                onFavorite = { viewModel.automation(AutomationReceiver.ACTION_SAVE) },
                onNever = { viewModel.dislikeCurrent() },
            )
            RotationStatusCard(
                isEnabled = settings.isEnabled,
                imageCount = pool.size,
                intervalMinutes = settings.intervalMinutes,
                lastRotationMs = lastRotationMs,
                linkedCollections = linked,
                onToggle = { viewModel.setRotationEnabled(!settings.isEnabled) },
            )
            if (errors.isNotEmpty()) {
                RotationErrorPane(errors = errors, onClearAll = { viewModel.clearRotationErrors() })
            }
        }
    }
    val upNextList: @Composable (Modifier) -> Unit = { m ->
        UpNextList(
            queued = upNext,
            pool = pool,
            shuffle = settings.shuffleMode,
            currentIndex = settings.currentIndex,
            onMove = viewModel::moveUpNext,
            onRemove = viewModel::removeFromUpNext,
            onClear = viewModel::clearUpNext,
            modifier = m,
        )
    }

    if (wide) {
        // Unfolded: on screen and up next on the left, history on the right.
        Row(modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 8.dp)) {
                header()
                Spacer(Modifier.size(8.dp))
                upNextList(Modifier.weight(1f))
            }
            VerticalDivider()
            HistoryScreen(modifier = Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { header() }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (upNext.isEmpty()) "Up next" else "Up next · ${upNext.size}") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
            }
            when (tab) {
                0 -> upNextList(Modifier.weight(1f).padding(horizontal = 16.dp))
                else -> HistoryScreen(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NowShowingCard(
    item: WallpaperHistoryItem?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onNever: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val model: Any? = item?.let { h ->
                h.fullUrl.takeIf { it.startsWith("/") }?.let(::File)?.takeIf { it.exists() }
                    ?: h.thumbUrl.ifBlank { h.sampleUrl.ifBlank { h.fullUrl } }
            }
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (model != null) {
                    AsyncImage(model = model, contentDescription = "Current wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("On screen now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        item == null -> "Nothing set by Rotato yet"
                        item.tags.isNotEmpty() -> item.tags.take(4).joinToString(" ") { it.replace('_', ' ') }
                        else -> item.source.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item != null) {
                    Text(
                        android.text.format.DateUtils.getRelativeTimeSpanString(item.timestamp).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous") }
                    FilledTonalIconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = "Next") }
                    FilledTonalIconButton(onClick = onFavorite, enabled = item != null) { Icon(Icons.Default.FavoriteBorder, contentDescription = "Save to Favorites") }
                    FilledTonalIconButton(onClick = onNever, enabled = item != null) { Icon(Icons.Default.Block, contentDescription = "Never show again") }
                }
            }
        }
    }
}

@Composable
private fun UpNextList(
    queued: List<File>,
    pool: List<File>,
    shuffle: Boolean,
    currentIndex: Int,
    onMove: (Int, Int) -> Unit,
    onRemove: (File) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // In order mode the next few are known; shuffle only promises the pool.
    val following = remember(pool, shuffle, currentIndex) {
        if (shuffle || pool.isEmpty()) emptyList()
        else (0 until minOf(6, pool.size)).map { pool[(currentIndex + it) % pool.size] }
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (queued.isEmpty()) {
            item {
                Text(
                    "Nothing lined up. In the Library, open a wallpaper and pick Play next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lined up", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            itemsIndexed(queued, key = { _, f -> "q:" + f.absolutePath }) { i, file ->
                QueueRow(file) {
                    IconButton(onClick = { onMove(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                    IconButton(onClick = { onMove(i, i + 1) }, enabled = i < queued.lastIndex) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                    IconButton(onClick = { onRemove(file) }) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                }
            }
        }
        item {
            Text(
                when {
                    pool.isEmpty() -> "Then: your Library is empty"
                    shuffle -> "Then: shuffled from ${pool.size} in your Library"
                    else -> "Then, in order:"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        itemsIndexed(following, key = { i, f -> "f:$i:" + f.absolutePath }) { _, file -> QueueRow(file) {} }
    }
}

@Composable
private fun QueueRow(file: File, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Box {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(
            if (LiveWallpaper.isVideoFile(file)) "Video" else "Image",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        actions()
    }
}
