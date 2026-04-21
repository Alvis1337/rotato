package com.chrisalvis.rotato.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val listCovers by vm.listCovers.collectAsStateWithLifecycle()
    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val wallpapers by vm.wallpapers.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()

    // Keep in-rotation badges in sync with actual filesystem state
    LaunchedEffect(Unit) { vm.refreshInRotation() }

    // Track which collection the picker was launched for
    var pickerTargetListId by remember { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val listId = pickerTargetListId
        if (!uris.isNullOrEmpty() && listId != null) vm.addLocalImages(listId, uris)
        pickerTargetListId = null
    }

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
                    if (!selectionMode && selectedList != null) {
                        IconButton(onClick = {
                            pickerTargetListId = selectedList!!.id
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add from device")
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
                listCovers = listCovers,
                onSelectList = { vm.selectList(it) },
                onDeleteList = { vm.deleteList(it) },
                onToggleRotation = { vm.toggleCollectionRotation(it) },
                onPickImages = { list ->
                    pickerTargetListId = list.id
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListPickerContent(
    lists: List<LocalList>,
    listCounts: Map<String, Int>,
    listCovers: Map<String, String?>,
    onSelectList: (LocalList) -> Unit,
    onDeleteList: (LocalList) -> Unit,
    onToggleRotation: (LocalList) -> Unit,
    onPickImages: (LocalList) -> Unit,
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(lists, key = { it.id }) { list ->
                var showDeleteConfirm by remember { mutableStateOf(false) }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete \"${list.name}\"?") },
                        text = { Text("All saved wallpapers in this collection will be removed.") },
                        confirmButton = {
                            TextButton(onClick = { onDeleteList(list); showDeleteConfirm = false }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                    )
                }
                val count = listCounts[list.id] ?: 0
                val coverUrl = listCovers[list.id]
                CollectionCard(
                    list = list,
                    count = count,
                    coverUrl = coverUrl,
                    onClick = { onSelectList(list) },
                    onDelete = { showDeleteConfirm = true },
                    onToggleRotation = { onToggleRotation(list) },
                    onPickImages = { onPickImages(list) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCard(
    list: LocalList,
    count: Int,
    coverUrl: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleRotation: () -> Unit,
    onPickImages: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .combinedClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Bottom gradient with name + count
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        list.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$count image${if (count != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            // Rotation badge top-left
            if (list.useAsRotation) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("Library", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Action icons top-right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(onClick = onPickImages, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add photos",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    )
                }
                IconButton(onClick = onToggleRotation, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (list.useAsRotation) Icons.Default.Wallpaper else Icons.Outlined.Wallpaper,
                        contentDescription = "Toggle Library",
                        tint = if (list.useAsRotation) MaterialTheme.colorScheme.primaryContainer else Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    )
                }
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
            Text("No wallpapers yet — tap 📷 to add from your device", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    "Tap to add/remove from Library rotation. Use ⊞ to add all, or 📷 to import from device.",
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
