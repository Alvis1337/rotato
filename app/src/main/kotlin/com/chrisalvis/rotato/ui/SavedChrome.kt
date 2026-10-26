package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// Shared pieces of the Saved tab: the Library ("In rotation") and collections.

/** Status green: something is in the wallpaper rotation. */
internal val SavedRotatingGreen = Color(0xFF2EB872)

/** Three portrait columns on a phone; unfolded or on a tablet, more columns rather than a side pane. */
internal fun savedGridColumns(width: Dp): Int = maxOf(3, (width / 130.dp).toInt())

/** A small round status light: green while rotating, a dim grey otherwise. */
@Composable
internal fun SavedStatusDot(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .background(if (on) SavedRotatingGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
    )
}

/** The single row of pills under a Saved sub-screen's top bar. */
@Composable
internal fun SavedActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A rounded action pill. [badgeCount] shows a small count, e.g. how many filters are on. */
@Composable
internal fun SavedPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    badgeCount: Int = 0,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            leading?.invoke()
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (badgeCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Text(
                        "$badgeCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** One compact, dismissible line for things worth knowing (low storage, a skipped rotation). */
@Composable
internal fun SavedNoticeLine(
    text: String,
    warning: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(container, RoundedCornerShape(10.dp))
            .padding(start = 12.dp),
    ) {
        Icon(
            if (warning) Icons.Default.Warning else Icons.Default.Info,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = onContainer, modifier = Modifier.size(16.dp))
        }
    }
}

/** Bottom sheet holding every filter and sort control of a Saved grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedFilterSheet(
    showReset: Boolean,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    "Filter & sort",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (showReset) TextButton(onClick = onReset) { Text("Reset") }
            }
            content()
        }
    }
}

@Composable
internal fun SavedSheetSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * A row of up to four pictures (a cover, or a peek at what's inside). [models] are anything Coil
 * loads: URLs or files. Empty shows a placeholder.
 */
@Composable
internal fun SavedMosaic(
    models: List<Any>,
    modifier: Modifier = Modifier,
    grid: Boolean = true,
    placeholder: ImageVector = Icons.Default.FolderOpen,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val shown = models.take(4)
        when {
            shown.isEmpty() -> Icon(
                placeholder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
            )
            shown.size == 4 && grid -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { model ->
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            grid -> AsyncImage(
                model = shown.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEach { model ->
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
