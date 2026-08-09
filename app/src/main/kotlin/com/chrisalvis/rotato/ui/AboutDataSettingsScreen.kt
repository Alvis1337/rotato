package com.chrisalvis.rotato.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.BuildConfig
import com.chrisalvis.rotato.data.AppErrorLog
import com.chrisalvis.rotato.data.DownloadState
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.UpdateCheckResult
import com.chrisalvis.rotato.data.UpdateInfo
import com.chrisalvis.rotato.data.UpdateRepository
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDataSettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToStats: () -> Unit = {},
    onNavigateToSourceHealth: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rotatoPrefs = remember { RotatoPreferences(context) }

    var showClearDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    // "Check for Updates" — self-contained here; the auto-check-on-open dialog lives on the
    // top-level Settings screen, this is the manual on-demand check triggered by the button below.
    var updateCheckState by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val googleDriveBackupEnabled by viewModel.googleDriveBackupEnabled.collectAsStateWithLifecycle()

    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportSettings(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importSettings(it) } }

    LaunchedEffect(backupState) {
        when (backupState) {
            BackupState.SUCCESS -> snackbarHostState.showSnackbar("Backup complete")
            BackupState.ERROR -> snackbarHostState.showSnackbar("Backup failed — check storage permissions")
            else -> {}
        }
    }

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
                        android.widget.Toast.makeText(context, "All photos cleared", android.widget.Toast.LENGTH_SHORT).show()
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

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("Import backup?") },
            text = { Text("This will overwrite your current sources, API keys, and preferences with the contents of the selected file. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                ) {
                    Text("Import", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("About & Data", fontWeight = FontWeight.Bold) }
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
                    SettingsSection(title = "Backup & Restore") {
                        Text(
                            text = "Export or import your sources, API keys, and preferences. MAL auth tokens are not backed up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SettingsToggleRow(
                            title = "Google Drive backup",
                            subtitle = "Automatically back up settings to your Google account",
                            checked = googleDriveBackupEnabled,
                            onCheckedChange = { viewModel.setGoogleDriveBackupEnabled(it) }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("rotato-backup-$today.json") },
                                enabled = backupState == BackupState.IDLE,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (backupState == BackupState.BUSY) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Export")
                                }
                            }
                            OutlinedButton(
                                onClick = { showImportConfirmDialog = true },
                                enabled = backupState == BackupState.IDLE,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Import")
                            }
                        }
                    }

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
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSection(title = "Diagnostics") {
                        var showDebugLog by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = onNavigateToSourceHealth,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Source Health")
                        }
                        OutlinedButton(
                            onClick = { showDebugLog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Debug Log")
                        }
                        if (showDebugLog) {
                            DebugLogSheet(onDismiss = { showDebugLog = false })
                        }
                    }

                    SettingsSection(title = "Stats") {
                        OutlinedButton(
                            onClick = onNavigateToStats,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View Stats")
                        }
                    }

                    SettingsSection(title = "About") {
                        OutlinedButton(
                            onClick = onShowOnboarding,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View App Tour")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Version", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val updateAvailable = updateCheckState is UpdateCheckResult.UpdateAvailable
                        OutlinedButton(
                            onClick = {
                                if (updateAvailable) {
                                    pendingUpdateInfo = (updateCheckState as UpdateCheckResult.UpdateAvailable).info
                                } else {
                                    updateChecking = true
                                    scope.launch {
                                        val result = UpdateRepository.checkForUpdate()
                                        updateCheckState = result
                                        updateChecking = false
                                        if (result is UpdateCheckResult.UpdateAvailable) {
                                            pendingUpdateInfo = result.info
                                        } else if (result is UpdateCheckResult.UpToDate) {
                                            android.widget.Toast.makeText(context, "You're up to date!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (result is UpdateCheckResult.Error) {
                                            android.widget.Toast.makeText(context, "Update check failed: ${result.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !updateChecking
                        ) {
                            if (updateChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Checking…")
                            } else if (updateAvailable) {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                val info = (updateCheckState as UpdateCheckResult.UpdateAvailable).info
                                Text("Update available — v${info.versionName}")
                            } else {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Check for Updates")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, OssLicensesMenuActivity::class.java)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Source Licenses")
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://alvis1337.github.io/rotato/privacy/"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Privacy Policy")
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://alvis1337.github.io/rotato/terms/"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Terms of Service")
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://alvis1337.github.io/rotato/"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Website")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugLogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logText = remember { AppErrorLog.getLog() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("Rotato Debug Log", logText))
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        try {
                            val file = java.io.File(context.filesDir, "rotato_debug.log")
                            if (!file.exists()) return@TextButton
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Debug Log"))
                        } catch (_: Exception) {}
                    }) { Text("Share") }
                    TextButton(onClick = { AppErrorLog.clear(); onDismiss() }) { Text("Clear") }
                }
            }
            HorizontalDivider()
            if (logText.isBlank() || logText == "(no log yet)") {
                Text(
                    "No errors logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val lines = remember(logText) { logText.lines() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(lines.size) { i ->
                        Text(
                            lines[i],
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
