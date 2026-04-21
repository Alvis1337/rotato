package com.chrisalvis.rotato.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.BrowseWallpaper
import com.chrisalvis.rotato.data.LocalList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onNavigateBack: () -> Unit) {
    val vm: BrowseViewModel = viewModel()

    val lists by vm.lists.collectAsStateWithLifecycle()
    val listCounts by vm.listCounts.collectAsStateWithLifecycle()
    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val wallpapers by vm.wallpapers.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()

    // Keep in-rotation badges in sync with actual filesystem state
    LaunchedEffect(Unit) { vm.refreshInRotation() }

    BackHandler(enabled = selectionMode) { vm.exitSelectionMode() }
    BackHandler(enabled = !selectionMode && selectedList != null) { vm.clearSelection() }

    if (showCreateDialog) {
        CreateListDialog(
            onConfirm = { vm.createList(it) },
            onDismiss = { vm.dismissCreateDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectionMode || selectedList != null) {
                        IconButton(onClick = {
                            when {
                                selectionMode -> vm.exitSelectionMode()
                                selectedList != null -> vm.clearSelection()
                                else -> onNavigateBack()
                            }
                        }) {
                            Icon(
                                if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        when {
                            selectionMode -> "${selected.size} selected"
                            selectedList != null -> selectedList!!.name
                            else -> "Collections"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (selectionMode && selected.isNotEmpty()) {
                        IconButton(onClick = { vm.downloadSelected() }) {
                            Icon(Icons.Default.Download, contentDescription = "Download selected to gallery")
                        }
                    }
                    if (!selectionMode && selectedList != null && wallpapers.isNotEmpty()) {
                        IconButton(onClick = { vm.addAllToRotation() }) {
                            Icon(Icons.Default.Wallpaper, contentDescription = "Add all to Library")
                        }
                    }
                    if (!selectionMode && selectedList == null) {
                        IconButton(onClick = { vm.showCreateDialog() }) {
                            Icon(Icons.Default.Add, contentDescription = "New collection")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedList == null) {
            ListPickerContent(
                lists = lists,
                listCounts = listCounts,
                onSelectList = { vm.selectList(it) },
                onDeleteList = { vm.deleteList(it) },
                onToggleRotation = { vm.toggleCollectionRotation(it) },
                onCreateList = { vm.showCreateDialog() },
                modifier = Modifier.padding(padding)
            )
        } else {
            WallpaperGridContent(
                wallpapers = wallpapers,
                isInRotation = { vm.isInRotation(it) },
                downloading = downloading,
                selectionMode = selectionMode,
                selected = selected,
                onTap = { wp ->
                    if (selectionMode) vm.toggleSelection(wp)
                    else if (!downloading.contains(wp.sourceId))
                        vm.toggleRotation(wp)
                },
                onLongPress = { wp -> vm.enterSelectionMode(wp) },
                onRemove = { wp -> if (wp.entryId.isNotBlank()) vm.removeWallpaper(wp.entryId) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CreateListDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Collection") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ListPickerContent(
    lists: List<LocalList>,
    listCounts: Map<String, Int>,
    onSelectList: (LocalList) -> Unit,
    onDeleteList: (LocalList) -> Unit,
    onToggleRotation: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lists.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Text("No collections yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onCreateList) { Text("Create Collection") }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lists, key = { it.id }) { list ->
                var showDeleteConfirm by remember { mutableStateOf(false) }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete \"${list.name}\"?") },
                        text = { Text("All saved wallpapers in this collection will be removed.") },
                        confirmButton = {
                            TextButton(onClick = { onDeleteList(list); showDeleteConfirm = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                    )
                }
                val count = listCounts[list.id] ?: 0
                ListItem(
                    headlineContent = { Text(list.name, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        val label = buildString {
                            append("$count wallpaper${if (count != 1) "s" else ""}")
                            if (list.useAsRotation) append(" · synced to Library")
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (list.useAsRotation) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onToggleRotation(list) }) {
                                Icon(
                                    if (list.useAsRotation) Icons.Default.Wallpaper
                                    else Icons.Outlined.Wallpaper,
                                    contentDescription = if (list.useAsRotation) "Remove from Library source" else "Use as Library source",
                                    tint = if (list.useAsRotation) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectList(list) }
                )
            }
        }
    }
}

@Composable
private fun WallpaperGridContent(
    wallpapers: List<BrowseWallpaper>,
    isInRotation: (BrowseWallpaper) -> Boolean,
    downloading: Set<String>,
    selectionMode: Boolean,
    selected: Set<String>,
    onTap: (BrowseWallpaper) -> Unit,
    onLongPress: (BrowseWallpaper) -> Unit,
    onRemove: (BrowseWallpaper) -> Unit,
    modifier: Modifier = Modifier
) {
    if (wallpapers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No wallpapers saved yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(
                "Tap a wallpaper to add/remove it from the Library rotation. Use ⊞ to add all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        items(wallpapers, key = { it.entryId.ifBlank { it.sourceId } }) { wp ->
            WallpaperThumbnail(
                wallpaper = wp,
                isInRotation = isInRotation(wp),
                isDownloading = downloading.contains(wp.sourceId),
                isSelected = selected.contains(wp.sourceId),
                selectionMode = selectionMode,
                onTap = { onTap(wp) },
                onLongPress = { onLongPress(wp) },
                onRemove = { onRemove(wp) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperThumbnail(
    wallpaper: BrowseWallpaper,
    isInRotation: Boolean,
    isDownloading: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .then(if (isSelected) Modifier.border(3.dp, borderColor, MaterialTheme.shapes.medium) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        AsyncImage(
            model = wallpaper.thumbUrl.ifBlank { wallpaper.fullUrl },
            contentDescription = wallpaper.animeTitle.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        when {
            isSelected -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
            }
            isInRotation && !selectionMode -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
            }
            isDownloading -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        }

        if (!selectionMode && wallpaper.entryId.isNotBlank()) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            }
        }

        if (wallpaper.animeTitle.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = wallpaper.animeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
