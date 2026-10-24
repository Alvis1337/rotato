package com.chrisalvis.rotato.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.chrisalvis.rotato.data.BrainrotWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hides Discover's chrome while scrolling down through content and brings it back on any
 * scroll up, so the pictures get the whole screen while browsing.
 */
class ChromeOnScroll : NestedScrollConnection {
    var visible by mutableStateOf(true)
    private var travel = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        // Small jitters don't flip it; a deliberate scroll of a few dozen pixels does.
        travel = if ((travel < 0) == (dy < 0)) travel + dy else dy
        if (travel < -48f) visible = false
        if (travel > 24f) visible = true
        return Offset.Zero
    }
}

/** The top of Discover: a search field and, when there are any, pinned searches as tabs. */
@Composable
internal fun DiscoverTopBar(
    visible: Boolean,
    query: String,
    pinned: List<String>,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSelectPinned: (String) -> Unit,
    onPinCurrent: () -> Unit,
    /** While searching: what each source returned, or why it sat out. */
    coverage: String? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                    )
                )
                .statusBarsPadding()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                onClick = onOpenSearch,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = query.ifBlank { "Search tags…" }.replace('_', ' '),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClearSearch) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                    }
                }
            }
            if (!coverage.isNullOrBlank()) {
                Text(
                    coverage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            SavedSearchTabs(pinned = pinned, current = query, onSelect = onSelectPinned, onPinCurrent = onPinCurrent)
        }
    }
}

/**
 * Every Discover control in one small floating bar: shuffle, filters, layout and back to top.
 * It hides with the header while scrolling down.
 */
@Composable
internal fun DiscoverControlPill(
    visible: Boolean,
    busy: Boolean,
    filtersOn: Boolean,
    viewMode: DiscoverViewMode,
    showTop: Boolean,
    onShuffle: () -> Unit,
    onFilters: () -> Unit,
    onLayout: (DiscoverViewMode) -> Unit,
    onTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                IconButton(onClick = onShuffle, enabled = !busy) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Shuffle, contentDescription = "New batch")
                }
                IconButton(onClick = onFilters) {
                    Box {
                        Icon(Icons.Default.Tune, contentDescription = "Filters and sources")
                        if (filtersOn) Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
                DiscoverLayoutButton(mode = viewMode, onSelect = onLayout)
                if (showTop) IconButton(onClick = onTop) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Back to top")
                }
            }
        }
    }
}

/** Long-press on a tile: a quick look with the everyday actions, without leaving the grid. */
@Composable
internal fun DiscoverPeek(
    wallpaper: BrainrotWallpaper,
    saved: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSet: () -> Unit,
    onSkip: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            val ratio = parseAspectRatio(wallpaper.resolution).coerceIn(0.5f, 1.8f)
            AsyncImage(
                model = wallpaper.thumbUrl.takeIf { wallpaper.isVideo && it.isNotBlank() } ?: wallpaper.gridUrl.ifBlank { wallpaper.fullUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onOpen() },
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(6.dp),
                ) {
                    PeekAction(if (saved) "Saved" else "Save", Icons.Default.Bookmark, highlighted = saved, onClick = onSave)
                    PeekAction("Set", Icons.Default.Wallpaper, onClick = onSet)
                    PeekAction("Skip", Icons.Default.SkipNext, onClick = onSkip)
                    PeekAction("Open", Icons.Default.OpenInFull, onClick = onOpen)
                    PeekAction("Select", Icons.Default.CheckCircle, onClick = onSelect)
                }
            }
        }
    }
}

@Composable
private fun PeekAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, highlighted: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = if (highlighted) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** The bookmark that pops over a tile when it's double-tapped to save. */
@Composable
internal fun SaveBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val scale = remember(trigger) { Animatable(0.4f) }
    val alpha = remember(trigger) { Animatable(1f) }
    LaunchedEffect(trigger) {
        scale.animateTo(1.25f, tween(220))
        scale.animateTo(1f, tween(120))
        alpha.animateTo(0f, tween(380))
    }
    Icon(
        Icons.Default.Bookmark,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                shadowElevation = 12f
            },
    )
}

/**
 * The average colour of [url]'s picture, from a tiny decode; used to tint Discover's backdrop
 * with whatever is on screen. Null until it's known.
 */
@Composable
internal fun rememberMoodColor(url: String?): Color? {
    val context = LocalContext.current
    var colour by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).size(12).allowHardware(false).build()
            )
        }
        val bmp = ((result as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        colour = averageColour(bmp)
    }
    return colour
}

private fun averageColour(bmp: Bitmap): Color {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (x in 0 until bmp.width) for (y in 0 until bmp.height) {
        val p = bmp.getPixel(x, y)
        r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff; n++
    }
    if (n == 0L) return Color.Black
    return Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt())
}

/** A soft glow of [colour] behind the top of the page. */
@Composable
internal fun MoodBackdrop(colour: Color?, modifier: Modifier = Modifier) {
    val strength by animateFloatAsState(if (colour == null) 0f else 1f, tween(900), label = "mood")
    val c = colour ?: Color.Transparent
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    0f to c.copy(alpha = 0.30f * strength),
                    0.55f to c.copy(alpha = 0.08f * strength),
                    1f to Color.Transparent,
                )
            )
    )
}
