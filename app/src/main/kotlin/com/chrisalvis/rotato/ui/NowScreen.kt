package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.RotatoSettings
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.live.LiveWallpaper
import com.chrisalvis.rotato.worker.AutomationReceiver
import java.io.File

private const val PANEL_UP_NEXT = "up_next"
private const val PANEL_HISTORY = "history"
private const val PANEL_ERRORS = "errors"

/**
 * Wallpaper: what's on screen, full-bleed, with next/back/save on top of it. Rotation's status is
 * a pill that opens [RotationSheet]; Up next and History open full height from the strip below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    viewModel: HomeViewModel,
    collections: List<LocalList>,
    onOpenSettings: () -> Unit,
    onGoToDiscover: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenRotationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by viewModel.nowShowing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastRotationMs by viewModel.lastRotationMs.collectAsStateWithLifecycle()
    val pool by viewModel.images.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val errors by viewModel.rotationErrors.collectAsStateWithLifecycle()
    val linkedCount = remember(collections) { collections.count { it.useAsRotation } }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var showRotation by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }

    val onPrevious = { viewModel.automation(AutomationReceiver.ACTION_PREVIOUS) }
    val onNext = { viewModel.automation(AutomationReceiver.ACTION_NEXT) }
    val onFavorite = { viewModel.automation(AutomationReceiver.ACTION_SAVE) }
    val onNever = { viewModel.dislikeCurrent() }

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
    val pill: @Composable (Boolean) -> Unit = { onImage ->
        RotationPill(
            settings = settings,
            lastRotationMs = lastRotationMs,
            linkedCount = linkedCount,
            poolSize = pool.size,
            onImage = onImage,
            onClick = { showRotation = true },
        )
    }
    val errorChip: @Composable () -> Unit = {
        if (errors.isNotEmpty()) RotationErrorChip(count = errors.size, onClick = { panel = PANEL_ERRORS })
    }

    if (wide) {
        // Unfolded: the wallpaper on the left, rotation and its queue on the right.
        Row(modifier.fillMaxSize()) {
            WallpaperStage(
                item = now,
                hasPool = pool.isNotEmpty(),
                onPrevious = onPrevious,
                onNext = onNext,
                onFavorite = onFavorite,
                onNever = onNever,
                onGoToDiscover = onGoToDiscover,
                onOpenSettings = null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                bottomInset = true,
            ) {}
            VerticalDivider()
            var tab by rememberSaveable { mutableIntStateOf(0) }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Wallpaper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SettingsGear(onClick = onOpenSettings, onImage = false)
                }
                errorChip()
                pill(false)
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (upNext.isEmpty()) "Up next" else "Up next · ${upNext.size}") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
                }
                when (tab) {
                    0 -> upNextList(Modifier.weight(1f))
                    else -> HistoryScreen(modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        WallpaperStage(
            item = now,
            hasPool = pool.isNotEmpty(),
            onPrevious = onPrevious,
            onNext = onNext,
            onFavorite = onFavorite,
            onNever = onNever,
            onGoToDiscover = onGoToDiscover,
            onOpenSettings = onOpenSettings,
            modifier = modifier.fillMaxSize(),
            bottomInset = false,
        ) { onImage ->
            errorChip()
            pill(onImage)
            UpNextStrip(
                upcoming = remember(upNext, pool, settings.shuffleMode, settings.currentIndex) {
                    upNext + followingInOrder(pool, settings.shuffleMode, settings.currentIndex, 10)
                }.take(10),
                queuedCount = upNext.size,
                shuffle = settings.shuffleMode,
                poolSize = pool.size,
                onOpenUpNext = { panel = PANEL_UP_NEXT },
                onOpenHistory = { panel = PANEL_HISTORY },
            )
        }
    }

    if (showRotation) {
        RotationSheet(
            viewModel = viewModel,
            onDismiss = { showRotation = false },
            onOpenSchedule = onOpenSchedule,
            onOpenMoreSettings = onOpenRotationSettings,
        )
    }
    when (panel) {
        PANEL_UP_NEXT -> NowPanelSheet(
            title = if (upNext.isEmpty()) "Up next" else "Up next · ${upNext.size}",
            onDismiss = { panel = null },
        ) { upNextList(Modifier.fillMaxSize().padding(horizontal = 16.dp)) }
        PANEL_HISTORY -> NowPanelSheet(title = "History", onDismiss = { panel = null }) {
            HistoryScreen(modifier = Modifier.fillMaxSize())
        }
        PANEL_ERRORS -> NowPanelSheet(title = "Rotation problems", onDismiss = { panel = null }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                RotationErrorPane(
                    errors = errors,
                    onClearAll = {
                        viewModel.clearRotationErrors()
                        panel = null
                    },
                )
            }
        }
    }
}

/**
 * The current wallpaper filling [modifier], scrimmed at the bottom, with its caption and controls
 * over it. [below] adds content under the controls and is told whether it sits on the image.
 */
@Composable
private fun WallpaperStage(
    item: WallpaperHistoryItem?,
    hasPool: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onNever: () -> Unit,
    onGoToDiscover: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottomInset: Boolean,
    below: @Composable ColumnScope.(onImage: Boolean) -> Unit,
) {
    val model: Any? = item?.let { h ->
        h.fullUrl.takeIf { it.startsWith("/") }?.let(::File)?.takeIf { it.exists() }
            ?: h.thumbUrl.ifBlank { h.sampleUrl.ifBlank { h.fullUrl } }
    }
    val onImage = model != null
    Box(modifier.background(if (onImage) Color.Black else MaterialTheme.colorScheme.surface)) {
        if (model != null) {
            // Crop on purpose: this previews the wallpaper as it fills the screen.
            AsyncImage(model = model, contentDescription = "Current wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
        }
        CompositionLocalProvider(LocalContentColor provides if (onImage) Color.White else MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
            ) {
                if (onOpenSettings != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        SettingsGear(onClick = onOpenSettings, onImage = onImage)
                    }
                }
                if (item == null) {
                    NowEmptyState(
                        hasPool = hasPool,
                        onGoToDiscover = onGoToDiscover,
                        onSetOne = onNext,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (item != null) {
                        NowCaption(item = item, onNever = onNever)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoundAction(Icons.Default.SkipPrevious, "Previous", onPrevious, onImage)
                            Button(onClick = onNext, modifier = Modifier.weight(1f).height(52.dp)) {
                                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Next wallpaper")
                            }
                            RoundAction(Icons.Default.FavoriteBorder, "Save to Favorites", onFavorite, onImage)
                        }
                    }
                    below(onImage)
                }
            }
        }
    }
}

@Composable
private fun NowCaption(item: WallpaperHistoryItem, onNever: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val source = item.source.replaceFirstChar { it.uppercase() }
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(item.timestamp).toString()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (item.tags.isNotEmpty()) item.tags.take(4).joinToString(" ") { it.replace('_', ' ') } else source,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.tags.isNotEmpty() && source.isNotBlank()) "$source · set $ago" else "Set $ago",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Never show again") },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                    onClick = {
                        menu = false
                        onNever()
                    },
                )
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit, onImage: Boolean) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        colors = if (onImage) {
            IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White)
        } else IconButtonDefaults.filledTonalIconButtonColors(),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun SettingsGear(onClick: () -> Unit, onImage: Boolean) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = if (onImage) {
            IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f), contentColor = Color.White)
        } else IconButtonDefaults.iconButtonColors(),
    ) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
}

@Composable
private fun NowEmptyState(hasPool: Boolean, onGoToDiscover: () -> Unit, onSetOne: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No wallpaper from Rotato yet", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                if (hasPool) "Set one now, or find more you like in Discover."
                else "Save wallpapers you like in Discover and Rotato will rotate through them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGoToDiscover) { Text("Go to Discover") }
            if (hasPool) FilledTonalButton(onClick = onSetOne) { Text("Set one now") }
        }
    }
}

/** Status dot, interval and what feeds rotation; opens [RotationSheet]. */
@Composable
private fun RotationPill(
    settings: RotatoSettings,
    lastRotationMs: Long,
    linkedCount: Int,
    poolSize: Int,
    onImage: Boolean,
    onClick: () -> Unit,
) {
    val next = rememberNextChangeText(settings, lastRotationMs)
    val subline = listOfNotNull(
        next?.let { "Next $it" },
        if (linkedCount > 0) "$linkedCount collection${if (linkedCount == 1) "" else "s"}"
        else "$poolSize image${if (poolSize == 1) "" else "s"}",
        if (settings.shuffleMode) "shuffle" else "in order",
    ).joinToString(" · ")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (onImage) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (settings.isEnabled) Color(0xFF4ADE80) else LocalContentColor.current.copy(alpha = 0.4f))
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (settings.isEnabled) "Rotating every ${rotationIntervalLabel(settings.intervalMinutes)}" else "Rotation off",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Rotation settings")
        }
    }
}

@Composable
private fun RotationErrorChip(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (count == 1) "1 rotation problem" else "$count rotation problems",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** A few thumbnails of what's coming, with the way into Up next and History. */
@Composable
private fun UpNextStrip(
    upcoming: List<File>,
    queuedCount: Int,
    shuffle: Boolean,
    poolSize: Int,
    onOpenUpNext: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val labelColors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenUpNext, colors = labelColors, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(if (queuedCount == 0) "Up next" else "Up next · $queuedCount", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenHistory, colors = labelColors, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("History", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (upcoming.isEmpty()) {
            Text(
                when {
                    poolSize == 0 -> "Nothing to show next yet"
                    shuffle -> "Shuffling from $poolSize in your rotation"
                    else -> "Nothing lined up"
                },
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(upcoming, key = { i, f -> "$i:" + f.absolutePath }) { _, file ->
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 44.dp, height = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onOpenUpNext),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPanelSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        content()
    }
}

/** In order mode the next few are known; shuffle only promises the pool. */
private fun followingInOrder(pool: List<File>, shuffle: Boolean, currentIndex: Int, count: Int): List<File> =
    if (shuffle || pool.isEmpty()) emptyList()
    else (0 until minOf(count, pool.size)).map { pool[(currentIndex + it) % pool.size] }

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
    val following = remember(pool, shuffle, currentIndex) { followingInOrder(pool, shuffle, currentIndex, 6) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (queued.isEmpty()) {
            item {
                Text(
                    "Nothing lined up. In Saved, open a wallpaper and pick Play next.",
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
                    pool.isEmpty() -> "Then: nothing in rotation yet"
                    shuffle -> "Then: shuffled from ${pool.size} in rotation"
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
