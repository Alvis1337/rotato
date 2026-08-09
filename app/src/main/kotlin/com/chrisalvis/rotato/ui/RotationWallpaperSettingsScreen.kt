package com.chrisalvis.rotato.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.AutoPauseSettings
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.RotationInterval
import com.chrisalvis.rotato.data.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationWallpaperSettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSchedule: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val widgetCollectionId by viewModel.widgetCollectionId.collectAsStateWithLifecycle()
    val autoPauseSettings by viewModel.autoPauseSettings.collectAsStateWithLifecycle()
    val chargingTriggerEnabled by viewModel.chargingTriggerEnabled.collectAsStateWithLifecycle()
    val autoFavoriteEnabled by viewModel.autoFavoriteEnabled.collectAsStateWithLifecycle()
    val autoFavoriteMinutes by viewModel.autoFavoriteMinutes.collectAsStateWithLifecycle()
    val autoRefillEnabled by viewModel.autoRefillEnabled.collectAsStateWithLifecycle()
    val autoRefillMinCount by viewModel.autoRefillMinCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Rotation & Wallpaper", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSection(title = "Rotation Interval") {
                        RotationInterval.entries.forEach { interval ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.intervalMinutes == interval.minutes,
                                    onClick = { viewModel.setIntervalMinutes(interval.minutes) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(interval.label)
                            }
                        }
                    }

                    SettingsSection(title = "Order") {
                        SettingsToggleRow(
                            title = "Shuffle",
                            subtitle = if (settings.shuffleMode) "Photos play in random order"
                                       else "Photos play in the order they were added",
                            checked = settings.shuffleMode,
                            onCheckedChange = { viewModel.setShuffleMode(it) }
                        )
                    }

                    SettingsSection(title = "Wallpaper Target") {
                        WallpaperTarget.entries.forEach { target ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.wallpaperTarget == target,
                                    onClick = { viewModel.setWallpaperTarget(target) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(target.label)
                            }
                        }
                    }

                    SettingsSection(title = "Wallpaper Fit") {
                        com.chrisalvis.rotato.data.WallpaperFit.entries.forEach { fit ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.wallpaperFit == fit,
                                    onClick = { viewModel.setWallpaperFit(fit) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(fit.label)
                            }
                        }
                    }

                    SettingsSection(title = "Video Previews") {
                        com.chrisalvis.rotato.data.VideoPreviewMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setVideoPreviewMode(mode) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.videoPreviewMode == mode,
                                    onClick = { viewModel.setVideoPreviewMode(mode) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mode.label)
                                    Text(
                                        mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSection(title = "Rotation Triggers") {
                        RotationTriggersSection(
                            chargingTriggerEnabled = chargingTriggerEnabled,
                            autoFavoriteEnabled = autoFavoriteEnabled,
                            autoFavoriteMinutes = autoFavoriteMinutes,
                            autoRefillEnabled = autoRefillEnabled,
                            autoRefillMinCount = autoRefillMinCount,
                            onChargingTriggerToggle = { viewModel.setChargingTriggerEnabled(it) },
                            onAutoFavoriteToggle = { viewModel.setAutoFavoriteEnabled(it) },
                            onAutoFavoriteMinutesChange = { viewModel.setAutoFavoriteMinutes(it) },
                            onAutoRefillToggle = { viewModel.setAutoRefillEnabled(it) },
                            onAutoRefillMinCountChange = { viewModel.setAutoRefillMinCount(it) },
                        )
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSection(title = "Schedule") {
                        OutlinedButton(
                            onClick = onNavigateToSchedule,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Schedule")
                        }
                    }

                    SettingsSection(title = "Auto-Pause") {
                        AutoPauseSection(
                            settings = autoPauseSettings,
                            onNightToggle = { viewModel.setAutoPauseNight(it) },
                            onNightHoursChange = { start, end -> viewModel.setAutoPauseNightHours(start, end) },
                            onChargingToggle = { viewModel.setAutoPauseCharging(it) },
                            onRotateScreenOnToggle = { viewModel.setRotateScreenOn(it) },
                        )
                    }

                    SettingsSection(title = "Widget") {
                        WidgetCollectionDropdown(
                            selectedCollectionId = widgetCollectionId,
                            lists = collections,
                            onSelect = viewModel::setWidgetCollectionId
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RotationTriggersSection(
    chargingTriggerEnabled: Boolean,
    autoFavoriteEnabled: Boolean,
    autoFavoriteMinutes: Int,
    autoRefillEnabled: Boolean,
    autoRefillMinCount: Int,
    onChargingTriggerToggle: (Boolean) -> Unit,
    onAutoFavoriteToggle: (Boolean) -> Unit,
    onAutoFavoriteMinutesChange: (Int) -> Unit,
    onAutoRefillToggle: (Boolean) -> Unit,
    onAutoRefillMinCountChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsToggleRow(
            title = "Change wallpaper on charge",
            subtitle = "Rotate to next wallpaper when plugged in",
            checked = chargingTriggerEnabled,
            onCheckedChange = onChargingTriggerToggle
        )

        SettingsToggleRow(
            title = "Auto-favorite long-running wallpapers",
            subtitle = "Save wallpapers that stay on screen past your keep threshold",
            checked = autoFavoriteEnabled,
            onCheckedChange = onAutoFavoriteToggle
        )

        AnimatedVisibility(visible = autoFavoriteEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Keep threshold (minutes)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30, 60, 120, 240).forEach { minutes ->
                        FilterChip(
                            selected = autoFavoriteMinutes == minutes,
                            onClick = { onAutoFavoriteMinutesChange(minutes) },
                            label = { Text("$minutes") }
                        )
                    }
                }
            }
        }

        SettingsToggleRow(
            title = "Auto-refill MAL collections",
            subtitle = "Top up low MAL-managed collections after each rotation",
            checked = autoRefillEnabled,
            onCheckedChange = onAutoRefillToggle
        )

        AnimatedVisibility(visible = autoRefillEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Refill when below",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 20, 50).forEach { count ->
                        FilterChip(
                            selected = autoRefillMinCount == count,
                            onClick = { onAutoRefillMinCountChange(count) },
                            label = { Text("$count images") }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoPauseSection(
    settings: AutoPauseSettings,
    onNightToggle: (Boolean) -> Unit,
    onNightHoursChange: (Int, Int) -> Unit,
    onChargingToggle: (Boolean) -> Unit,
    onRotateScreenOnToggle: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsToggleRow(
            title = "Pause at night",
            subtitle = "Skip rotation during quiet hours",
            checked = settings.nightEnabled,
            onCheckedChange = onNightToggle
        )

        AnimatedVisibility(visible = settings.nightEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "From",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HourDropdown(
                        hour = settings.nightStartHour,
                        onSelect = { onNightHoursChange(it, settings.nightEndHour) }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Until",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HourDropdown(
                        hour = settings.nightEndHour,
                        onSelect = { onNightHoursChange(settings.nightStartHour, it) }
                    )
                }
            }
        }

        SettingsToggleRow(
            title = "Pause while charging",
            subtitle = "Stop rotating when plugged in",
            checked = settings.chargingEnabled,
            onCheckedChange = onChargingToggle
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text("Rotate while screen is on")
                Text(
                    "When off, wallpaper only changes while the screen is off. Turning this on may cause sudden changes or accent color shifts while you're using your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.rotateScreenOn, onCheckedChange = onRotateScreenOnToggle)
        }
    }
}

@Composable
private fun WidgetCollectionDropdown(
    selectedCollectionId: String,
    lists: List<LocalList>,
    onSelect: (String) -> Unit,
) {
    SettingsDropdown(
        label = "Widget collection",
        description = "Choose which collection the home-screen widget previews.",
        items = listOf<LocalList?>(null) + lists,
        selectedLabel = lists.firstOrNull { it.id == selectedCollectionId }?.name ?: "Main rotation queue",
        itemLabel = { it?.name ?: "Main rotation queue" },
        onSelect = { onSelect(it?.id ?: "") },
    )
}

@Composable
private fun HourDropdown(hour: Int, onSelect: (Int) -> Unit) {
    SettingsDropdown(
        items = (0..23).toList(),
        selectedLabel = formatHour(hour),
        itemLabel = { formatHour(it) },
        onSelect = onSelect,
    )
}

private fun formatHour(hour: Int): String = when (hour) {
    0 -> "12 AM"
    in 1..11 -> "$hour AM"
    12 -> "12 PM"
    else -> "${hour - 12} PM"
}
