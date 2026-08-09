package com.chrisalvis.rotato.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.DownloadState
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.UpdateCheckResult
import com.chrisalvis.rotato.data.UpdateInfo
import com.chrisalvis.rotato.data.UpdateRepository
import com.chrisalvis.rotato.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings is now a router into nav-based sub-screens, so SettingsScreen itself recomposes fresh
 * every time the user navigates back into it — a plain `remember` here would re-run the update
 * check (and re-show a just-dismissed dialog) on every such visit. This survives that.
 */
private object UpdateCheckSessionState {
    var hasCheckedThisSession = false
}

/**
 * Top-level Settings screen. Appearance stays here (simple, high-traffic);
 * everything else lives in a category sub-screen reached via [SettingsCategoryRow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRotationWallpaper: () -> Unit = {},
    onNavigateToNsfwPrivacy: () -> Unit = {},
    onNavigateToDiscoverSources: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {},
    onNavigateToAboutData: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rotatoPrefs = remember { RotatoPreferences(context) }

    // Update checker state — auto-checked on Settings open, surfaced via a dialog.
    var updateCheckState by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    // Auto-check on open — popup if update available and not ignored. Once per app session,
    // not once per visit, or navigating into a sub-screen and back would re-show a dismissed dialog.
    LaunchedEffect(Unit) {
        if (UpdateCheckSessionState.hasCheckedThisSession) return@LaunchedEffect
        UpdateCheckSessionState.hasCheckedThisSession = true
        val ignoredVersion = rotatoPrefs.ignoredUpdateVersion.first()
        val result = UpdateRepository.checkForUpdate()
        updateCheckState = result
        if (result is UpdateCheckResult.UpdateAvailable &&
            result.info.versionCode != ignoredVersion) {
            pendingUpdateInfo = result.info
        }
    }

    // Update available dialog — changelog + 3-button layout
    pendingUpdateInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            downloadState = downloadState,
            onDownloadStateChange = { downloadState = it },
            onDismiss = { pendingUpdateInfo = null },
            onIgnore = {
                scope.launch {
                    rotatoPrefs.setIgnoredUpdateVersion(info.versionCode)
                    pendingUpdateInfo = null
                    downloadState = DownloadState.Idle
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
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
                    SettingsSection(title = "Appearance") {
                        val themeMode by rotatoPrefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
                        val dynamicColor by rotatoPrefs.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
                        val themeOptions = listOf(
                            ThemeMode.SYSTEM to "Follow system",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                            ThemeMode.AMOLED to "AMOLED black",
                        )
                        themeOptions.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = { scope.launch { rotatoPrefs.setThemeMode(mode) } }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                        Spacer(Modifier.size(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dynamic color")
                                Text(
                                    "Use the Material You palette from your wallpaper (Android 12+)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { value ->
                                    scope.launch { rotatoPrefs.setDynamicColor(value) }
                                }
                            )
                        }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsCategoryRow(
                        icon = Icons.Default.Tune,
                        title = "Rotation & Wallpaper",
                        subtitle = "Interval, order, target, fit, video previews, triggers, auto-pause, widget",
                        onClick = onNavigateToRotationWallpaper,
                    )
                    HorizontalDivider()
                    SettingsCategoryRow(
                        icon = Icons.Default.Shield,
                        title = "NSFW & Privacy",
                        subtitle = "Blur toggles, stealth collection",
                        onClick = onNavigateToNsfwPrivacy,
                    )
                    HorizontalDivider()
                    SettingsCategoryRow(
                        icon = Icons.Default.Explore,
                        title = "Discover & Sources",
                        subtitle = "Prefetch batch size, Wi-Fi only, manage sources",
                        onClick = onNavigateToDiscoverSources,
                    )
                    HorizontalDivider()
                    SettingsCategoryRow(
                        icon = Icons.Default.Link,
                        title = "Integrations",
                        subtitle = "MyAnimeList",
                        onClick = onNavigateToIntegrations,
                    )
                    HorizontalDivider()
                    SettingsCategoryRow(
                        icon = Icons.Default.Info,
                        title = "About & Data",
                        subtitle = "Backup & restore, danger zone, diagnostics, stats, about",
                        onClick = onNavigateToAboutData,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}
