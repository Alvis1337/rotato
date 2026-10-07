package com.chrisalvis.rotato.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared pieces of the full-screen viewers (Discover and Collections): glass pills over the
// image, and a dock that pulls up into a details sheet holding the same actions.

/**
 * The viewer's bottom dock: a floating glass card with a pull handle, the image's name and a
 * peek at its tags, and the main actions. Tapping or pulling up the top part opens the details
 * sheet, which repeats the same actions so the two read as one surface at two heights.
 */
@Composable
internal fun ViewerDock(
    title: String,
    subtitle: String?,
    onExpand: () -> Unit,
    actions: @Composable (Modifier) -> Unit,
    hint: Boolean,
    onHinted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The first time the dock shows, the handle nudges upward twice so pulling it is discoverable.
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(hint) {
        if (!hint) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        repeat(2) {
            nudge.animateTo(-7f, tween(200, easing = FastOutLinearInEasing))
            nudge.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        onHinted()
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Show details", onClick = onExpand)
                    .padding(horizontal = 18.dp)
                    .padding(top = 8.dp, bottom = 6.dp)
            ) {
                Box(
                    Modifier
                        .graphicsLayer { translationY = nudge.value }
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(50))
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.graphicsLayer { translationY = nudge.value }
                    )
                }
            }
            actions(Modifier.fillMaxWidth().padding(horizontal = 6.dp))
        }
    }
}

@Composable
internal fun DockAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val content = LocalContentColor.current
    // The folded cover screen fits six of these across, so they shrink a little there.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (compact) 40.dp else 44.dp)
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primary else content.copy(alpha = 0.12f),
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimary else content,
                modifier = Modifier.size(if (compact) 20.dp else 22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp) else MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun InfoPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    container: Color = Color.Black.copy(alpha = 0.55f),
    content: Color = Color.White,
    bold: Boolean = false,
) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                color = content
            )
        }
    }
}

/**
 * Size class, dimensions, orientation and (on a foldable) "Fold-friendly" pills for an image's
 * "WxH" [resolution]. Emits nothing for an unknown size. Call inside a FlowRow.
 */
@Composable
internal fun ImageFactPills(resolution: String) {
    val (w, h) = remember(resolution) {
        resolution.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }
            .let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
    }
    if (w <= 0 || h <= 0) return
    val foldCanvas = rememberFoldCanvas()
    val longSide = maxOf(w, h)
    InfoPill(
        when {
            longSide >= 7680 -> "8K"
            longSide >= 3840 -> "4K"
            longSide >= 2560 -> "QHD"
            longSide >= 1920 -> "FHD"
            longSide >= 1280 -> "HD"
            else -> "Low res"
        },
        bold = true,
    )
    InfoPill("$w × $h")
    val ratio = w.toFloat() / h
    InfoPill(if (ratio > 1.15f) "Landscape" else if (ratio < 0.87f) "Portrait" else "Square")
    if (foldCanvas != null && com.chrisalvis.rotato.data.isFoldFriendly(w, h, foldCanvas)) {
        InfoPill(
            "Fold-friendly",
            icon = Icons.Default.Smartphone,
            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
            content = MaterialTheme.colorScheme.onTertiary,
        )
    }
}
