package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrisalvis.rotato.data.DownloadState
import com.chrisalvis.rotato.data.UpdateInfo
import com.chrisalvis.rotato.data.UpdateRepository
import kotlinx.coroutines.launch

/**
 * Shared section header + content wrapper used across the Settings screen and
 * its category sub-screens (Rotation & Wallpaper, NSFW & Privacy, Discover &
 * Sources, Integrations, About & Data).
 */
@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

/**
 * "Update available" dialog — changelog + download/install + later/ignore actions.
 * Shared between the top-level Settings screen (auto-check on open) and the About & Data
 * sub-screen ("Check for Updates" button), which each drive their own check state.
 */
@Composable
internal fun UpdateAvailableDialog(
    info: UpdateInfo,
    downloadState: DownloadState,
    onDownloadStateChange: (DownloadState) -> Unit,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDownloading = downloadState is DownloadState.Progress || downloadState is DownloadState.Installing

    Dialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Update Available",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "v${info.versionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                HorizontalDivider()

                // Scrollable changelog
                if (info.releaseNotes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "What's New",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            formatChangelogLines(info.releaseNotes).forEach { (text, isHeader) ->
                                if (text.isBlank()) {
                                    Spacer(Modifier.size(4.dp))
                                } else if (isHeader) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                } else {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Download progress
                when (val ds = downloadState) {
                    is DownloadState.Progress -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Downloading… ${ds.percent}%", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { ds.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is DownloadState.Installing -> Text("Opening installer…", style = MaterialTheme.typography.bodySmall)
                    is DownloadState.Failed -> Text(
                        "Failed: ${ds.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> {}
                }

                HorizontalDivider()

                // Primary action
                Button(
                    onClick = {
                        scope.launch {
                            UpdateRepository.downloadAndInstall(context, info) { state ->
                                onDownloadStateChange(state)
                                if (state is DownloadState.Installing) {
                                    onDismiss()
                                    onDownloadStateChange(DownloadState.Idle)
                                }
                            }
                        }
                    },
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isDownloading) "Downloading…" else "Download & Install")
                }

                // Secondary actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDismiss(); onDownloadStateChange(DownloadState.Idle) },
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Later") }
                    TextButton(
                        onClick = onIgnore,
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Ignore") }
                }
            }
        }
    }
}

/** Returns pairs of (displayText, isHeader) for richer rendering in the changelog dialog. */
internal fun formatChangelogLines(raw: String): List<Pair<String, Boolean>> =
    raw.lines().map { line ->
        val t = line.trim()
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("`(.*?)`"), "$1")
        when {
            t.startsWith("### ") -> t.removePrefix("### ") to true
            t.startsWith("## ")  -> t.removePrefix("## ")  to true
            t.startsWith("# ")   -> t.removePrefix("# ")   to true
            t.startsWith("- ") || t.startsWith("* ") -> "• ${t.drop(2)}" to false
            else -> t to false
        }
    }
