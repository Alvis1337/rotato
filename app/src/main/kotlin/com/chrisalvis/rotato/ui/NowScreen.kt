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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.RotatoSettings
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.WallpaperTarget
import com.chrisalvis.rotato.live.LiveWallpaper
import com.chrisalvis.rotato.worker.AutomationReceiver
import com.chrisalvis.rotato.worker.FavoriteWallpaperReceiver
import kotlinx.coroutines.launch
import java.io.File

private const val PANEL_UP_NEXT = "up_next"
private const val PANEL_HISTORY = "history"
private const val PANEL_ERRORS = "errors"

private const val TAB_UP_NEXT = 0
private const val TAB_HISTORY = 1

// One visual system for everything over the wallpaper: one container colour and corner, one icon
// size, one gap, and three text styles (title, body, caption).
private val NowShape = RoundedCornerShape(20.dp)
private val NowIcon = 22.dp
private val NowGap = 12.dp
private val NowPad = 16.dp

private object NowType {
    val title: TextStyle @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val body: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val caption: TextStyle @Composable get() = MaterialTheme.typography.bodySmall
}

/** Translucent dark over the wallpaper; the theme's raised surface where there's no image behind. */
@Composable
private fun nowContainer(onImage: Boolean): Color =
    if (onImage) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun nowContent(onImage: Boolean): Color =
    if (onImage) Color.White else MaterialTheme.colorScheme.onSurface

/**
 * Wallpaper: what's on screen, full-bleed, with its caption, one row of labelled actions, the
 * rotation card (opens [RotationSheet]) and an Up next / History strip at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    viewModel: HomeViewModel,
    collections: List<LocalList>,
    onGoToDiscover: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenRotationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by viewModel.nowShowing.collectAsStateWithLifecycle()
    val nowFile by viewModel.nowShowingFile.collectAsStateWithLifecycle()
    val nowLists by viewModel.nowShowingCollections.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastRotationMs by viewModel.lastRotationMs.collectAsStateWithLifecycle()
    val pool by viewModel.images.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val errors by viewModel.rotationErrors.collectAsStateWithLifecycle()
    // Same instance HistoryScreen uses on this screen, so the strip and the full list agree.
    val historyVm: HistoryViewModel = viewModel()
    val history by historyVm.history.collectAsStateWithLifecycle()
    val nsfwHidden = LocalNsfwHidden.current
    val nsfwNames by viewModel.nsfwFileNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // What rotation can draw right now, as the worker sees it: videos only through the live
    // wallpaper, and nothing the content filter hides. The forecast below follows this, not the
    // whole Library, so "Then, in order" matches what Next actually sets.
    val liveActive = remember(pool, now) { LiveWallpaper.isActive(context) }
    val rotationPool = remember(pool, liveActive, nsfwHidden, nsfwNames) {
        pool.filter { (liveActive || !LiveWallpaper.isVideoFile(it)) && !(nsfwHidden && it.name in nsfwNames) }
    }
    val waitReason: (File) -> String? = { file ->
        when {
            LiveWallpaper.isVideoFile(file) && !liveActive -> "Waits for the live wallpaper"
            nsfwHidden && file.name in nsfwNames -> "Hidden by the content filter"
            else -> null
        }
    }
    val linkedCount = remember(collections) { collections.count { it.useAsRotation } }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var showRotation by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var queueTab by rememberSaveable { mutableIntStateOf(TAB_UP_NEXT) }

    val isFavorite = nowLists.any { it.name.equals(FavoriteWallpaperReceiver.FAVORITES_LIST_NAME, ignoreCase = true) }
    // "from …": the rotation collection it came out of, else any other collection that holds it.
    val fromCollection = (nowLists.firstOrNull { it.useAsRotation }
        ?: nowLists.firstOrNull { !it.name.equals(FavoriteWallpaperReceiver.FAVORITES_LIST_NAME, ignoreCase = true) })?.name
    // The one on screen is shown above; the strip is what came before it.
    val pastItems = remember(history, now) { history.filterNot { it == now } }

    val onPrevious = { viewModel.automation(AutomationReceiver.ACTION_PREVIOUS) }
    val onNext = { viewModel.automation(AutomationReceiver.ACTION_NEXT) }
    val onFavorite = { viewModel.automation(AutomationReceiver.ACTION_SAVE) }
    // Blocking deletes the file, so it waits out an Undo snackbar before it happens.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var neverPending by remember { mutableStateOf(false) }
    val onNever: () -> Unit = {
        if (!neverPending) {
            neverPending = true
            // Only the wallpaper that was on screen at the tap gets blocked: if rotation moves on
            // during the snackbar, blocking "the current one" would hit a different image.
            val tapped = viewModel.nowShowing.value
            scope.launch {
                var undone = false
                try {
                    undone = snackbarHostState.showSnackbar(
                        "Won't show this one again",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    ) == SnackbarResult.ActionPerformed
                } finally {
                    // Leaving the screen mid-snackbar still counts as a yes.
                    neverPending = false
                    if (!undone && viewModel.nowShowing.value == tapped) viewModel.dislikeCurrent()
                }
            }
        }
    }

    val upNextList: @Composable (Modifier) -> Unit = { m ->
        UpNextList(
            queued = upNext,
            pool = rotationPool,
            waitReason = waitReason,
            shuffle = settings.shuffleMode,
            currentIndex = settings.currentIndex,
            onMove = viewModel::moveUpNext,
            onRemove = viewModel::removeFromUpNext,
            onClear = viewModel::clearUpNext,
            modifier = m,
        )
    }
    val rotationCard: @Composable (Boolean) -> Unit = { onImage ->
        RotationCard(
            settings = settings,
            lastRotationMs = lastRotationMs,
            linkedCount = linkedCount,
            poolSize = pool.size,
            problemCount = errors.size,
            onImage = onImage,
            onClick = { showRotation = true },
            onOpenProblems = { panel = PANEL_ERRORS },
        )
    }
    val openSelectedList = { panel = if (queueTab == TAB_UP_NEXT) PANEL_UP_NEXT else PANEL_HISTORY }

    Box(modifier.fillMaxSize()) {
        if (wide) {
            // Unfolded: the wallpaper on the left; the same cards stacked on the right, with the
            // selected list given the room the thumbnail strip has on a phone.
            Row(Modifier.fillMaxSize()) {
                WallpaperStage(
                    item = now,
                    localFile = nowFile,
                    hasPool = pool.isNotEmpty(),
                    fromCollection = fromCollection,
                    isFavorite = isFavorite,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onFavorite = onFavorite,
                    onNever = onNever,
                    onGoToDiscover = onGoToDiscover,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    bottomInset = true,
                ) {}
                VerticalDivider()
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(NowPad),
                    verticalArrangement = Arrangement.spacedBy(NowGap)
                ) {
                    rotationCard(false)
                    QueueCard(
                        tab = queueTab,
                        onTab = { queueTab = it },
                        upNextCount = upNext.size,
                        historyCount = history.size,
                        onImage = false,
                        onSeeAll = null,
                        modifier = Modifier.weight(1f),
                    ) {
                        when (queueTab) {
                            TAB_UP_NEXT -> upNextList(Modifier.weight(1f).padding(horizontal = NowPad))
                            else -> HistoryScreen(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            WallpaperStage(
                item = now,
                localFile = nowFile,
                hasPool = pool.isNotEmpty(),
                fromCollection = fromCollection,
                isFavorite = isFavorite,
                onPrevious = onPrevious,
                onNext = onNext,
                onFavorite = onFavorite,
                onNever = onNever,
                onGoToDiscover = onGoToDiscover,
                modifier = Modifier.fillMaxSize(),
                bottomInset = false,
            ) { onImage ->
                rotationCard(onImage)
                // Queued ones that have to wait are passed over by Next, so they aren't shown as coming.
                val readyQueued = upNext.filter { waitReason(it) == null }
                val upcoming = remember(readyQueued, rotationPool, settings.shuffleMode, settings.currentIndex) {
                    (readyQueued + followingInOrder(rotationPool, settings.shuffleMode, settings.currentIndex, 10)).take(10)
                }
                QueueCard(
                    tab = queueTab,
                    onTab = { queueTab = it },
                    upNextCount = upNext.size,
                    historyCount = history.size,
                    onImage = onImage,
                    onSeeAll = openSelectedList,
                ) {
                    when {
                        queueTab == TAB_UP_NEXT && upNext.isEmpty() ->
                            QueueHelp("Nothing queued. In Saved, open an image and tap Play next.")
                        queueTab == TAB_UP_NEXT && upcoming.isEmpty() ->
                            QueueHelp("What's queued is waiting its turn. Tap See all for why.")
                        queueTab == TAB_UP_NEXT ->
                            ThumbStrip(models = upcoming, onClick = openSelectedList)
                        pastItems.isEmpty() ->
                            QueueHelp("Wallpapers Rotato sets will show up here.")
                        else ->
                            ThumbStrip(models = pastItems.take(10).map { it.thumbUrl.ifBlank { it.fullUrl } }, onClick = openSelectedList)
                    }
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = NowPad),
        )
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
 * The current wallpaper filling [modifier], scrimmed at the bottom, with its caption and action
 * row over it. [below] adds the cards under the actions and is told whether they sit on the image.
 */
@Composable
private fun WallpaperStage(
    item: WallpaperHistoryItem?,
    /** The full-size copy on the phone, when there is one; see [HomeViewModel.nowShowingFile]. */
    localFile: File?,
    hasPool: Boolean,
    fromCollection: String?,
    isFavorite: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onNever: () -> Unit,
    onGoToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Boolean,
    below: @Composable ColumnScope.(onImage: Boolean) -> Unit,
) {
    // The applied file first, then the full image, then the sample; the thumbnail is only a
    // stand-in underneath while the big one loads (it used to be all that was shown).
    val full: Any? = item?.let { h -> localFile ?: h.fullUrl.ifBlank { h.sampleUrl }.ifBlank { null } }
    val thumb: String? = item?.let { h -> h.thumbUrl.takeIf { it.isNotBlank() && it != h.fullUrl } }
    val onImage = full != null || thumb != null
    val context = LocalContext.current
    Box(modifier.background(if (onImage) Color.Black else MaterialTheme.colorScheme.surface)) {
        if (onImage) {
            // Crop on purpose: this previews the wallpaper as it fills the screen.
            if (thumb != null) {
                AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (full != null) {
                // Sized from the layout, so it decodes at screen size and fades in over the thumbnail.
                AsyncImage(
                    model = remember(full) { ImageRequest.Builder(context).data(full).crossfade(300).build() },
                    contentDescription = "Current wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Just enough shade under the status bar icons to keep them readable.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent)))
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
        CompositionLocalProvider(LocalContentColor provides nowContent(onImage)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
            ) {
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
                        .padding(start = NowPad, end = NowPad, bottom = NowPad),
                    verticalArrangement = Arrangement.spacedBy(NowGap)
                ) {
                    if (item != null) {
                        NowCaption(item = item, fromCollection = fromCollection)
                        Row(horizontalArrangement = Arrangement.spacedBy(NowGap)) {
                            NowAction(Icons.Default.SkipPrevious, "Previous", onPrevious, onImage, Modifier.weight(1f))
                            NowAction(Icons.Default.SkipNext, "Next", onNext, onImage, Modifier.weight(1f), emphasised = true)
                            NowAction(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (isFavorite) "Favourited" else "Favourite",
                                onFavorite,
                                onImage,
                                Modifier.weight(1f),
                            )
                            NowAction(Icons.Default.Block, "Never show", onNever, onImage, Modifier.weight(1f))
                        }
                    }
                    below(onImage)
                }
            }
        }
    }
}

/** What it is (tags, else the source), and when and where it came from. */
@Composable
private fun NowCaption(item: WallpaperHistoryItem, fromCollection: String?) {
    val source = item.source.replaceFirstChar { it.uppercase() }
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(item.timestamp).toString()
    val title = if (item.tags.isNotEmpty()) item.tags.take(4).joinToString(" ") { it.replace('_', ' ') }
    else source.ifBlank { "Current wallpaper" }
    // The source is the title when there are no tags, so it isn't repeated here.
    val from = fromCollection ?: source.takeIf { item.tags.isNotEmpty() && it.isNotBlank() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = NowType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull("Set $ago", from?.let { "from $it" }).joinToString(" · "),
            style = NowType.caption,
            color = LocalContentColor.current.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One of the four equal actions: icon over a short label. Only Next is filled. */
@Composable
private fun NowAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onImage: Boolean,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = NowShape,
        color = if (emphasised) MaterialTheme.colorScheme.primary else nowContainer(onImage),
        contentColor = if (emphasised) MaterialTheme.colorScheme.onPrimary else nowContent(onImage),
        modifier = modifier.height(64.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(NowIcon))
            Spacer(Modifier.height(4.dp))
            Text(label, style = NowType.caption, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The shared container for the cards at the bottom. */
@Composable
private fun NowCard(onImage: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = NowShape,
        color = nowContainer(onImage),
        contentColor = nowContent(onImage),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
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

/**
 * Status dot, interval and what feeds rotation; opens [RotationSheet]. Rotation problems show as
 * a second line in the same card.
 */
@Composable
private fun RotationCard(
    settings: RotatoSettings,
    lastRotationMs: Long,
    linkedCount: Int,
    poolSize: Int,
    problemCount: Int,
    onImage: Boolean,
    onClick: () -> Unit,
    onOpenProblems: () -> Unit,
) {
    val next = rememberNextChangeText(settings, lastRotationMs)
    val subline = listOfNotNull(
        next?.let { "Next $it" },
        if (linkedCount > 0) "$linkedCount collection${if (linkedCount == 1) "" else "s"}"
        else "$poolSize image${if (poolSize == 1) "" else "s"}",
        if (settings.shuffleMode) "Shuffle" else "In order",
        when (settings.wallpaperTarget) {
            WallpaperTarget.HOME_ONLY -> "Home screen only"
            WallpaperTarget.LOCK_ONLY -> "Lock screen only"
            WallpaperTarget.BOTH -> null
        },
    ).joinToString(" · ")
    NowCard(onImage) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = NowPad, vertical = NowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(NowIcon), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (settings.isEnabled) Color(0xFF4ADE80) else LocalContentColor.current.copy(alpha = 0.4f))
                )
            }
            Column(Modifier.weight(1f).padding(start = NowGap)) {
                Text(
                    if (settings.isEnabled) "Rotating every ${rotationIntervalLabel(settings.intervalMinutes)}" else "Rotation off",
                    style = NowType.title,
                )
                Text(
                    subline,
                    style = NowType.caption,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Rotation settings", modifier = Modifier.size(NowIcon))
        }
        if (problemCount > 0) {
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.12f))
            val warning = if (onImage) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenProblems)
                    .padding(horizontal = NowPad, vertical = NowGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(NowIcon))
                Text(
                    "${if (problemCount == 1) "1 problem" else "$problemCount problems"} · Tap to see",
                    style = NowType.body,
                    color = warning,
                    modifier = Modifier.padding(start = NowGap),
                )
            }
        }
    }
}

/** "Up next" and "History" as two labelled tabs, then whatever [body] shows for the selected one. */
@Composable
private fun QueueCard(
    tab: Int,
    onTab: (Int) -> Unit,
    upNextCount: Int,
    historyCount: Int,
    onImage: Boolean,
    /** Opens the selected list full height; null where the list is already shown in full. */
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    NowCard(onImage, modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QueueTab(if (upNextCount > 0) "Up next · $upNextCount" else "Up next", selected = tab == TAB_UP_NEXT) { onTab(TAB_UP_NEXT) }
            QueueTab(if (historyCount > 0) "History · $historyCount" else "History", selected = tab == TAB_HISTORY) { onTab(TAB_HISTORY) }
            Spacer(Modifier.weight(1f))
            if (onSeeAll != null) {
                TextButton(
                    onClick = onSeeAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                ) { Text("See all", style = NowType.body) }
            }
        }
        body()
    }
}

@Composable
private fun QueueTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val content = LocalContentColor.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) content.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = if (selected) content else content.copy(alpha = 0.7f),
    ) {
        Text(
            label,
            style = NowType.body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = NowGap, vertical = 6.dp),
        )
    }
}

@Composable
private fun QueueHelp(text: String) {
    Text(
        text,
        style = NowType.caption,
        color = LocalContentColor.current.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = NowPad, end = NowPad, top = 8.dp, bottom = NowPad),
    )
}

/** A row of small thumbnails; tapping any opens the full list. */
@Composable
private fun ThumbStrip(models: List<Any>, onClick: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = NowPad, end = NowPad, top = 8.dp, bottom = NowPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(models, key = { i, m -> "$i:$m" }) { _, model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalContentColor.current.copy(alpha = 0.12f))
                    .clickable(onClick = onClick),
            )
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
    /** Why a queued one is being passed over for now, or null when Next will show it. */
    waitReason: (File) -> String?,
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
                    "Nothing lined up. Open a wallpaper in Saved and tap Play next to show it after this one.",
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
                QueueRow(file, note = waitReason(file)) {
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
private fun QueueRow(file: File, note: String? = null, actions: @Composable () -> Unit) {
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
            listOfNotNull(if (LiveWallpaper.isVideoFile(file)) "Video" else "Image", note).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (note != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        actions()
    }
}
