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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrowseWallpaper
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.MalAnimeEntry
import com.chrisalvis.rotato.data.LocalSource
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.MinResolution
import com.chrisalvis.rotato.data.SmartRule
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import android.content.Intent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onGoToDiscover: () -> Unit = {}, onSearchDiscover: (String) -> Unit = {}) {
    val vm: BrowseViewModel = viewModel()

    val lists by vm.lists.collectAsStateWithLifecycle()
    val unlockedListIds by vm.unlockedListIds.collectAsStateWithLifecycle()
    val lockedHiddenCount by vm.lockedHiddenCount.collectAsStateWithLifecycle()
    val listCounts by vm.listCounts.collectAsStateWithLifecycle()
    val listCovers by vm.listCovers.collectAsStateWithLifecycle()
    val listMosaics by vm.listMosaics.collectAsStateWithLifecycle()
    val scheduleEntries by vm.scheduleEntries.collectAsStateWithLifecycle()
    val selectedList by vm.selectedList.collectAsStateWithLifecycle()
    val wallpapers by vm.wallpapers.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val videoPreviewMode by vm.videoPreviewMode.collectAsStateWithLifecycle()
    val nsfwBlurEnabled by vm.nsfwBlurEnabled.collectAsStateWithLifecycle()
    val effectiveNsfwBlurEnabled = nsfwBlurEnabled && selectedList?.blurExempt != true
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val showCreateDialog by vm.showCreateDialog.collectAsStateWithLifecycle()
    val collectionSearch by vm.collectionSearch.collectAsStateWithLifecycle()
    val sortOrder by vm.sortOrder.collectAsStateWithLifecycle()
    val brokenEntryIds by vm.brokenEntryIds.collectAsStateWithLifecycle()
    val isCheckingLinks by vm.isCheckingLinks.collectAsStateWithLifecycle()
    val exportProgress by vm.exportProgress.collectAsStateWithLifecycle()
    val restoreProgress by vm.restoreProgress.collectAsStateWithLifecycle()
    val downloadAllProgress by vm.downloadAllProgress.collectAsStateWithLifecycle()
    val allKnownTags by vm.allKnownTags.collectAsStateWithLifecycle()
    val activeSources by vm.activeSources.collectAsStateWithLifecycle()
    val malAnimeEntries by vm.malAnimeEntries.collectAsStateWithLifecycle()
    val foldPairOuter by vm.foldPairOuter.collectAsStateWithLifecycle()
    val foldPairBusy by vm.foldPairBusy.collectAsStateWithLifecycle()
    val malLoggedIn by vm.malLoggedIn.collectAsStateWithLifecycle()
    val malRefreshing by vm.malRefreshing.collectAsStateWithLifecycle()
    val managedMalCollectionCount by vm.managedMalCollectionCount.collectAsStateWithLifecycle()
    val fetchFillLoading by vm.fetchFillLoading.collectAsStateWithLifecycle()
    val tagSuggestions by vm.tagSuggestions.collectAsStateWithLifecycle()
    val lastFillState by vm.lastFillState.collectAsStateWithLifecycle()
    var fetchFillFor by remember { mutableStateOf<LocalList?>(null) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep in-rotation badges in sync with actual filesystem state
    LaunchedEffect(Unit) { vm.refreshInRotation() }

    val inRotation by vm.inRotation.collectAsStateWithLifecycle()
    val rotationCount = remember(wallpapers, inRotation) { wallpapers.count { vm.isInRotation(it) } }

    var showMoveDialog by remember { mutableStateOf(false) }
    var showSaveRotationDialog by remember { mutableStateOf(false) }
    var showRemoveBrokenConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var createSmart by remember { mutableStateOf(false) }
    var showCreateMalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedList?.id) {
        vm.setCollectionSearch("")
        showSortMenu = false
        showCollectionMenu = false
    }
    LaunchedEffect(vm, context) {
        vm.duplicateWarning.collectLatest { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(vm, context) {
        vm.exportCompletion.collectLatest { count ->
            android.widget.Toast.makeText(context, "Exported $count images", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(restoreProgress) {
        restoreProgress?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(vm) {
        vm.fetchFillResult.collectLatest { message ->
            val undoable = vm.lastFillState.value != null
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (undoable) "Undo" else null,
                duration = if (undoable) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                vm.undoLastFill()
            }
        }
    }

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
            onCreateList = { createSmart = false; vm.showCreateDialog() },
        )
    }

    // Track which collection the picker was launched for
    var pickerTargetListId by remember { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val listId = pickerTargetListId
        if (!uris.isNullOrEmpty() && listId != null) vm.addLocalImages(listId, uris)
        pickerTargetListId = null
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.restoreFromBackup(context, uri)
    }

    BackHandler(enabled = selectionMode) { vm.exitSelectionMode() }
    BackHandler(enabled = !selectionMode && selectedList != null) { vm.clearSelection() }

    var showActionsFor by remember { mutableStateOf<BrowseWallpaper?>(null) }
    var previewWallpaper by remember { mutableStateOf<BrowseWallpaper?>(null) }
    var editRulesFor by remember { mutableStateOf<LocalList?>(null) }
    var editMalFor by remember { mutableStateOf<LocalList?>(null) }
    val gridState = rememberLazyGridState()
    val browseScope = rememberCoroutineScope()

    fun updateCover(wallpaper: BrowseWallpaper) {
        val entry = vm.wallpaperEntry(wallpaper.entryId) ?: return
        vm.setCoverImage(entry)
        android.widget.Toast.makeText(context, "Cover image updated", android.widget.Toast.LENGTH_SHORT).show()
    }

    val selectedWallpapers = remember(selected, wallpapers) {
        wallpapers.filter { it.entryId in selected }
    }
    val singleSelectedWallpaper = remember(selectedWallpapers) {
        selectedWallpapers.singleOrNull()
    }

    showActionsFor?.let { wp ->
        WallpaperDetailSheet(
            wallpaper = wp,
            isInRotation = vm.isInRotation(wp),
            isDeviceImage = wp.source == "device",
            isBroken = brokenEntryIds.contains(wp.entryId),
            onToggleRotation = { vm.toggleRotation(wp); showActionsFor = null },
            onSaveToGallery = { vm.saveWallpaper(wp); showActionsFor = null },
            onCopyUrl = {
                clipboard.setText(AnnotatedString(wp.shareLink.ifBlank { wp.fullUrl }))
                android.widget.Toast.makeText(context, "URL copied", android.widget.Toast.LENGTH_SHORT).show()
                showActionsFor = null
            },
            onShare = {
                vm.shareWallpapers(context, listOf(wp.toLocalWallpaperEntry(selectedList?.id.orEmpty())))
                showActionsFor = null
            },
            onSetAsCover = { updateCover(wp); showActionsFor = null },
            onRemoveFromCollection = { if (wp.entryId.isNotBlank()) { vm.removeWallpaper(wp.entryId); showActionsFor = null } },
            onDismiss = { showActionsFor = null }
        )
    }

    previewWallpaper?.let { initial ->
        WallpaperUrlPreviewDialog(
            wallpapers = wallpapers,
            initialWallpaper = initial,
            isInRotation = { vm.isInRotation(it) },
            onToggleRotation = { vm.toggleRotation(it) },
            onSaveToGallery = { vm.saveWallpaper(it) },
            onRemoveFromCollection = { wp ->
                if (wp.entryId.isNotBlank()) vm.removeWallpaper(wp.entryId)
            },
            onSetAsCover = { wp -> updateCover(wp) },
            onCopyUrl = { wp ->
                clipboard.setText(AnnotatedString(wp.shareLink.ifBlank { wp.fullUrl }))
                android.widget.Toast.makeText(context, "URL copied", android.widget.Toast.LENGTH_SHORT).show()
            },
            onShare = { wp ->
                vm.shareWallpapers(context, listOf(wp.toLocalWallpaperEntry(selectedList?.id.orEmpty())))
            },
            moveTargets = lists.filter { it.id != selectedList?.id && !it.isSmartCollection },
            onMoveTo = { wp, target -> vm.moveEntryToList(wp.entryId, target.id) },
            onSetWallpaper = { vm.setAsWallpaper(it) },
            canFoldPair = vm.canFoldPair,
            foldPairOuter = foldPairOuter,
            foldPairBusy = foldPairBusy,
            onFoldPair = { wp ->
                val outer = foldPairOuter
                when {
                    outer == null -> {
                        vm.pickFoldOuter(wp)
                        android.widget.Toast.makeText(context, "Cover screen picked. Now open the image for the inside screen and tap Pair.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    outer.fullUrl == wp.fullUrl -> vm.pickFoldOuter(null)
                    else -> vm.saveFoldPair(outer, wp)
                }
            },
            onTagSearch = { tag -> previewWallpaper = null; onSearchDiscover(tag) },
            onTagTier = { tag, tier -> vm.setTagTier(tag, tier, initial.isNsfw) },
            onDismiss = { lastViewed ->
                previewWallpaper = null
                val idx = wallpapers.indexOfFirst { it.entryId == lastViewed?.entryId }.takeIf { it >= 0 }
                if (idx != null) browseScope.launch { gridState.scrollToItem(idx) }
            }
        )
    }

    if (showCreateMenu) {
        NewCollectionSheet(
            malConnected = malLoggedIn,
            managedMalCount = managedMalCollectionCount,
            onPlain = { showCreateMenu = false; createSmart = false; vm.showCreateDialog() },
            onSmart = { showCreateMenu = false; createSmart = true; vm.showCreateDialog() },
            onAnime = { showCreateMenu = false; showCreateMalDialog = true },
            onSyncAnime = { showCreateMenu = false; vm.syncManagedMalCollections() },
            onDismiss = { showCreateMenu = false },
        )
    }

    if (showCreateDialog) {
        CreateListDialog(
            startSmart = createSmart,
            onConfirm = { name, rule -> vm.createList(name, rule) },
            onDismiss = { vm.dismissCreateDialog(); vm.clearTagSuggestions() },
            knownTags = allKnownTags,
            tagSuggestions = tagSuggestions,
            onFetchTagSuggestions = { vm.fetchTagSuggestions(it) },
            onClearTagSuggestions = { vm.clearTagSuggestions() },
        )
    }

    if (showCreateMalDialog) {
        LaunchedEffect(Unit) { vm.refreshMalListIfStale() }
        AnimeCollectionBuilder(
            entries = malAnimeEntries,
            loggedIn = malLoggedIn,
            refreshing = malRefreshing,
            activeSources = activeSources,
            nsfwBlurEnabled = nsfwBlurEnabled,
            onRefresh = { vm.refreshMalListIfStale(force = true) },
            onPreview = { tags, any -> vm.previewImages(tags, any) },
            onQuickStart = { shows, addToRotation ->
                vm.createCollectionsForShows(shows, addToRotation)
                showCreateMalDialog = false
            },
            onConfirm = { d ->
                vm.createMalCollection(
                    name = d.name,
                    animeTitle = d.animeTitle,
                    characterTags = d.characterTags,
                    pluginId = d.pluginId,
                    instanceId = d.instanceId,
                    fillCount = d.fillCount,
                    matchAny = d.matchAny,
                    autoAddToLibrary = d.autoAddToLibrary,
                    nsfwOverride = d.nsfwOverride,
                    minResolution = d.minResolution,
                    aspectRatio = d.aspectRatio,
                    useMalFilter = d.useMalFilter,
                    animeQuery = d.seriesTag,
                )
                showCreateMalDialog = false
            },
            onDismiss = { showCreateMalDialog = false }
        )
    }

    editRulesFor?.let { list ->
        EditRulesDialog(
            list = list,
            knownTags = allKnownTags,
            tagSuggestions = tagSuggestions,
            onFetchTagSuggestions = { vm.fetchTagSuggestions(it) },
            onClearTagSuggestions = { vm.clearTagSuggestions() },
            onConfirm = { rule -> vm.editSmartRule(list, rule); vm.clearTagSuggestions(); editRulesFor = null },
            onDismiss = { vm.clearTagSuggestions(); editRulesFor = null }
        )
    }

    editMalFor?.let { list ->
        AnimeCollectionBuilder(
            entries = malAnimeEntries,
            loggedIn = malLoggedIn,
            refreshing = malRefreshing,
            activeSources = activeSources,
            nsfwBlurEnabled = nsfwBlurEnabled,
            existingList = list,
            onRefresh = { vm.refreshMalListIfStale(force = true) },
            onPreview = { tags, any -> vm.previewImages(tags, any) },
            onQuickStart = { _, _ -> },
            onConfirm = { d ->
                vm.updateMalCollection(
                    list = list,
                    name = d.name,
                    animeTitle = d.animeTitle,
                    characterTags = d.characterTags,
                    pluginId = d.pluginId,
                    instanceId = d.instanceId,
                    fillCount = d.fillCount,
                    matchAny = d.matchAny,
                    autoAddToLibrary = d.autoAddToLibrary,
                    nsfwOverride = d.nsfwOverride,
                    minResolution = d.minResolution,
                    aspectRatio = d.aspectRatio,
                    useMalFilter = d.useMalFilter,
                    animeQuery = d.seriesTag,
                )
                editMalFor = null
            },
            onDismiss = { editMalFor = null }
        )
    }

    fetchFillFor?.let { list ->
        FetchFromSourcesDialog(
            list = list,
            activeSources = activeSources,
            tagSuggestions = tagSuggestions,
            onTagsChange = { vm.fetchTagSuggestions(it) },
            onClearTagSuggestions = { vm.clearTagSuggestions() },
            onConfirm = { tags, count, pluginId, instanceId, matchAny, nsfwOverride, minResolution, aspectRatio, useMalFilter ->
                vm.clearTagSuggestions()
                vm.fetchFill(list, tags, count, pluginId, instanceId, matchAny, nsfwOverride, minResolution, aspectRatio, useMalFilter)
                fetchFillFor = null
            },
            onDismiss = { vm.clearTagSuggestions(); fetchFillFor = null }
        )
    }

    exportProgress?.let { (current, total) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting collection") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Exporting $current/$total...")
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    if (selectionMode && selectedList != null) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Select all")
                        }
                    }
                    if (!selectionMode && selectedList != null) {
                        IconButton(onClick = { vm.enterSelectionMode() }) {
                            Icon(Icons.Default.CheckBox, contentDescription = "Select images")
                        }
                        Box {
                            IconButton(onClick = { showCollectionMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Collection actions")
                            }
                            DropdownMenu(
                                expanded = showCollectionMenu,
                                onDismissRequest = { showCollectionMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add from device") },
                                    onClick = {
                                        pickerTargetListId = selectedList!!.id
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        showCollectionMenu = false
                                    }
                                )
                                if (wallpapers.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Download all to Library") },
                                        onClick = {
                                            vm.downloadAllToRotation(selectedList!!.id)
                                            showCollectionMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export to gallery") },
                                        onClick = {
                                            vm.exportCollectionToGallery()
                                            showCollectionMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (!selectionMode && selectedList == null) {
                        IconButton(onClick = { showSaveRotationDialog = true }) {
                            Icon(Icons.Outlined.Wallpaper, contentDescription = "Save rotation as collection")
                        }
                        IconButton(onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) }) {
                            Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Import a shared collection or restore a backup")
                        }
                        Box {
                            IconButton(onClick = { showCreateMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Create collection")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode && selected.isNotEmpty() && selectedList != null) {
                BottomAppBar {
                    IconButton(
                        onClick = { showDeleteSelectedConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                            Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(
                        onClick = { showMoveDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
                            Text("Move", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(
                        onClick = { vm.downloadSelected() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Download, contentDescription = "Save to gallery")
                            Text("Save", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // Overflow for secondary actions
                    var showSelectionOverflow by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { showSelectionOverflow = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                Text("More", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        DropdownMenu(
                            expanded = showSelectionOverflow,
                            onDismissRequest = { showSelectionOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showSelectionOverflow = false
                                    vm.shareWallpapers(
                                        context,
                                        selectedWallpapers.map { it.toLocalWallpaperEntry(selectedList?.id.orEmpty()) }
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy URL") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showSelectionOverflow = false
                                    val urls = selectedWallpapers.map { it.shareLink }.filter { it.isNotBlank() }.joinToString("\n")
                                    if (urls.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(urls))
                                        android.widget.Toast.makeText(context, "URL copied", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            if (singleSelectedWallpaper != null) {
                                DropdownMenuItem(
                                    text = { Text("Set as cover") },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                    onClick = {
                                        showSelectionOverflow = false
                                        singleSelectedWallpaper?.let {
                                            updateCover(it)
                                            vm.exitSelectionMode()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Unfolded foldable / tablet: keep the collection list on the left while a collection
        // is open on the right, instead of swapping one full-width screen for the other.
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
        val twoPane = maxWidth >= 600.dp
        val listPaneWidth = (maxWidth * 0.4f).coerceIn(280.dp, 400.dp)
        Row(modifier = Modifier.fillMaxSize()) {
        if (selectedList == null || twoPane) {
            val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
            ListPickerContent(
                lists = lists,
                listCounts = listCounts,
                listCovers = listCovers,
                listMosaics = listMosaics,
                scheduleLinkCounts = remember(scheduleEntries) {
                    buildMap {
                        scheduleEntries.filter { it.enabled }.forEach { entry ->
                            entry.listIds.forEach { listId ->
                                if (listId.isNotBlank()) {
                                    put(listId, (get(listId) ?: 0) + 1)
                                }
                            }
                        }
                    }
                },
                lockedHiddenCount = lockedHiddenCount,
                unlockedListIds = unlockedListIds,
                onSelectList = { vm.selectList(it) },
                onDeleteList = { vm.deleteList(it) },
                onToggleRotation = { vm.toggleCollectionRotation(it) },
                onSetRotationTarget = { list, target -> vm.setRotationTarget(list, target) },
                onSetRotationInterval = { list, minutes -> vm.setRotationInterval(list, minutes) },
                onEditRules = { editRulesFor = it },
                onEditMal = { editMalFor = it },
                onAutofill = { vm.autofillSmartCollection(it) },
                onRefreshMal = { vm.refreshManagedMalCollection(it) },
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
                onLockAll = { vm.lockAll() },
                onMoveList = { list, delta -> vm.moveList(list, delta) },
                onMergeList = { from, into -> vm.mergeLists(from, into) },
                onPickImages = { list ->
                    pickerTargetListId = list.id
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onFetchFromSources = { fetchFillFor = it },
                onToggleBlurExempt = { vm.toggleBlurExempt(it) },
                onShareList = { vm.shareCollection(context, it) },
                onCreateList = { vm.showCreateDialog() },
                modifier = if (selectedList != null) Modifier.width(listPaneWidth) else Modifier.weight(1f)
            )
        }
        if (selectedList != null) {
            if (twoPane) VerticalDivider()
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = collectionSearch,
                    onValueChange = { vm.setCollectionSearch(it) },
                    placeholder = { Text("Filter by tag...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (collectionSearch.isNotEmpty()) {
                        { IconButton(onClick = { vm.setCollectionSearch("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") } }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.setCollectionSearch(collectionSearch) })
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box {
                        AssistChip(
                            onClick = { showSortMenu = true },
                            label = { Text("Sort: ${sortOrder.label()}") }
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            WallpaperSortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label()) },
                                    onClick = {
                                        vm.setSortOrder(order)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (isCheckingLinks) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                downloadAllProgress?.let { (current, total) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else current.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Downloading $current/$total to Library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        TextButton(onClick = { showRemoveBrokenConfirm = true }) {
                            Text("Remove all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (showDeleteSelectedConfirm) {
                    val count = selected.size
                    AlertDialog(
                        onDismissRequest = { showDeleteSelectedConfirm = false },
                        title = { Text("Delete $count item${if (count != 1) "s" else ""}?") },
                        text = { Text("This will permanently remove $count entr${if (count != 1) "ies" else "y"} from this collection. This cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.removeSelected()
                                showDeleteSelectedConfirm = false
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
                if (showRemoveBrokenConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRemoveBrokenConfirm = false },
                        title = { Text("Remove broken links?") },
                        text = {
                            Text(
                                "This will permanently delete ${brokenEntryIds.size} " +
                                "entr${if (brokenEntryIds.size != 1) "ies" else "y"} from this collection. " +
                                "This cannot be undone."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.removeBrokenEntries()
                                showRemoveBrokenConfirm = false
                            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveBrokenConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
                WallpaperGridContent(
                    wallpapers = wallpapers,
                    listId = selectedList?.id,
                    isCheckingLinks = isCheckingLinks,
                    isInRotation = { vm.isInRotation(it) },
                    downloading = downloading,
                    selectionMode = selectionMode,
                    selected = selected,
                    brokenEntryIds = brokenEntryIds,
                    videoPreviewMode = videoPreviewMode,
                    nsfwBlurEnabled = effectiveNsfwBlurEnabled,
                    gridState = gridState,
                    onTap = { wp ->
                        if (selectionMode) vm.toggleSelection(wp)
                        else previewWallpaper = wp
                    },
                    onLongPress = { wp -> vm.enterSelectionMode(wp) },
                    onDragSelect = { wp -> vm.dragSelect(wp) },
                    onPickFromDevice = selectedList?.let { list ->
                        {
                            pickerTargetListId = list.id
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                    onGoToDiscover = onGoToDiscover,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        }
        }
    }
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
    onCopyUrl: () -> Unit,
    onShare: () -> Unit,
    onSetAsCover: () -> Unit,
    onRemoveFromCollection: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (wallpaper.isVideo) {
                VideoPlayerView(
                    url = wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.medium),
                    allowTapToToggle = true,
                    showMuteButton = true,
                    showSeekBar = true,
                    allowDoubleTapSeek = true
                )
            } else {
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
            }
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
            if (!wallpaper.isVideo) {
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
            }
            if (!isDeviceImage) {
                ListItem(
                    headlineContent = { Text("Save to gallery") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onSaveToGallery)
                )
            }
            ListItem(
                headlineContent = { Text("Copy URL") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onCopyUrl)
            )
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onShare)
            )
            if (!wallpaper.isVideo) {
                ListItem(
                    headlineContent = { Text("Set as cover") },
                    leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = { onSetAsCover(); onDismiss() })
                )
            }
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
private fun CreateListDialog(
    startSmart: Boolean = false,
    onConfirm: (String, SmartRule?) -> Unit,
    onDismiss: () -> Unit,
    knownTags: List<String> = emptyList(),
    tagSuggestions: List<String> = emptyList(),
    onFetchTagSuggestions: (String) -> Unit = {},
    onClearTagSuggestions: () -> Unit = {},
) {
    var name by remember { mutableStateOf("") }
    var isSmart by remember { mutableStateOf(startSmart) }
    var requireAllText by remember { mutableStateOf("") }
    var requireAnyText by remember { mutableStateOf("") }
    var excludeAnyText by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { nameFocus.requestFocus() }
    }

    fun tagsFromText(text: String) = text.split(",").map { it.trim() }.filter { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSmart) "New smart collection" else "New collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) {
                            val rule = if (isSmart) SmartRule(
                                requireAll = tagsFromText(requireAllText),
                                requireAny = tagsFromText(requireAnyText),
                                excludeAny = tagsFromText(excludeAnyText),
                            ) else null
                            onConfirm(name, rule)
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Smart collection", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Auto-fills with matching wallpapers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isSmart, onCheckedChange = { isSmart = it })
                }
                if (isSmart) {
                    Text(
                        "Pick an idea or type tags. Every image you've saved with a match lands here, now and later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SMART_IDEAS.forEach { (label, tags) ->
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank() || SMART_IDEAS.any { it.first == name }) name = label
                                    requireAllText = ""
                                    requireAnyText = tags
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    TagRuleFields(
                        requireAllText = requireAllText,
                        requireAnyText = requireAnyText,
                        excludeAnyText = excludeAnyText,
                        onRequireAllChange = { requireAllText = it },
                        onRequireAnyChange = { requireAnyText = it },
                        onExcludeAnyChange = { excludeAnyText = it },
                        knownTags = knownTags,
                        tagSuggestions = tagSuggestions,
                        onFetchTagSuggestions = onFetchTagSuggestions,
                        onClearTagSuggestions = onClearTagSuggestions,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rule = if (isSmart) SmartRule(
                        requireAll = tagsFromText(requireAllText),
                        requireAny = tagsFromText(requireAnyText),
                        excludeAny = tagsFromText(excludeAnyText),
                    ) else null
                    onConfirm(name, rule)
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Starting points for smart collections: a name and tags any of which can match. */
private val SMART_IDEAS = listOf(
    "Scenery" to "scenery, landscape, sky",
    "Night skies" to "night_sky, starry_sky, moon",
    "Cityscapes" to "city, cityscape, city_lights",
    "Rainy days" to "rain, umbrella",
    "Flowers" to "flower, cherry_blossoms",
    "Ocean" to "ocean, beach, underwater",
    "Cats" to "cat, cat_ears",
    "Minimal" to "simple_background, minimalism",
)

@Composable
private fun TagRuleFields(
    requireAllText: String,
    requireAnyText: String,
    excludeAnyText: String,
    onRequireAllChange: (String) -> Unit,
    onRequireAnyChange: (String) -> Unit,
    onExcludeAnyChange: (String) -> Unit,
    knownTags: List<String>,
    tagSuggestions: List<String> = emptyList(),
    onFetchTagSuggestions: (String) -> Unit = {},
    onClearTagSuggestions: () -> Unit = {},
) {
    var showAdvanced by remember { mutableStateOf(requireAnyText.isNotBlank() || excludeAnyText.isNotBlank()) }

    @Composable
    fun TagField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, supporting: String? = null) {
        val lastToken = value.substringAfterLast(",").trimStart()
        // Merge local known tags with live API suggestions, deduplicating
        val localSuggestions = if (lastToken.length >= 2) {
            knownTags.filter { it.startsWith(lastToken.lowercase()) && it != lastToken.lowercase() }.take(5)
        } else emptyList()
        val liveSuggestions = tagSuggestions.filter { it !in localSuggestions }
        val suggestions = (localSuggestions + liveSuggestions).take(8)
        Column {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    onFetchTagSuggestions(it.substringAfterLast(",").trimStart())
                },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                supportingText = if (supporting != null) ({ Text(supporting) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            if (suggestions.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEach { tag ->
                        SuggestionChip(
                            onClick = {
                                val prefix = if (value.contains(",")) value.substringBeforeLast(",") + ", " else ""
                                onValueChange(prefix + tag + ", ")
                                onClearTagSuggestions()
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }

    TagField(requireAllText, onRequireAllChange, "Tags", "anime, landscape", "Comma-separated — wallpapers must have all of these")
    TextButton(
        onClick = { showAdvanced = !showAdvanced },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
    ) {
        Text(
            if (showAdvanced) "▲ Advanced" else "▼ Advanced",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (showAdvanced) {
        TagField(requireAnyText, onRequireAnyChange, "Match any tag (OR)", "wallpaper, scenery", "At least one must be present")
        TagField(excludeAnyText, onExcludeAnyChange, "Exclude tags", "nsfw, gore", "None of these can be present")
    }
}

@Composable
private fun EditRulesDialog(
    list: LocalList,
    knownTags: List<String>,
    tagSuggestions: List<String> = emptyList(),
    onFetchTagSuggestions: (String) -> Unit = {},
    onClearTagSuggestions: () -> Unit = {},
    onConfirm: (SmartRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = list.smartRule
    var requireAllText by remember { mutableStateOf(existing?.requireAll?.joinToString(", ") ?: "") }
    var requireAnyText by remember { mutableStateOf(existing?.requireAny?.joinToString(", ") ?: "") }
    var excludeAnyText by remember { mutableStateOf(existing?.excludeAny?.joinToString(", ") ?: "") }

    fun tagsFromText(text: String) = text.split(",").map { it.trim() }.filter { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Rules — ${list.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TagRuleFields(
                    requireAllText = requireAllText,
                    requireAnyText = requireAnyText,
                    excludeAnyText = excludeAnyText,
                    onRequireAllChange = { requireAllText = it },
                    onRequireAnyChange = { requireAnyText = it },
                    onExcludeAnyChange = { excludeAnyText = it },
                    knownTags = knownTags,
                    tagSuggestions = tagSuggestions,
                    onFetchTagSuggestions = onFetchTagSuggestions,
                    onClearTagSuggestions = onClearTagSuggestions,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = SmartRule(
                    requireAll = tagsFromText(requireAllText),
                    requireAny = tagsFromText(requireAnyText),
                    excludeAny = tagsFromText(excludeAnyText),
                )
                onConfirm(if (rule.isEmpty) null else rule)
            }) { Text("Save") }
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
    listMosaics: Map<String, List<String>> = emptyMap(),
    scheduleLinkCounts: Map<String, Int>,
    lockedHiddenCount: Int,
    unlockedListIds: Set<String>,
    onSelectList: (LocalList) -> Unit,
    onDeleteList: (LocalList) -> Unit,
    onToggleRotation: (LocalList) -> Unit,
    onSetRotationTarget: (LocalList, com.chrisalvis.rotato.data.ScreenRotationTarget) -> Unit,
    onSetRotationInterval: (LocalList, Int?) -> Unit,
    onEditRules: (LocalList) -> Unit,
    onEditMal: (LocalList) -> Unit,
    onAutofill: (LocalList) -> Unit,
    onRefreshMal: (LocalList) -> Unit,
    onLockCollection: (LocalList) -> Unit,
    onUnlockCollection: (LocalList) -> Unit,
    onRelockForSession: (LocalList) -> Unit,
    onShowHidden: () -> Unit,
    onLockAll: () -> Unit,
    onMoveList: (LocalList, Int) -> Unit,
    onMergeList: (from: LocalList, into: LocalList) -> Unit,
    onPickImages: (LocalList) -> Unit,
    onFetchFromSources: (LocalList) -> Unit,
    onToggleBlurExempt: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier,
    onShareList: (LocalList) -> Unit = {},
) {
    if (lists.isEmpty() && lockedHiddenCount == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Text("No collections yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "Tap ＋ above to create a collection",
                    "Save images from Discover into a collection",
                    "Sync a collection to your rotation with one tap"
                ).forEach { tip ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onCreateList) { Text("Create Collection") }
            }
        }
    } else {
        var listToDelete by remember { mutableStateOf<LocalList?>(null) }
        var mergeSource by remember { mutableStateOf<LocalList?>(null) }
        listToDelete?.let { list ->
            AlertDialog(
                onDismissRequest = { listToDelete = null },
                title = { Text("Delete \"${list.name}\"?") },
                text = { Text("All saved wallpapers in this collection will be removed.") },
                confirmButton = {
                    TextButton(onClick = { onDeleteList(list); listToDelete = null }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { listToDelete = null }) { Text("Cancel") } }
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // A full-width banner at the top instead of a thin line of text under the grid, so
            // locked collections are one obvious tap away.
            val sessionUnlocked = lists.count { it.isLocked && it.id in unlockedListIds }
            if (lockedHiddenCount > 0 || sessionUnlocked > 0) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "locked_banner") {
                    LockedCollectionsBanner(
                        hiddenCount = lockedHiddenCount,
                        unlockedCount = sessionUnlocked,
                        onUnlock = onShowHidden,
                        onLockAll = onLockAll,
                    )
                }
            }
            items(lists, key = { it.id }) { list ->
                val count = listCounts[list.id] ?: 0
                val coverUrl = listCovers[list.id]
                CollectionCard(
                    list = list,
                    count = count,
                    coverUrl = coverUrl,
                    mosaic = listMosaics[list.id].orEmpty(),
                    scheduleCount = scheduleLinkCounts[list.id] ?: 0,
                    isSessionUnlocked = list.isLocked && list.id in unlockedListIds,
                    onClick = { onSelectList(list) },
                    onDelete = { listToDelete = list },
                    onToggleRotation = { onToggleRotation(list) },
                    onSetRotationTarget = { onSetRotationTarget(list, it) },
                    onSetRotationInterval = { onSetRotationInterval(list, it) },
                    onEditRules = { onEditRules(list) },
                    onEditMal = { onEditMal(list) },
                    onAutofill = { onAutofill(list) },
                    onRefreshMal = { onRefreshMal(list) },
                    onLock = { onLockCollection(list) },
                    onUnlock = { onUnlockCollection(list) },
                    onRelockForSession = { onRelockForSession(list) },
                    onPickImages = { onPickImages(list) },
                    onFetchFromSources = { onFetchFromSources(list) },
                    onToggleBlurExempt = { onToggleBlurExempt(list) },
                    onMoveEarlier = { onMoveList(list, -1) },
                    onMoveLater = { onMoveList(list, 1) },
                    onMergeInto = { mergeSource = list },
                    onShare = { onShareList(list) },
                )
            }
        }
        mergeSource?.let { from ->
            val targets = lists.filter { it.id != from.id }
            AlertDialog(
                onDismissRequest = { mergeSource = null },
                title = { Text("Merge \"${from.name}\" into…") },
                text = {
                    if (targets.isEmpty()) Text("There's no other collection to merge into.")
                    else Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Its images move to the collection you pick (duplicates are skipped) and \"${from.name}\" is removed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        targets.forEach { into ->
                            TextButton(
                                onClick = { onMergeList(from, into); mergeSource = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(into.name, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { mergeSource = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun LockedCollectionsBanner(
    hiddenCount: Int,
    unlockedCount: Int,
    onUnlock: () -> Unit,
    onLockAll: () -> Unit,
) {
    Surface(
        onClick = if (hiddenCount > 0) onUnlock else onLockAll,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                if (hiddenCount > 0) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Text(
                if (hiddenCount > 0) "$hiddenCount locked collection${if (hiddenCount != 1) "s" else ""} hidden"
                else "$unlockedCount locked collection${if (unlockedCount != 1) "s" else ""} unlocked",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            if (hiddenCount > 0) {
                Button(onClick = onUnlock) { Text("Unlock") }
            } else {
                FilledTonalButton(onClick = onLockAll) { Text("Lock") }
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
    mosaic: List<String> = emptyList(),
    scheduleCount: Int,
    isSessionUnlocked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleRotation: () -> Unit,
    onSetRotationTarget: (com.chrisalvis.rotato.data.ScreenRotationTarget) -> Unit,
    onSetRotationInterval: (Int?) -> Unit,
    onEditRules: () -> Unit,
    onEditMal: () -> Unit,
    onAutofill: () -> Unit,
    onRefreshMal: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onRelockForSession: () -> Unit,
    onPickImages: () -> Unit,
    onFetchFromSources: () -> Unit,
    onMoveEarlier: () -> Unit = {},
    onMoveLater: () -> Unit = {},
    onMergeInto: () -> Unit = {},
    onShare: () -> Unit = {},
    onToggleBlurExempt: () -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    if (showIntervalDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Rotation interval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Override the global interval for \"${list.name}\" only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf<Int?>(null, 15, 30, 60, 120, 360, 720, 1440).forEach { minutes ->
                            FilterChip(
                                selected = list.rotationIntervalMinutes == minutes,
                                onClick = { onSetRotationInterval(minutes); showIntervalDialog = false },
                                label = {
                                    Text(when (minutes) {
                                        null -> "Global"
                                        in 1..59 -> "${minutes}m"
                                        else -> "${minutes / 60}h"
                                    })
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) { Text("Done") }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                if (mosaic.size == 4) {
                    // No cover picked: a 2×2 peek at the newest images.
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        mosaic.chunked(2).forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                } else if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "${list.name} cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (list.useAsRotation) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Library", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (scheduleCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f), MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Schedule ×$scheduleCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (list.isSmartCollection) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f), MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("⚡ Smart", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (list.isMalManaged) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f), MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("MAL", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (list.isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 6.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        list.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$count image${if (count != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val statusLine = buildList {
                        if (list.useAsRotation) add("Linked to Library")
                        if (scheduleCount > 0) add("In $scheduleCount schedule${if (scheduleCount != 1) "s" else ""}")
                    }.joinToString(" · ")
                    if (statusLine.isNotBlank()) {
                        Text(
                            statusLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onToggleRotation) {
                    Icon(
                        if (list.useAsRotation) Icons.Default.Wallpaper else Icons.Outlined.Wallpaper,
                        contentDescription = if (list.useAsRotation) "Remove from Library" else "Add to Library",
                        tint = if (list.useAsRotation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add from device") },
                            onClick = { onPickImages(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Fill from sources") },
                            onClick = { onFetchFromSources(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                        if (list.isSmartCollection) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Edit rules") },
                                onClick = { onEditRules(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Autofill (25)") },
                                onClick = { onAutofill(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null) }
                            )
                        }
                        if (list.isMalManaged) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Edit MAL settings") },
                                onClick = { onEditMal(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh from MAL") },
                                onClick = { onRefreshMal(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                        }
                        if (list.useAsRotation) {
                            HorizontalDivider()
                            com.chrisalvis.rotato.data.ScreenRotationTarget.entries.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.label) },
                                    onClick = { onSetRotationTarget(target); showMoreMenu = false },
                                    leadingIcon = {
                                        if (list.rotationTarget == target) Icon(Icons.Default.Check, contentDescription = null)
                                        else Spacer(Modifier.size(24.dp))
                                    }
                                )
                            }
                            val intervalLabel = list.rotationIntervalMinutes?.let { m ->
                                if (m < 60) "${m}m" else "${m / 60}h"
                            } ?: "Global"
                            DropdownMenuItem(
                                text = { Text("Interval: $intervalLabel") },
                                onClick = { showMoreMenu = false; showIntervalDialog = true },
                                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                            )
                        }
                        if (!LocalNsfwHidden.current) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (list.blurExempt) "Restore NSFW blur for this collection" else "Skip NSFW blur for this collection") },
                            onClick = { onToggleBlurExempt(); showMoreMenu = false },
                            leadingIcon = {
                                Icon(
                                    if (list.blurExempt) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        )
                        HorizontalDivider()
                        when {
                            !list.isLocked -> DropdownMenuItem(
                                text = { Text("Lock collection") },
                                onClick = { onLock(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                            )
                            isSessionUnlocked -> DropdownMenuItem(
                                text = { Text("Re-hide for session") },
                                onClick = { onRelockForSession(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                            )
                            else -> DropdownMenuItem(
                                text = { Text("Remove lock") },
                                onClick = { onUnlock(); showMoreMenu = false },
                                leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                            )
                        }
                        } // content filter
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Move earlier") },
                            onClick = { onMoveEarlier(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move later") },
                            onClick = { onMoveLater(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Merge into…") },
                            onClick = { onMergeInto(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.CallMerge, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share collection") },
                            onClick = { onShare(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun WallpaperGridContent(
    wallpapers: List<BrowseWallpaper>,
    listId: String?,
    isCheckingLinks: Boolean,
    isInRotation: (BrowseWallpaper) -> Boolean,
    downloading: Set<String>,
    selectionMode: Boolean,
    selected: Set<String>,
    brokenEntryIds: Set<String>,
    videoPreviewMode: com.chrisalvis.rotato.data.VideoPreviewMode,
    nsfwBlurEnabled: Boolean,
    gridState: LazyGridState = rememberLazyGridState(),
    onTap: (BrowseWallpaper) -> Unit,
    onLongPress: (BrowseWallpaper) -> Unit,
    onDragSelect: ((BrowseWallpaper) -> Unit)? = null,
    onPickFromDevice: (() -> Unit)? = null,
    onGoToDiscover: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (wallpapers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!isCheckingLinks && listId != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "This collection is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Save images from Discover to fill it up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onPickFromDevice != null) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = onPickFromDevice) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add from device")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    FilledTonalButton(onClick = onGoToDiscover) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Discover")
                    }
                }
            }
        }
        return
    }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(listId) { gridState.animateScrollToItem(0) }
    // Lazy layouts keep a small off-screen buffer mounted for smooth scrolling; video autoplay
    // should only kick in for items actually within the viewport, not the whole mounted buffer.
    val visibleKeys by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
    }

    // Drag-to-select: in selection mode, swipe across thumbnails to bulk-select them.
    // Uses Initial pass so we see events before the scrollable handler; we only consume
    // after the pointer has moved past slop, so taps still fall through normally.
    val dragSelectModifier = if (selectionMode && onDragSelect != null) {
        Modifier.pointerInput(selectionMode, wallpapers) {
            var lastDragIndex: Int? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var isDragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val delta = change.position - down.position
                    if (!isDragging && delta.getDistance() > viewConfiguration.touchSlop) {
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lastDragIndex = null
                    }
                    if (isDragging) {
                        change.consume()
                        val pos = change.position
                        val info = gridState.layoutInfo.visibleItemsInfo.find { item ->
                            pos.x >= item.offset.x && pos.x < item.offset.x + item.size.width &&
                            pos.y >= item.offset.y && pos.y < item.offset.y + item.size.height
                        }
                        if (info != null && info.index != lastDragIndex) {
                            lastDragIndex = info.index
                            wallpapers.getOrNull(info.index)?.let { onDragSelect(it) }
                        }
                    }
                }
            }
        }
    } else Modifier

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = modifier.fillMaxSize().then(dragSelectModifier),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(wallpapers, key = { it.entryId.ifBlank { "${it.sourceId}:${it.thumbUrl}" } }) { wp ->
            val key = wp.entryId.ifBlank { "${wp.sourceId}:${wp.thumbUrl}" }
            WallpaperThumbnail(
                wallpaper = wp,
                isInRotation = isInRotation(wp),
                isDownloading = downloading.contains(wp.sourceId),
                isSelected = selected.contains(wp.entryId),
                isBroken = brokenEntryIds.contains(wp.entryId),
                selectionMode = selectionMode,
                videoPreviewMode = videoPreviewMode,
                isVisible = key in visibleKeys,
                nsfwBlurEnabled = nsfwBlurEnabled,
                onTap = { onTap(wp) },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(wp)
                }
            )
        }
    }
}

private fun WallpaperSortOrder.label(): String = when (this) {
    WallpaperSortOrder.DATE_ADDED -> "Date added"
    WallpaperSortOrder.SOURCE -> "Source"
    WallpaperSortOrder.RESOLUTION -> "Resolution"
}

private fun BrowseWallpaper.toLocalWallpaperEntry(listId: String) = LocalWallpaperEntry(
    id = entryId,
    listId = listId,
    sourceId = sourceId,
    source = source,
    thumbUrl = thumbUrl,
    sampleUrl = sampleUrl,
    fullUrl = fullUrl,
    resolution = resolution,
    pageUrl = pageUrl,
    tags = tags,
    isVideo = isVideo,
    isNsfw = isNsfw
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperThumbnail(
    wallpaper: BrowseWallpaper,
    isInRotation: Boolean,
    isDownloading: Boolean,
    isSelected: Boolean,
    isBroken: Boolean,
    selectionMode: Boolean,
    videoPreviewMode: com.chrisalvis.rotato.data.VideoPreviewMode,
    isVisible: Boolean,
    nsfwBlurEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val revealKey = wallpaper.entryId.ifBlank { "${wallpaper.sourceId}:${wallpaper.thumbUrl}" }
    var revealed by rememberNsfwRevealed(revealKey)
    val isBlurred = wallpaper.isNsfw && nsfwBlurEnabled && !revealed
    val previewSlot = wallpaper.isVideo && !isBlurred &&
        rememberVideoPreviewSlot(enabled = isVisible && videoPreviewMode == com.chrisalvis.rotato.data.VideoPreviewMode.AUTOPLAY)
    // For video without a usable static thumbnail, model falls back to the video URL itself —
    // coil-video's VideoFrameDecoder (registered app-wide) decodes a still frame from it.
    val showThumb = !wallpaper.isVideo || videoPreviewMode != com.chrisalvis.rotato.data.VideoPreviewMode.OFF

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (isBlurred) revealed = true else onTap() },
                onLongClick = onLongPress
            )
    ) {
        if (previewSlot) {
            VideoPlayerView(
                url = wallpaper.fullUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else if (showThumb) {
            SubcomposeAsyncImage(
                model = wallpaper.thumbUrl.ifBlank { wallpaper.fullUrl },
                contentDescription = wallpaper.animeTitle.ifBlank { null },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().nsfwContentBlur(wallpaper.isNsfw, nsfwBlurEnabled, revealed),
                error = {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Wallpaper,
                            contentDescription = "Image unavailable",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }

        NsfwBlurLayer(wallpaper.isNsfw, nsfwBlurEnabled, revealed, compact = true)

        if (wallpaper.isVideo) {
            if (previewSlot) {
                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription = "Video (muted preview)",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp)
                )
            } else {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                )
            }
        }

        // Subtle scrim when selected (matches HomeScreen pattern)
        if (isSelected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        }

        // Corner selection indicator — consistent with HomeScreen
        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) Color.White else Color.Black.copy(alpha = 0.35f),
                        CircleShape
                    )
            )
        }

        when {
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
    val saveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { saveFocus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Rotation as Collection") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Collection name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
                modifier = Modifier.focusRequester(saveFocus)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperUrlPreviewDialog(
    wallpapers: List<BrowseWallpaper>,
    initialWallpaper: BrowseWallpaper,
    isInRotation: (BrowseWallpaper) -> Boolean,
    onToggleRotation: (BrowseWallpaper) -> Unit,
    onSaveToGallery: (BrowseWallpaper) -> Unit,
    onRemoveFromCollection: (BrowseWallpaper) -> Unit,
    onSetAsCover: (BrowseWallpaper) -> Unit,
    onCopyUrl: (BrowseWallpaper) -> Unit,
    onShare: (BrowseWallpaper) -> Unit,
    moveTargets: List<LocalList> = emptyList(),
    onMoveTo: (BrowseWallpaper, LocalList) -> Unit = { _, _ -> },
    onSetWallpaper: (BrowseWallpaper) -> Unit = {},
    canFoldPair: Boolean = false,
    foldPairOuter: BrowseWallpaper? = null,
    foldPairBusy: Boolean = false,
    onFoldPair: (BrowseWallpaper) -> Unit = {},
    onTagSearch: (String) -> Unit = {},
    onTagTier: (String, com.chrisalvis.rotato.data.TagTier) -> Unit = { _, _ -> },
    onDismiss: (currentWallpaper: BrowseWallpaper?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 150.dp.toPx() } }
    val initialPage = remember(wallpapers, initialWallpaper.entryId) {
        wallpapers.indexOfFirst { it.entryId == initialWallpaper.entryId }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { wallpapers.size }
    val currentWallpaper by remember(wallpapers, pagerState) {
        derivedStateOf { wallpapers.getOrNull(pagerState.currentPage) ?: wallpapers.getOrNull(initialPage) }
    }
    val zoom = remember { ZoomState() }
    val zoomed = zoom.zoomed
    var chromeVisible by remember { mutableStateOf(true) }
    val offsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var dockHinted by remember { mutableStateOf(false) }
    // Measured height of the bottom info/action panel below — the video's seek bar reads this
    // so it renders above the panel instead of sitting underneath its (touchable) rows.
    var bottomPanelHeight by remember { mutableStateOf(0.dp) }

    BackHandler(onBack = { onDismiss(currentWallpaper) })

    LaunchedEffect(pagerState.currentPage) { zoom.reset() }
    BackHandler(enabled = zoomed) { zoom.reset() }

    // When an item is removed, either close the dialog (list empty) or
    // keep the pager in bounds by scrolling back one page.
    LaunchedEffect(wallpapers.size) {
        when {
            wallpapers.isEmpty() -> onDismiss(null)
            pagerState.currentPage >= wallpapers.size ->
                pagerState.scrollToPage((wallpapers.size - 1).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = { onDismiss(currentWallpaper) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f)
                }
                .pointerInput(isDismissing, zoomed) {
                    if (isDismissing || zoomed) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var totalDy = 0f
                            var totalDx = 0f
                            var dragActive = false
                            // Detect drag direction
                            detect@ while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                // Two-finger pinch: leave it to the page's in-place zoom
                                if (event.changes.count { it.pressed } >= 2) break@detect
                                val delta = change.position - change.previousPosition
                                totalDy += delta.y
                                totalDx += delta.x
                                val absX = kotlin.math.abs(totalDx)
                                val absY = kotlin.math.abs(totalDy)
                                // Upward swipe opens the details sheet
                                if (-totalDy > viewConfiguration.touchSlop * 2 && absY > absX) {
                                    change.consume()
                                    showDetails = true
                                    break@detect
                                }
                                if (absY > viewConfiguration.touchSlop && absY > absX && totalDy > 0) {
                                    change.consume()
                                    dragActive = true
                                    coroutineScope.launch { offsetY.snapTo(totalDy.coerceAtLeast(0f)) }
                                    break@detect
                                }
                                if (absX > viewConfiguration.touchSlop) break@detect
                            }
                            if (dragActive) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    val dy = (change.position - change.previousPosition).y
                                    if (dy > 0f || offsetY.value > 0f) {
                                        change.consume()
                                        coroutineScope.launch {
                                            offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f))
                                        }
                                    }
                                }
                                coroutineScope.launch {
                                    if (offsetY.value > swipeThresholdPx) {
                                        isDismissing = true
                                        offsetY.animateTo(
                                            targetValue = 800f,
                                            animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing)
                                        )
                                        onDismiss(currentWallpaper)
                                    } else {
                                        offsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f)))
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.aboveTabletopFold().fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
            ) { page ->
                val wp = wallpapers.getOrNull(page) ?: return@HorizontalPager
                val imageUrl = wp.fullUrl.ifBlank { wp.sampleUrl.ifBlank { wp.thumbUrl } }
                // beyondViewportPageCount keeps neighbor pages mounted for smooth swiping — only the
                // page actually on screen should stream video.
                if (wp.isVideo && page == pagerState.currentPage) {
                    VideoPlayerView(
                        url = imageUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val shrink = 1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                                scaleX = shrink
                                scaleY = shrink
                            },
                        allowTapToToggle = true,
                        showMuteButton = true,
                        showSeekBar = true,
                        allowDoubleTapSeek = true,
                        seekBarBottomInset = bottomPanelHeight
                    )
                } else if (wp.isVideo) {
                    val posterUrl = wp.thumbUrl.ifBlank { wp.sampleUrl }.takeUnless { it.isBlank() || com.chrisalvis.rotato.data.MediaType.isVideoUrl(it) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val shrink = 1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                                scaleX = shrink
                                scaleY = shrink
                            }
                    ) {
                        if (posterUrl != null) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                    }
                } else {
                    FullscreenImage(
                        url = imageUrl,
                        placeholderKey = wp.thumbUrl.ifBlank { null },
                        contentDescription = wp.animeTitle.ifBlank { null },
                        modifier = Modifier.onSizeChanged { zoom.size = it },
                        imageModifier = Modifier
                            .zoomTransform(zoom, active = page == pagerState.currentPage) {
                                1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                            }
                            .zoomGestures(
                                zoom,
                                active = page == pagerState.currentPage,
                                onTap = { chromeVisible = !chromeVisible },
                            )
                    )
                }
            }

            // Same language as Discover: glass pills over the image at the top, a dock at the
            // bottom that pulls up into a sheet with everything else. One tap hides it all.
            currentWallpaper?.let { wp ->
                val inRotation = isInRotation(wp)
                val actions: @Composable (Modifier) -> Unit = { mod ->
                    Row(mod, horizontalArrangement = Arrangement.SpaceEvenly) {
                        // Same actions as Discover's viewer; videos play through the live wallpaper.
                        DockAction(Icons.Outlined.Wallpaper, "Set", onClick = { onSetWallpaper(wp) }, modifier = Modifier.weight(1f))
                        DockAction(
                            if (inRotation) Icons.Default.Check else Icons.Default.Download,
                            if (inRotation) "In Library" else "Library",
                            highlighted = inRotation,
                            enabled = !wp.isVideo,
                            onClick = { onToggleRotation(wp) },
                            modifier = Modifier.weight(1f)
                        )
                        if (canFoldPair && !wp.isVideo) {
                            val pickedHere = foldPairOuter?.let { it.fullUrl == wp.fullUrl } == true
                            DockAction(
                                Icons.Default.Smartphone,
                                when {
                                    foldPairBusy -> "Pairing…"
                                    pickedHere -> "Cover ✓"
                                    foldPairOuter != null -> "Pair"
                                    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400 -> "Pair"
                                    else -> "Fold pair"
                                },
                                highlighted = foldPairOuter != null,
                                enabled = !foldPairBusy,
                                onClick = { onFoldPair(wp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DockAction(Icons.Default.Share, "Share", onClick = { onShare(wp) }, modifier = Modifier.weight(1f))
                        if (wp.entryId.isNotBlank()) {
                            DockAction(
                                Icons.Default.Delete, "Remove",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRemoveFromCollection(wp)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = chromeVisible && !zoomed,
                    enter = androidx.compose.animation.fadeIn(tween(180)),
                    exit = androidx.compose.animation.fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showDetails = true }
                                ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) { CollectionInfoPills(wp, if (wallpapers.size > 1) "${pagerState.currentPage + 1} / ${wallpapers.size}" else null) }
                        IconButton(
                            onClick = { onDismiss(currentWallpaper) },
                            modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = chromeVisible && !zoomed,
                    enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.slideInVertically(tween(220)) { it / 3 },
                    exit = androidx.compose.animation.fadeOut(tween(180)) + androidx.compose.animation.slideOutVertically(tween(180)) { it / 3 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // navigationBarsPadding must come before the measurement below, or the reported
                            // height double-counts the nav-bar inset once here and again in VideoPlayerView's
                            // own navigationBarsPadding() when this height is passed through as seekBarBottomInset.
                            .navigationBarsPadding()
                            .onGloballyPositioned { bottomPanelHeight = with(density) { it.size.height.toDp() } }
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        ViewerDock(
                            title = wp.tags.firstOrNull()?.replace('_', ' ') ?: wp.animeTitle.ifBlank { wp.source.replaceFirstChar { it.uppercase() } },
                            subtitle = wp.tags.drop(1).let { rest ->
                                if (rest.isEmpty()) wp.animeTitle.takeIf { wp.tags.isNotEmpty() && it.isNotBlank() }
                                else rest.take(3).joinToString(" · ") { it.replace('_', ' ') } + if (rest.size > 3) " · +${rest.size - 3}" else ""
                            },
                            onExpand = { showDetails = true },
                            actions = actions,
                            hint = !dockHinted,
                            onHinted = { dockHinted = true },
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        )
                    }
                }

                if (showDetails) {
                    CollectionImageSheet(
                        wallpaper = wp,
                        actions = actions,
                        moveTargets = if (wp.entryId.isNotBlank()) moveTargets else emptyList(),
                        onMoveTo = { target -> showDetails = false; onMoveTo(wp, target) },
                        onCopyUrl = { onCopyUrl(wp) },
                        onSaveToGallery = { onSaveToGallery(wp) },
                        onSetAsCover = if (wp.isVideo) null else ({ showDetails = false; onSetAsCover(wp) }),
                        onTagSearch = { tag -> showDetails = false; onTagSearch(tag) },
                        onTagTier = onTagTier,
                        onSetWallpaper = { showDetails = false; onSetWallpaper(wp) },
                        onDismiss = { showDetails = false },
                    )
                }
            }

        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionInfoPills(wp: BrowseWallpaper, position: String?) {
    if (wp.source.isNotBlank()) InfoPill(if (wp.source == "device") "On device" else wp.source.replaceFirstChar { it.uppercase() }, bold = true)
    ImageFactPills(wp.resolution)
    if (wp.isVideo) InfoPill("Video", icon = Icons.Default.PlayArrow)
    if (wp.isNsfw && !LocalNsfwHidden.current) InfoPill("NSFW", container = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
    if (position != null) InfoPill(position)
}

/** The Collections viewer's dock at full height: facts, the same actions, moving, and tags. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CollectionImageSheet(
    wallpaper: BrowseWallpaper,
    actions: @Composable (Modifier) -> Unit,
    moveTargets: List<LocalList>,
    onMoveTo: (LocalList) -> Unit,
    onCopyUrl: () -> Unit,
    onSaveToGallery: () -> Unit,
    onSetAsCover: (() -> Unit)? = null,
    onTagSearch: (String) -> Unit = {},
    onTagTier: (String, com.chrisalvis.rotato.data.TagTier) -> Unit = { _, _ -> },
    onSetWallpaper: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var tagMenu by remember { mutableStateOf<String?>(null) }
    var showScreenPreview by remember { mutableStateOf(false) }
    if (showScreenPreview) {
        ScreenPreviewDialog(imageUrl = wallpaper.fullUrl, onSet = onSetWallpaper, onDismiss = { showScreenPreview = false })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    wallpaper.tags.firstOrNull()?.replace('_', ' ') ?: wallpaper.animeTitle.ifBlank { "This image" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CollectionInfoPills(wallpaper, null)
                }
            }

            actions(Modifier.fillMaxWidth())

            if (moveTargets.isNotEmpty()) {
                SheetSection("Move to") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        moveTargets.forEach { target ->
                            AssistChip(
                                onClick = { onMoveTo(target) },
                                label = { Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val page = wallpaper.pageUrl.takeIf { it.startsWith("http") }
                if (page != null) {
                    FilledTonalButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(page))) }
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open post")
                    }
                }
                FilledTonalButton(onClick = onSaveToGallery) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save to gallery")
                }
                FilledTonalButton(onClick = onCopyUrl) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy link")
                }
                if (onSetAsCover != null) {
                    FilledTonalButton(onClick = onSetAsCover) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use as cover")
                    }
                }
                if (!wallpaper.isVideo) {
                    FilledTonalButton(onClick = { showScreenPreview = true }) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview on my screens")
                    }
                }
            }

            if (wallpaper.tags.isNotEmpty()) {
                SheetSection("Tags · tap to find more, hold for options") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        wallpaper.tags.forEach { tag ->
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .combinedClickable(onClick = { onTagSearch(tag) }, onLongClick = { tagMenu = tag })
                                ) {
                                    Text(
                                        tag.replace('_', ' '),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                // Same tag actions as Discover: search, or tune your taste.
                                DropdownMenu(expanded = tagMenu == tag, onDismissRequest = { tagMenu = null }) {
                                    DropdownMenuItem(text = { Text("Find more in Discover") }, onClick = { tagMenu = null; onTagSearch(tag) })
                                    DropdownMenuItem(text = { Text("Show me more of this") }, onClick = { tagMenu = null; onTagTier(tag, com.chrisalvis.rotato.data.TagTier.LOVE) })
                                    DropdownMenuItem(text = { Text("Never show this") }, onClick = { tagMenu = null; onTagTier(tag, com.chrisalvis.rotato.data.TagTier.NEVER) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LocalSource.displayName(): String {
    val base = pluginId.lowercase().replaceFirstChar { it.uppercase() }
    return if (instanceId.isNotBlank()) "$base / $instanceId" else base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FetchFromSourcesDialog(
    list: LocalList,
    activeSources: List<LocalSource>,
    tagSuggestions: List<String> = emptyList(),
    onTagsChange: (String) -> Unit = {},
    onClearTagSuggestions: () -> Unit = {},
    onConfirm: (
        tags: String,
        count: Int,
        pluginId: String?,
        instanceId: String?,
        matchAny: Boolean,
        nsfwOverride: Boolean?,
        minResolution: MinResolution,
        aspectRatio: AspectRatio,
        useMalFilter: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var tags by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(25) }
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    var selectedInstanceId by remember { mutableStateOf<String?>(null) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var matchAny by remember { mutableStateOf(false) }
    // null = Auto (use global setting), true = Force ON, false = Force OFF
    var nsfwOverride by remember { mutableStateOf<Boolean?>(null) }
    var minResolution by remember { mutableStateOf(MinResolution.ANY) }
    var aspectRatio by remember { mutableStateOf(AspectRatio.ANY) }
    var useMalFilter by remember { mutableStateOf(false) }

    val selectedSource = activeSources.find { it.pluginId == selectedPluginId && it.instanceId == (selectedInstanceId ?: "") }
    val multiTag = tags.trim().contains(' ')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fill from Sources") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = {
                            tags = it
                            onTagsChange(it)
                        },
                        label = { Text("Tags") },
                        placeholder = { Text("e.g. hatsune_miku blue_hair") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    if (tagSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tagSuggestions.forEach { tag ->
                                SuggestionChip(
                                    onClick = {
                                        val prefix = tags.trimEnd().substringBeforeLast(' ').let {
                                            if (it.isBlank()) "" else "$it "
                                        }
                                        tags = "$prefix$tag "
                                        onClearTagSuggestions()
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
                // Tag match mode — only meaningful with multiple tags
                if (multiTag) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tag matching", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !matchAny, onClick = { matchAny = false }, label = { Text("All (AND)") })
                            FilterChip(selected = matchAny, onClick = { matchAny = true }, label = { Text("Any (OR)") })
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How many", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 25, 50, 100).forEach { n ->
                            FilterChip(
                                selected = count == n,
                                onClick = { count = n },
                                label = { Text("$n") }
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Min resolution", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(
                            MinResolution.ANY,
                            MinResolution.HD,
                            MinResolution.FHD,
                            MinResolution.QHD,
                            MinResolution.UHD,
                        ).forEach { res ->
                            FilterChip(
                                selected = minResolution == res,
                                onClick = { minResolution = res },
                                label = { Text(res.label.substringBefore(' ')) }
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aspect ratio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AspectRatio.entries.filter { it != AspectRatio.MY_PHONE }.forEach { ratio ->
                            FilterChip(
                                selected = aspectRatio == ratio,
                                onClick = { aspectRatio = ratio },
                                label = { Text(ratio.label.substringBefore(' ')) }
                            )
                        }
                    }
                }
                if (!LocalNsfwHidden.current) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NSFW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = nsfwOverride == null, onClick = { nsfwOverride = null }, label = { Text("Auto") })
                        FilterChip(selected = nsfwOverride == true, onClick = { nsfwOverride = true }, label = { Text("On") })
                        FilterChip(selected = nsfwOverride == false, onClick = { nsfwOverride = false }, label = { Text("Off") })
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useMalFilter = !useMalFilter }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = useMalFilter, onCheckedChange = { useMalFilter = it })
                    Text("MAL list only", style = MaterialTheme.typography.bodyMedium)
                }
                if (activeSources.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("From source", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(
                            expanded = sourceExpanded,
                            onExpandedChange = { sourceExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedSource?.displayName() ?: "All active sources",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = sourceExpanded,
                                onDismissRequest = { sourceExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All active sources") },
                                    onClick = { selectedPluginId = null; selectedInstanceId = null; sourceExpanded = false }
                                )
                                activeSources.forEach { src ->
                                    DropdownMenuItem(
                                        text = { Text(src.displayName()) },
                                        onClick = {
                                            selectedPluginId = src.pluginId
                                            selectedInstanceId = src.instanceId
                                            sourceExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        tags, count,
                        selectedPluginId,
                        selectedInstanceId.takeIf { it?.isNotBlank() == true },
                        matchAny,
                        nsfwOverride,
                        minResolution,
                        aspectRatio,
                        useMalFilter,
                    )
                },
                enabled = tags.isNotBlank()
            ) { Text("Fill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveWallpapersDialog(
    lists: List<LocalList>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateList: () -> Unit = {},
) {
    if (lists.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Move") },
            text = { Text("No other collections to move to.") },
            confirmButton = {
                TextButton(onClick = { onDismiss(); onCreateList() }) { Text("Create collection") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
