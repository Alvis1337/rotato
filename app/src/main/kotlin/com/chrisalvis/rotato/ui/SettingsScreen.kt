package com.chrisalvis.rotato.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.RotationInterval
import com.chrisalvis.rotato.data.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    malViewModel: MalViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSources: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val malLoggedIn by malViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val malUsername by malViewModel.username.collectAsStateWithLifecycle()
    val malAnimeCount by malViewModel.animeCount.collectAsStateWithLifecycle()
    val malLoading by malViewModel.loading.collectAsStateWithLifecycle()
    val malError by malViewModel.error.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all photos?") },
            text = { Text("This removes all photos from the rotation pool and stops rotation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
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

            HorizontalDivider()

            SettingsSection(title = "Order") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shuffle")
                        Text(
                            text = if (settings.shuffleMode) "Photos play in random order"
                                   else "Photos play in the order they were added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.shuffleMode,
                        onCheckedChange = { viewModel.setShuffleMode(it) }
                    )
                }
            }

            HorizontalDivider()

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

            HorizontalDivider()

            SettingsSection(title = "Sources") {
                FilledTonalButton(
                    onClick = onNavigateToSources,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Sources")
                }
            }

            HorizontalDivider()

            SettingsSection(title = "MyAnimeList") {
                if (malLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (malLoggedIn) {
                    Text(
                        "Connected as $malUsername",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (malAnimeCount > 0) {
                        Text(
                            "$malAnimeCount anime in list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (malError != null) {
                        Text(
                            malError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { malViewModel.refresh() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Refresh List") }
                        OutlinedButton(
                            onClick = { malViewModel.logout() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Disconnect") }
                    }
                } else {
                    if (malError != null) {
                        Text(
                            malError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "Connect your MAL account to use your anime watch list as discover queries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { malViewModel.login(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Connect MyAnimeList") }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Danger Zone") {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All Photos")
                }
            }

            HorizontalDivider()

            SettingsSection(title = "About") {
                OutlinedButton(
                    onClick = {
                        OssLicensesMenuActivity.setActivityTitle("Open Source Licenses")
                        context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Source Licenses")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
