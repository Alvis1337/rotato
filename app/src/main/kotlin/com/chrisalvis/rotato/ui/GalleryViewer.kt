package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// The one look shared by every full-screen viewer (Discover, collections, Saved): glass buttons
// along the top, and along the bottom a caption, a filmstrip to jump around, and the main actions
// as labelled buttons. Swiping up (or tapping the caption or the info button) opens the details.

private val Glass = Color.Black.copy(alpha = 0.5f)

/** One labelled button in the viewer's action row. [primary] is the filled one (Set). */
internal data class ViewerAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val active: Boolean = false,
    val enabled: Boolean = true,
)

/** Back on the left, "12 of 148" in the middle, details on the right. */
@Composable
internal fun ViewerTopBar(
    position: String?,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (position != null) {
                Text(
                    position,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Glass, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        GlassIconButton(Icons.Outlined.Info, "Details", onDetails)
    }
}

@Composable
internal fun GlassIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Glass)
            .clickable(onClickLabel = label, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * The viewer's bottom: caption (tap for details), an optional [extra] row (e.g. a video's seek
 * bar lives above this, so nothing here), the filmstrip, and the action row. Dragging it up opens
 * the details.
 */
@Composable
internal fun ViewerBottomBar(
    title: String,
    subtitle: String?,
    actions: List<ViewerAction>,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    filmstrip: List<Any?> = emptyList(),
    currentIndex: Int = 0,
    onJump: (Int) -> Unit = {},
    /** Any touch on the bar, so a playing video's controls don't hide while it's being used. */
    onTouch: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(onTouch) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    onTouch()
                }
            }
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))))
            .pointerInput(onDetails) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { change, dy -> dragged += dy; if (dy < 0) change.consume() },
                    onDragEnd = { if (dragged < -48.dp.toPx()) onDetails() },
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 36.dp, bottom = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Show details", onClick = onDetails)
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (filmstrip.size > 1) Filmstrip(filmstrip, currentIndex, onJump)
        ViewerActionRow(actions)
    }
}

@Composable
internal fun ViewerActionRow(actions: List<ViewerAction>, modifier: Modifier = Modifier) {
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
    Row(modifier = modifier.fillMaxWidth()) {
        actions.forEach { a ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = a.enabled, onClickLabel = a.label, onClick = a.onClick)
                    .padding(vertical = 4.dp)
                    .graphicsLayer { alpha = if (a.enabled) 1f else 0.45f }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (compact) 44.dp else 48.dp)
                        .background(
                            when {
                                a.primary -> Color.White
                                a.active -> MaterialTheme.colorScheme.primary
                                else -> Color.White.copy(alpha = 0.14f)
                            },
                            CircleShape
                        )
                ) {
                    Icon(
                        a.icon,
                        contentDescription = null,
                        tint = when {
                            a.primary -> Color(0xFF0B0B0F)
                            a.active -> MaterialTheme.colorScheme.onPrimary
                            else -> Color.White
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    a.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = if (compact) 11.sp else 12.sp, letterSpacing = 0.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small thumbnails of every image in the viewer, the current one outlined; tap one to jump. */
@Composable
private fun Filmstrip(models: List<Any?>, current: Int, onJump: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 4).coerceAtLeast(0))
    LaunchedEffect(current) {
        // Keep the current one roughly centred.
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(9)
        state.animateScrollToItem((current - visible / 2).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        itemsIndexed(models) { i, model ->
            val selected = i == current
            AsyncImage(
                model = model,
                contentDescription = if (selected) "Current image" else "Image ${i + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = if (selected) 30.dp else 24.dp, height = if (selected) 44.dp else 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier.graphicsLayer { alpha = 0.7f })
                    .clickable { onJump(i) }
            )
        }
    }
}

/** "4K", "QHD" and so on for an image's long side, or null when unknown. */
internal fun sizeClassOf(width: Int, height: Int): String? {
    val longSide = maxOf(width, height)
    if (width <= 0 || height <= 0) return null
    return when {
        longSide >= 7680 -> "8K"
        longSide >= 3840 -> "4K"
        longSide >= 2560 -> "QHD"
        longSide >= 1920 -> "FHD"
        longSide >= 1280 -> "HD"
        else -> "Low res"
    }
}

/** "4K · 2160 × 3840" for a "WxH" resolution string, or null when it can't be read. */
internal fun sizeCaption(resolution: String): String? {
    val parts = resolution.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 2) return null
    val (w, h) = parts[0] to parts[1]
    return sizeClassOf(w, h)?.let { "$it · $w × $h" }
}

// ── Details sheet pieces, shared by every viewer so the sheet reads as one tidy list ──

/** The sheet's heading: the image's name and one line of where it's from. */
@Composable
internal fun DetailsHeader(title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small grey label over a section of the sheet. */
@Composable
internal fun DetailsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Rows grouped on one rounded card, separated by hairlines, like the rotation sheet. */
@Composable
internal fun DetailsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}

/** One action in a [DetailsGroup]: icon, label, and optionally a short note on the right. */
@Composable
internal fun DetailsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (divider) {
            Box(
                Modifier
                    .padding(start = 54.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * Tags as quiet chips, the first dozen only until "Show all" is tapped, so a post with fifty tags
 * doesn't bury everything below it. Tap searches; hold (when [onHold] is given) offers options.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DetailsTags(tags: List<String>, onTap: (String) -> Unit, onHold: ((String) -> Unit)? = null) {
    if (tags.isEmpty()) return
    var expanded by remember(tags) { mutableStateOf(false) }
    val limit = 12
    val shown = if (expanded) tags else tags.take(limit)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEach { tag ->
            Text(
                tag.replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(onClick = { onTap(tag) }, onLongClick = onHold?.let { { it(tag) } })
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        if (tags.size > limit) {
            Text(
                if (expanded) "Show less" else "Show all ${tags.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}
