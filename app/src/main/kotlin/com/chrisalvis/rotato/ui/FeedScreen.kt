package com.chrisalvis.rotato.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.FeedSyncResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateBack: () -> Unit,
    onBrowseFeed: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Feeds", fontWeight = FontWeight.Bold) },
            )
        }
    ) { padding ->
        FeedBody(
            viewModel = viewModel,
            onBrowseFeed = onBrowseFeed,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
internal fun FeedBody(
    viewModel: FeedViewModel,
    onBrowseFeed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val addFeedState by viewModel.addFeedState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (feeds.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "No feeds yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Add an animebacks feed URL to auto-sync wallpapers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Feed")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Feed")
                        }
                    }
                }
                items(feeds, key = { it.id }) { feed ->
                    FeedCard(
                        feed = feed,
                        syncStatus = syncStatus[feed.id],
                        onSync = { viewModel.syncFeed(feed) },
                        onDelete = { confirmDeleteId = feed.id },
                        onBrowse = { onBrowseFeed() }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddFeedDialog(
            state = addFeedState,
            onAdd = { url, headers -> viewModel.addFeed(url, headers) },
            onDismiss = {
                viewModel.resetAddFeedState()
                showAddDialog = false
            }
        )
    }

    // Close dialog when add succeeds (transition from Validating → Idle)
    var wasValidating by remember { mutableStateOf(false) }
    LaunchedEffect(addFeedState) {
        if (addFeedState is AddFeedState.Validating) wasValidating = true
        if (addFeedState is AddFeedState.Idle && wasValidating) {
            showAddDialog = false
            wasValidating = false
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Remove feed?") },
            text = { Text("Downloaded wallpapers stay in your library.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFeed(id); confirmDeleteId = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FeedCard(
    feed: FeedConfig,
    syncStatus: SyncStatus?,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onBrowse: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(feed.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    feed.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (feed.lastSyncMs > 0L) {
                    Text(
                        "Last sync: ${formatDate(feed.lastSyncMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                syncStatus?.result?.let { result ->
                    Text(
                        buildSyncSummary(result),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                syncStatus?.error?.let { err ->
                    Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBrowse) {
                    Icon(Icons.Default.GridView, contentDescription = "Browse", tint = MaterialTheme.colorScheme.primary)
                }
                // Live Search feeds (base URL, no /api/feed/ path) don't support sync
                val canSync = feed.url.contains("/api/feed/", ignoreCase = true)
                if (canSync) {
                    IconButton(onClick = onSync, enabled = syncStatus?.syncing != true) {
                        if (syncStatus?.syncing == true) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove feed", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun AddFeedDialog(
    state: AddFeedState,
    onAdd: (url: String, headers: Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    val headerRows = remember { mutableStateListOf<Pair<String, String>>() }
    val busy = state is AddFeedState.Validating

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add Feed") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Paste an animebacks feed URL. The API key is fetched automatically from your server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Feed URL") },
                    placeholder = { Text("http://192.168.1.x:3000/api/feed/my-feed") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )
                // Custom headers section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Extra Headers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { headerRows.add("" to "") },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                if (headerRows.isEmpty()) {
                    Text(
                        "No extra headers — tap + for Cloudflare Access or custom auth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                headerRows.forEachIndexed { index, (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { headerRows[index] = it to value },
                            label = { Text("Key") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !busy
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { headerRows[index] = key to it },
                            label = { Text("Value") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !busy
                        )
                        IconButton(
                            onClick = { headerRows.removeAt(index) },
                            enabled = !busy
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove header",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                if (state is AddFeedState.Error) {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val extras = headerRows
                        .filter { it.first.isNotBlank() }
                        .associate { it.first to it.second }
                    onAdd(url, extras)
                },
                enabled = url.isNotBlank() && !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

private fun buildSyncSummary(result: FeedSyncResult): String {
    val parts = buildList {
        if (result.added > 0) add("${result.added} added")
        if (result.skipped > 0) add("${result.skipped} already synced")
        if (result.failed > 0) add("${result.failed} failed")
    }
    return if (parts.isEmpty()) "Nothing to sync" else parts.joinToString(" · ")
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
