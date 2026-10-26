package com.chrisalvis.rotato.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.RotationInterval
import com.chrisalvis.rotato.data.RotatoSettings
import com.chrisalvis.rotato.data.SchedulePreferences
import com.chrisalvis.rotato.data.WallpaperTarget
import kotlinx.coroutines.delay

/**
 * Everything about rotation in one sheet: on/off, how often, order, which screens and which
 * collections feed it. The full list of options stays in Settings › Rotation & Wallpaper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenMoreSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RotationControls(
                viewModel = viewModel,
                onOpenSchedule = { onDismiss(); onOpenSchedule() },
                onOpenMoreSettings = { onDismiss(); onOpenMoreSettings() },
            )
        }
    }
}

/** The sheet's contents. */
@Composable
private fun RotationControls(
    viewModel: HomeViewModel,
    onOpenSchedule: () -> Unit,
    onOpenMoreSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastRotationMs by viewModel.lastRotationMs.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val sizes by viewModel.collectionSizes.collectAsStateWithLifecycle()
    val pool by viewModel.images.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val schedulePrefs = remember { SchedulePreferences(context.applicationContext) }
    val schedule by schedulePrefs.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val nextChange = rememberNextChangeText(settings, lastRotationMs)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rotation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !settings.isEnabled -> "Off"
                        pool.isEmpty() -> "Nothing to rotate yet"
                        nextChange != null -> "Next change $nextChange"
                        else -> "Every ${rotationIntervalLabel(settings.intervalMinutes)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Same toggle the old status card had.
            Switch(checked = settings.isEnabled, onCheckedChange = { viewModel.setRotationEnabled(it) })
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Change every", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RotationInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = settings.intervalMinutes == interval.minutes,
                        onClick = { viewModel.setIntervalMinutes(interval.minutes) },
                        label = { Text(interval.label) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RotationTile(
                icon = if (settings.shuffleMode) Icons.Default.Shuffle else Icons.Default.FormatListNumbered,
                label = "Order",
                value = if (settings.shuffleMode) "Shuffle" else "In order",
                onClick = { viewModel.setShuffleMode(!settings.shuffleMode) },
                modifier = Modifier.weight(1f),
            )
            var targetMenu by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                RotationTile(
                    icon = Icons.Default.Smartphone,
                    label = "Screens",
                    value = when (settings.wallpaperTarget) {
                        WallpaperTarget.HOME_ONLY -> "Home"
                        WallpaperTarget.LOCK_ONLY -> "Lock"
                        WallpaperTarget.BOTH -> "Home & lock"
                    },
                    onClick = { targetMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                    WallpaperTarget.entries.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.label) },
                            onClick = {
                                viewModel.setWallpaperTarget(target)
                                targetMenu = false
                            },
                        )
                    }
                }
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rotating from", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    "${pool.size} image${if (pool.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (collections.isEmpty()) {
                Text(
                    "No collections yet. Save wallpapers from Discover into a collection to rotate through it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            collections.forEach { list ->
                // Locked collections keep their name hidden; unlock them in Saved to change them.
                val locked = list.isLocked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !locked) { viewModel.toggleCollectionAsRotation(list) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = list.useAsRotation,
                        onCheckedChange = { viewModel.toggleCollectionAsRotation(list) },
                        enabled = !locked,
                    )
                    if (locked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (locked) "Locked collection" else list.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${sizes[list.id] ?: 0}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onOpenSchedule),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Schedule", style = MaterialTheme.typography.bodyLarge)
                val active = schedule.count { it.enabled }
                Text(
                    when {
                        schedule.isEmpty() -> "Set different collections for different times"
                        active == 0 -> "${schedule.size} saved, none on"
                        else -> "$active active"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        TextButton(onClick = onOpenMoreSettings, modifier = Modifier.align(Alignment.Start)) {
            Text("More rotation settings")
        }
    }
}

@Composable
private fun RotationTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "every 15 min", "every hour", "every 4 hours", "every day": reads after "every". */
internal fun rotationIntervalLabel(minutes: Int): String = when {
    minutes == 60 -> "hour"
    minutes == 1440 -> "day"
    minutes % 1440 == 0 -> "${minutes / 1440} days"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes min"
}

/**
 * When the next timed change is due ("at 14:30", "tomorrow at 07:00", "any moment"), or null when
 * rotation is off or hasn't run yet. Refreshes every half minute so it doesn't go stale.
 */
@Composable
internal fun rememberNextChangeText(settings: RotatoSettings, lastRotationMs: Long): String? {
    val context = LocalContext.current
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    if (!settings.isEnabled || lastRotationMs <= 0L) return null
    val next = lastRotationMs + settings.intervalMinutes * 60_000L
    if (next <= nowMs) return "any moment"
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(next))
    return if (android.text.format.DateUtils.isToday(next)) "at $time" else "tomorrow at $time"
}
