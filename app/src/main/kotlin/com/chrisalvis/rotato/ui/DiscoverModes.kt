package com.chrisalvis.rotato.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.MediaType
import com.chrisalvis.rotato.data.MinResolution
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How Discover lays out results. Grid is the default; the choice is remembered. */
enum class DiscoverViewMode(val key: String, val label: String) {
    GRID("grid", "Grid"),
    COMPACT("compact", "Compact grid"),
    SWIPE("swipe", "Swipe"),
    FEED("feed", "Feed");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: GRID
    }
}

/** The layout picker in Discover's header. */
@Composable
internal fun DiscoverLayoutButton(mode: DiscoverViewMode, onSelect: (DiscoverViewMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(mode.icon(), contentDescription = "Layout: ${mode.label}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DiscoverViewMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(m.label)
                            Text(m.hint(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Icon(m.icon(), contentDescription = null) },
                    onClick = { open = false; onSelect(m) },
                )
            }
        }
    }
}

private fun DiscoverViewMode.icon() = when (this) {
    DiscoverViewMode.GRID -> Icons.Default.ViewAgenda
    DiscoverViewMode.COMPACT -> Icons.Default.GridView
    DiscoverViewMode.SWIPE -> Icons.Default.Style
    DiscoverViewMode.FEED -> Icons.Default.ViewDay
}

private fun DiscoverViewMode.hint() = when (this) {
    DiscoverViewMode.GRID -> "Big cards, shaped like each image"
    DiscoverViewMode.COMPACT -> "Small squares, lots at once"
    DiscoverViewMode.SWIPE -> "One at a time: right saves, left skips"
    DiscoverViewMode.FEED -> "Full screen, just scroll"
}

/**
 * Filters as chips under Discover's title, so the common ones are one tap instead of a sheet.
 * "More" still opens the full sheet (aspect ratio, profiles, MAL).
 */
@Composable
internal fun DiscoverFilterBar(
    filters: BrainrotFilters,
    nsfwMode: Boolean,
    nsfwHidden: Boolean,
    isFoldable: Boolean,
    forYou: Boolean,
    interests: Boolean,
    onNsfw: (Boolean) -> Unit,
    onVideos: (Boolean) -> Unit,
    onFoldFriendly: (Boolean) -> Unit,
    onMinResolution: (MinResolution) -> Unit,
    onForYou: (Boolean) -> Unit,
    onInterests: (Boolean) -> Unit,
    onMore: () -> Unit,
    onTaste: () -> Unit,
) {
    var sizeOpen by remember { mutableStateOf(false) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!nsfwHidden) item { FilterChip(selected = nsfwMode, onClick = { onNsfw(!nsfwMode) }, label = { Text("NSFW") }) }
        item { FilterChip(selected = filters.videoOnly, onClick = { onVideos(!filters.videoOnly) }, label = { Text("Videos") }) }
        if (isFoldable) item { FilterChip(selected = filters.isFoldFriendly, onClick = { onFoldFriendly(!filters.isFoldFriendly) }, label = { Text("Fold-friendly") }) }
        item {
            Box {
                FilterChip(
                    selected = filters.minResolution != MinResolution.ANY,
                    onClick = { sizeOpen = true },
                    label = { Text(if (filters.minResolution == MinResolution.ANY) "Any size" else filters.minResolution.label.substringBefore(" (").trim()) },
                )
                DropdownMenu(expanded = sizeOpen, onDismissRequest = { sizeOpen = false }) {
                    MinResolution.entries.forEach { r ->
                        DropdownMenuItem(text = { Text(r.label) }, onClick = { sizeOpen = false; onMinResolution(r) })
                    }
                }
            }
        }
        item { FilterChip(selected = forYou, onClick = { onForYou(!forYou) }, label = { Text("For you") }) }
        item { FilterChip(selected = interests, onClick = { onInterests(!interests) }, label = { Text("My interests") }) }
        item { AssistChip(onClick = onTaste, label = { Text("Taste") }) }
        item { AssistChip(onClick = onMore, label = { Text("More filters") }) }
    }
}

/**
 * Saved searches as tabs: "All" plus each pinned search. Pinning the current search adds one.
 */
@Composable
internal fun SavedSearchTabs(
    pinned: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    onPinCurrent: () -> Unit,
) {
    if (pinned.isEmpty() && current.isBlank()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { FilterChip(selected = current.isBlank(), onClick = { onSelect("") }, label = { Text("All") }) }
        items(pinned, key = { it }) { s ->
            FilterChip(selected = current == s, onClick = { onSelect(s) }, label = { Text(s.replace('_', ' '), maxLines = 1) })
        }
        if (current.isNotBlank() && current !in pinned) {
            item {
                AssistChip(
                    onClick = onPinCurrent,
                    label = { Text("Save \"${current.replace('_', ' ')}\"", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

/** Shared paging trigger: asks for more once fewer than [ahead] items are left past [index]. */
@Composable
private fun LoadAhead(index: () -> Int, size: Int, ahead: Int, busy: Boolean, ended: Boolean, onLoadMore: () -> Unit) {
    val latestIndex by rememberUpdatedState(index)
    LaunchedEffect(size, busy, ended) {
        snapshotFlow { latestIndex() >= size - ahead }
            .collect { near -> if (near && !busy && !ended) onLoadMore() }
    }
}

/**
 * Feed: one image or video per screen, scrolled vertically. Nothing has to be decided; saving
 * is there if something catches your eye (the button, or double-tap on a picture).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverFeed(
    items: List<BrainrotWallpaper>,
    isSaved: (BrainrotWallpaper) -> Boolean,
    nsfwBlurEnabled: Boolean,
    loadingMore: Boolean,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    /** Null hides Save (and double-tap saving), e.g. when playing the user's own Library. */
    onSave: ((BrainrotWallpaper) -> Unit)?,
    onOpen: (BrainrotWallpaper) -> Unit,
    onSet: (BrainrotWallpaper) -> Unit,
    onSkip: ((BrainrotWallpaper) -> Unit)? = {},
    onTag: (String) -> Unit = {},
    onMoreLikeThis: ((BrainrotWallpaper) -> Unit)? = {},
    onDwell: (BrainrotWallpaper, Long) -> Unit = { _, _ -> },
    /** Replaces the default share (the post's link as text), e.g. to share a file on the phone. */
    onShare: ((BrainrotWallpaper) -> Unit)? = null,
    /** The caption's first line; defaults to the source and size. */
    title: ((BrainrotWallpaper) -> String)? = null,
    startPage: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    LoadAhead({ pager.currentPage }, items.size, 4, loadingMore, endReached, onLoadMore)
    // Load the start of the next two videos while this one plays, so a swipe starts instantly.
    LaunchedEffect(pager.currentPage, items.size) {
        (1..2).mapNotNull { items.getOrNull(pager.currentPage + it) }.filter { it.isVideo }.forEach { next ->
            launch { VideoPrefetch.warm(context, next.fullUrl.ifBlank { next.sampleUrl }) }
        }
    }
    // Time spent on each page is a quiet taste signal (lingering vs flicking past).
    val currentItem = items.getOrNull(pager.currentPage)
    androidx.compose.runtime.DisposableEffect(currentItem?.source, currentItem?.id) {
        val shownAt = android.os.SystemClock.uptimeMillis()
        onDispose { currentItem?.let { onDwell(it, android.os.SystemClock.uptimeMillis() - shownAt) } }
    }
    VerticalPager(
        state = pager,
        modifier = modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1,
        key = { "${items.getOrNull(it)?.source}:${items.getOrNull(it)?.id}:$it" },
    ) { page ->
        val wp = items.getOrNull(page) ?: return@VerticalPager
        var revealed by rememberNsfwRevealed("${wp.source}:${wp.id}")
        val blurred = wp.isNsfw && nsfwBlurEnabled && !revealed
        var burst by remember { mutableStateOf(0) }
        val saved = isSaved(wp)
        val save = { if (onSave != null) { if (!saved) onSave(wp); burst++ } }
        Box(Modifier.fillMaxSize()) {
            ReelMedia(
                wp = wp,
                playing = page == pager.currentPage && !blurred,
                modifier = Modifier
                    .fillMaxSize()
                    .nsfwContentBlur(wp.isNsfw, nsfwBlurEnabled, revealed)
                    .pointerInput(wp.id, blurred, saved) {
                        detectTapGestures(
                            onTap = { if (blurred) revealed = true },
                            onDoubleTap = { if (!blurred && onSave != null) save() },
                            onLongPress = { onOpen(wp) },
                        )
                    },
            )
            NsfwBlurLayer(wp.isNsfw, nsfwBlurEnabled, revealed)
            // Shade top and bottom so white text and icons read on any picture.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.18f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.6f),
                        )
                    )
            )
            SaveBurst(burst, Modifier.align(Alignment.Center))

            // Action rail, right side.
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 8.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (onSave != null) ReelAction(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (saved) "Saved" else "Save", tint = if (saved) MaterialTheme.colorScheme.primary else Color.White) { save() }
                ReelAction(Icons.Default.Wallpaper, "Set") { onSet(wp) }
                ReelAction(Icons.Default.Share, "Share") {
                    if (onShare != null) { onShare(wp); return@ReelAction }
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, wp.pageUrl.takeIf { it.startsWith("http") } ?: wp.fullUrl)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, "Share"))
                }
                if (onMoreLikeThis != null) ReelAction(Icons.Default.AutoAwesome, "More like this") { onMoreLikeThis(wp) }
                if (onSkip != null) ReelAction(Icons.Default.ThumbDown, "Not for me") { onSkip(wp) }
                ReelAction(Icons.Default.OpenInFull, "More") { onOpen(wp) }
            }

            // Caption, bottom left: where it's from, then its tags (tap one to search it).
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 84.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title?.invoke(wp) ?: buildString {
                        append(sourceDisplayName(wp.source))
                        if (wp.resolution.isNotBlank()) append(" · ").append(wp.resolution.replace("x", "×"))
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxLines = 2,
                ) {
                    wp.tags.take(8).forEach { tag ->
                        Text(
                            "#" + tag.replace('_', ' '),
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.combinedClickable(
                                onClick = { onTag(tag) },
                                onLongClick = { onMoreLikeThis?.invoke(wp) },
                            ),
                        )
                    }
                }
            }
        }
    }
    if (items.isEmpty() && loadingMore) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun ReelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { shadowElevation = 6f; shape = CircleShape; clip = false },
        )
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * One page of the feed. Media is always shown whole, never cropped or zoomed; whatever space
 * it leaves is filled by a blurred, darkened copy of itself instead of hard black bars.
 */
@Composable
private fun ReelMedia(wp: BrainrotWallpaper, playing: Boolean, modifier: Modifier) {
    val still = if (wp.isVideo) wp.thumbUrl.takeUnless { it.isBlank() || MediaType.isVideoUrl(it) } ?: wp.gridUrl.takeUnless { MediaType.isVideoUrl(it) }
    else wp.sampleUrl.takeIf { it.isNotBlank() && !MediaType.isVideoUrl(it) } ?: wp.fullUrl.ifBlank { wp.thumbUrl }
    Box(modifier) {
        if (!still.isNullOrBlank()) {
            AsyncImage(
                model = still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
                    .graphicsLayer { alpha = 0.55f },
            )
        }
        if (wp.isVideo && playing) {
            VideoPlayerView(
                url = wp.fullUrl.ifBlank { wp.sampleUrl },
                modifier = Modifier.fillMaxSize(),
                allowTapToToggle = true,
                showMuteButton = true,
                progressLine = true,
                posterUrl = still,
            )
        } else {
            AsyncImage(
                model = still,
                contentDescription = wp.tags.take(3).joinToString(" "),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A picture fitted to the page, or the video playing when [playing] (its poster otherwise). */
@Composable
private fun MediaPage(wp: BrainrotWallpaper, playing: Boolean, modifier: Modifier, crop: Boolean = false) {
    Box(modifier) {
        if (wp.isVideo && playing) {
            VideoPlayerView(url = wp.fullUrl.ifBlank { wp.sampleUrl }, modifier = Modifier.fillMaxSize(), showMuteButton = true)
        } else {
            val url = if (wp.isVideo) wp.thumbUrl.takeUnless { it.isBlank() || MediaType.isVideoUrl(it) } ?: wp.gridUrl
            else wp.sampleUrl.takeIf { it.isNotBlank() && !MediaType.isVideoUrl(it) } ?: wp.fullUrl.ifBlank { wp.thumbUrl }
            AsyncImage(
                model = url,
                contentDescription = wp.tags.take(3).joinToString(" "),
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Swipe: one card at a time. Drag right (or tap the bookmark) to save, left (or ✕) to skip.
 */
@Composable
internal fun SwipeDeck(
    items: List<BrainrotWallpaper>,
    nsfwBlurEnabled: Boolean,
    loading: Boolean,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onSave: (BrainrotWallpaper) -> Unit,
    onSkip: (BrainrotWallpaper) -> Unit,
    onOpen: (BrainrotWallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    LoadAhead({ 0 }, items.size, 6, loading, endReached, onLoadMore)
    val top = items.firstOrNull()
    val scope = rememberCoroutineScope()
    val offsetX = remember(top?.id, top?.source) { Animatable(0f) }
    Column(modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            items.getOrNull(1)?.let { next ->
                MediaPage(next, playing = false, crop = true, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 0.94f; scaleY = 0.94f }.clip(RoundedCornerShape(20.dp)).background(Color.Black))
            }
            if (top == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator() else Text("That's everything for these filters", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                var revealed by rememberNsfwRevealed("${top.source}:${top.id}")
                val decide: (Boolean) -> Unit = { save ->
                    scope.launch {
                        offsetX.animateTo(if (save) 1600f else -1600f)
                        if (save) onSave(top) else onSkip(top)
                        settle(offsetX)
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offsetX.value
                            rotationZ = offsetX.value / 60f
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .pointerInput(top.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val x = offsetX.value
                                    when {
                                        x > size.width * 0.28f -> decide(true)
                                        x < -size.width * 0.28f -> decide(false)
                                        else -> scope.launch { offsetX.animateTo(0f) }
                                    }
                                },
                                onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                            ) { change, drag ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + drag) }
                            }
                        }
                        .pointerInput(top.id) {
                            detectTapGestures(onTap = { if (top.isNsfw && nsfwBlurEnabled && !revealed) revealed = true else onOpen(top) })
                        }
                ) {
                    MediaPage(top, playing = true, crop = true, modifier = Modifier.fillMaxSize().nsfwContentBlur(top.isNsfw, nsfwBlurEnabled, revealed))
                    NsfwBlurLayer(top.isNsfw, nsfwBlurEnabled, revealed)
                    val x = offsetX.value
                    if (abs(x) > 40f) {
                        Text(
                            if (x > 0) "SAVE" else "SKIP",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier
                                .align(if (x > 0) Alignment.TopStart else Alignment.TopEnd)
                                .padding(20.dp)
                                .background((if (x > 0) Color(0xFF2E7D32) else Color(0xFFC62828)).copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp, bottom = 72.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { top?.let { onSkipNow(it, offsetX, scope, onSkip) } }, enabled = top != null, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Skip")
            }
            FilledTonalIconButton(onClick = { top?.let(onOpen) }, enabled = top != null) {
                Icon(Icons.Default.OpenInFull, contentDescription = "Open")
            }
            FilledIconButton(onClick = { top?.let { onSaveNow(it, offsetX, scope, onSave) } }, enabled = top != null, modifier = Modifier.size(60.dp), shape = CircleShape) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = "Save")
            }
        }
    }
}

private fun onSkipNow(wp: BrainrotWallpaper, offset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, scope: kotlinx.coroutines.CoroutineScope, onSkip: (BrainrotWallpaper) -> Unit) {
    scope.launch { offset.animateTo(-1600f); onSkip(wp); settle(offset) }
}

private fun onSaveNow(wp: BrainrotWallpaper, offset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, scope: kotlinx.coroutines.CoroutineScope, onSave: (BrainrotWallpaper) -> Unit) {
    scope.launch { offset.animateTo(1600f); onSave(wp); settle(offset) }
}

/** If the card is still the top one (e.g. saving opened "new collection" and was cancelled), bring it back. */
private suspend fun settle(offset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    kotlinx.coroutines.delay(600)
    offset.snapTo(0f)
}


/**
 * The feed, full screen over everything, for the user's own pictures and videos: the Library or
 * a collection played like Discover's Feed. Swipe down past the first, press back or tap ✕ to leave.
 */
@Composable
internal fun ReelPlayer(
    items: List<BrainrotWallpaper>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onSet: (BrainrotWallpaper) -> Unit,
    onShare: ((BrainrotWallpaper) -> Unit)? = null,
    onSave: ((BrainrotWallpaper) -> Unit)? = null,
    isSaved: (BrainrotWallpaper) -> Boolean = { false },
    title: ((BrainrotWallpaper) -> String)? = null,
    nsfwBlurEnabled: Boolean = false,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            DiscoverFeed(
                items = items,
                isSaved = isSaved,
                nsfwBlurEnabled = nsfwBlurEnabled,
                loadingMore = false,
                endReached = true,
                onLoadMore = {},
                onSave = onSave,
                onOpen = {},
                onSet = onSet,
                onSkip = null,
                onMoreLikeThis = null,
                onShare = onShare,
                title = title,
                startPage = startIndex,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}
