package com.chrisalvis.rotato.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
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
fun BrowseScreen() {
    val vm: BrowseViewModel = viewModel()

    val lists by vm.lists.collectAsStateWithLifecycle()
    val unlockedListIds by vm.unlockedListIds.collectAsStateWithLifecycle()
    val lockedHiddenCount by vm.lockedHiddenCount.collectAsStateWithLifecycle()
    val listCounts by vm.listCounts.collectAsStateWithLifecycle()
    val listCovers by vm.listCovers.collectAsStateWithLifecycle()
    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val wallpapers by vm.wallpapers.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val brokenEntryIds by vm.brokenEntryIds.collectAsStateWithLifecycle()
    val isCheckingLinks by vm.isCheckingLinks.collectAsStateWithLifecycle()

    // Keep in-rotation badges in sync with actual filesystem state
    LaunchedEffect(Unit) { vm.refreshInRotation() }

    val inRotation by vm.inRotation.collectAsStateWithLifecycle()
    val rotationCount = remember(wallpapers, inRotation) { wallpapers.count { vm.isInRotation(it) } }

    var showMoveDialog by remember { mutableStateOf(false) }
    var showSaveRotationDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(selectedList?.id) { showSearch = false }

    if (showSaveRotationDialog) {
        SaveRotationDialog(
            onConfirm = { name -> vm.importFromRotation(name); showSaveRotationDialog = false },
            onDismiss = { showSaveRotationDialog = false },
        )
    }

    if (showMoveDialog && selectedList != null) {
        MoveWallpapersDialog(
            lists = lists.filter { it.id != selectedList!!.id },
            onConfirm = { vm.moveSelectedToList(it); showMoveDialog = false },
            onDismiss = { showMoveDialog = false },
        )
    }

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

    var showActionsFor by remember { mutableStateOf<BrowseWallpaper?>(null) }

    showActionsFor?.let { wp ->
        WallpaperDetailSheet(
            wallpaper = wp,
            isInRotation = vm.isInRotation(wp),
            isDeviceImage = wp.source == "device",
            isBroken = brokenEntryIds.contains(wp.entryId),
            onToggleRotation = { vm.toggleRotation(wp); showActionsFor = null },
            onSaveToGallery = { vm.saveWallpaper(wp); showActionsFor = null },
            onRemoveFromCollection = { if (wp.entryId.isNotBlank()) { vm.removeWallpaper(wp.entryId); showActionsFor = null } },
            onDismiss = { showActionsFor = null }
        )
    }

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
                            if (selectionMode) vm.exitSelectionMode() else vm.clearSelection()
                        }) {
                            Icon(
                                if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (selectionMode) "Exit selection" else "Back to collections"
                            )
                        }
                    }
                },
                title = {
                    when {
                        selectionMode -> Text("${selected.size} selected", fontWeight = FontWeight.Bold)
                        selectedList != null -> Column {
                            Text(selectedList!!.name, fontWeight = FontWeight.Bold)
                            if (wallpapers.isNotEmpty()) {
                                Text(
                                    "${wallpapers.size} · $rotationCount in Library",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> Text("Collections", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (!selectionMode && selectedList != null) {
                        IconButton(onClick = {
                            if (showSearch) vm.setSearchQuery("")
                            showSearch = !showSearch
                        }) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearch) "Hide search" else "Search"
                            )
                        }
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
                        IconButton(onClick = { showSaveRotationDialog = true }) {
                            Icon(Icons.Outlined.Wallpaper, contentDescription = "Save rotation as collection")
                        }
                        IconButton(onClick = { vm.showCreateDialog() }) {
                            Icon(Icons.Default.Add, contentDescription = "New collection")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode && selected.isNotEmpty() && selectedList != null) {
                BottomAppBar {
                    Spacer(Modifier.weight(1f))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { vm.removeSelected() },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showMoveDialog = true },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
                        Text("Move", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { vm.downloadSelected() },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Save to gallery")
                        Text("Save", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        if (selectedList == null) {
            val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
            ListPickerContent(
                lists = lists,
                listCounts = listCounts,
                listCovers = listCovers,
                lockedHiddenCount = lockedHiddenCount,
                unlockedListIds = unlockedListIds,
                onSelectList = { vm.selectList(it) },
                onDeleteList = { vm.deleteList(it) },
                onToggleRotation = { vm.toggleCollectionRotation(it) },
                onLockCollection = { vm.lockCollection(it.id) },
                onUnlockCollection = { list ->
                    BiometricHelper.authenticate(
                        activity = activity,
                        title = "Unlock \"${list.name}\"",
                        onSuccess = { vm.unlockCollection(list.id) }
                    )
                },
                onRelockForSession = { vm.relockForSession(it.id) },
                onShowHidden = {
                    BiometricHelper.authenticate(
                        activity = activity,
                        title = "Show locked collections",
                        onSuccess = { vm.grantSessionAccess() }
                    )
                },
                onPickImages = { list ->
                    pickerTargetListId = list.id
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCreateList = { vm.showCreateDialog() },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(modifier = Modifier.padding(padding)) {
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        label = { Text("Search tags…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            { IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") } }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
                if (isCheckingLinks) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (brokenEntryIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "${brokenEntryIds.size} broken link${if (brokenEntryIds.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.removeBrokenEntries() }) {
                            Text("Remove all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                WallpaperGridContent(
                    wallpapers = wallpapers,
                    listId = selectedList?.id,
                    isInRotation = { vm.isInRotation(it) },
                    downloading = downloading,
                    selectionMode = selectionMode,
                    selected = selected,
                    brokenEntryIds = brokenEntryIds,
                    onTap = { wp ->
                        if (selectionMode) vm.toggleSelection(wp)
                        else showActionsFor = wp
                    },
                    onLongPress = { wp -> vm.enterSelectionMode(wp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun shareWallpaper(context: android.content.Context, wallpaper: BrowseWallpaper) {
    val text = wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share wallpaper"))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WallpaperDetailSheet(
    wallpaper: BrowseWallpaper,
    isInRotation: Boolean,
    isDeviceImage: Boolean,
    isBroken: Boolean,
    onToggleRotation: () -> Unit,
    onSaveToGallery: () -> Unit,
    onRemoveFromCollection: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl },
                contentDescription = wallpaper.animeTitle.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            if (wallpaper.animeTitle.isNotBlank()) {
                Text(
                    wallpaper.animeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (isBroken) {
                ListItem(
                    headlineContent = { Text("Broken link — image may not load", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(if (isInRotation) "Remove from Library" else "Add to Library") },
                leadingContent = {
                    Icon(
                        if (isInRotation) Icons.Outlined.Wallpaper else Icons.Default.Wallpaper,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable(onClick = onToggleRotation)
            )
            if (!isDeviceImage) {
                ListItem(
                    headlineContent = { Text("Save to gallery") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onSaveToGallery)
                )
            }
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable(onClick = { shareWallpaper(context, wallpaper); onDismiss() })
            )
            ListItem(
                headlineContent = { Text("Remove from collection", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onRemoveFromCollection)
            )
            Spacer(Modifier.height(24.dp))
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
    lockedHiddenCount: Int,
    unlockedListIds: Set<String>,
    onSelectList: (LocalList) -> Unit,
    onDeleteList: (LocalList) -> Unit,
    onToggleRotation: (LocalList) -> Unit,
    onLockCollection: (LocalList) -> Unit,
    onUnlockCollection: (LocalList) -> Unit,
    onRelockForSession: (LocalList) -> Unit,
    onShowHidden: () -> Unit,
    onPickImages: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lists.isEmpty() && lockedHiddenCount == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Text("No collections yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    isSessionUnlocked = list.isLocked && list.id in unlockedListIds,
                    onClick = { onSelectList(list) },
                    onDelete = { showDeleteConfirm = true },
                    onToggleRotation = { onToggleRotation(list) },
                    onLock = { onLockCollection(list) },
                    onUnlock = { onUnlockCollection(list) },
                    onRelockForSession = { onRelockForSession(list) },
                    onPickImages = { onPickImages(list) }
                )
            }
            if (lockedHiddenCount > 0) {
                item(span = { GridItemSpan(2) }) {
                    TextButton(
                        onClick = onShowHidden,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$lockedHiddenCount locked collection${if (lockedHiddenCount != 1) "s" else ""} — tap to unlock",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
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
    isSessionUnlocked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleRotation: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onRelockForSession: () -> Unit,
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

            // Rotation badge top-left; lock badge overlaid if locked
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
            if (list.isLocked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (list.useAsRotation) 40.dp else 8.dp)
                        .size(18.dp)
                )
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
                // Lock toggle: 3 states —
                //   not locked  → white Lock,     tap to permanently lock
                //   session-unlocked → white LockOpen, tap to re-hide for session
                //   permanently locked (shouldn't appear here) → blue LockOpen, tap to permanently unlock
                val lockIcon = when {
                    !list.isLocked -> Icons.Default.Lock
                    isSessionUnlocked -> Icons.Default.LockOpen
                    else -> Icons.Default.LockOpen
                }
                val lockTint = when {
                    !list.isLocked -> Color.White
                    isSessionUnlocked -> Color.White
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val lockAction = when {
                    !list.isLocked -> onLock
                    isSessionUnlocked -> onRelockForSession
                    else -> onUnlock
                }
                IconButton(
                    onClick = lockAction,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        lockIcon,
                        contentDescription = if (!list.isLocked) "Lock collection" else if (isSessionUnlocked) "Re-hide collection" else "Remove lock",
                        tint = lockTint,
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
    listId: String?,
    isInRotation: (BrowseWallpaper) -> Boolean,
    downloading: Set<String>,
    selectionMode: Boolean,
    selected: Set<String>,
    brokenEntryIds: Set<String>,
    onTap: (BrowseWallpaper) -> Unit,
    onLongPress: (BrowseWallpaper) -> Unit,
    modifier: Modifier = Modifier
) {
    if (wallpapers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No wallpapers yet — tap 📷 to add from your device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(listId) { gridState.animateScrollToItem(0) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(wallpapers, key = { it.entryId.ifBlank { it.sourceId } }) { wp ->
            WallpaperThumbnail(
                wallpaper = wp,
                isInRotation = isInRotation(wp),
                isDownloading = downloading.contains(wp.sourceId),
                isSelected = selected.contains(wp.sourceId),
                isBroken = brokenEntryIds.contains(wp.entryId),
                selectionMode = selectionMode,
                onTap = { onTap(wp) },
                onLongPress = { onLongPress(wp) }
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
    isBroken: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
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
            isDownloading -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
        }

        if (isInRotation && !selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "In Library",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isBroken) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Broken link",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
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

@Composable
private fun SaveRotationDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("Rotation ${java.time.LocalDate.now()}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Rotation as Collection") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Collection name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveWallpapersDialog(
    lists: List<LocalList>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (lists.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Move") },
            text = { Text("No other collections to move to.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Move to collection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            lists.forEach { list ->
                ListItem(
                    headlineContent = { Text(list.name) },
                    modifier = Modifier.clickable { onConfirm(list.id) }
                )
            }
        }
    }
}
