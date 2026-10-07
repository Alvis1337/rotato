package com.chrisalvis.rotato.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.RotatoSettings
import com.chrisalvis.rotato.data.RotationError
import com.chrisalvis.rotato.data.RotationErrorType
import com.dragselectcompose.core.DragSelectState
import com.dragselectcompose.core.gridDragSelect
import com.dragselectcompose.core.rememberDragSelectState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.platform.LocalDensity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBrowseFeed: () -> Unit = {}
) {
    val allImages by viewModel.images.collectAsStateWithLifecycle()
    // With the content filter on, NSFW images in the library aren't shown.
    val nsfwHidden = LocalNsfwHidden.current
    val nsfwNames by viewModel.nsfwFileNames.collectAsStateWithLifecycle()
    val images = remember(allImages, nsfwHidden, nsfwNames) {
        if (nsfwHidden) allImages.filter { it.name !in nsfwNames } else allImages
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val setNowState by viewModel.setNowState.collectAsStateWithLifecycle()
    val lastRotationMs by viewModel.lastRotationMs.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val linkedCollections = remember(collections) { collections.filter { it.useAsRotation } }

    val dragSelectState = rememberDragSelectState<File>()
    val inSelectionMode = dragSelectState.inSelectionMode

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pairedPaths by viewModel.pairedPaths.collectAsStateWithLifecycle()
    var foldPairCandidates by remember { mutableStateOf<Pair<File, File>?>(null) }

    foldPairCandidates?.let { (first, second) ->
        FoldPairDialog(
            first = first,
            second = second,
            onDismiss = { foldPairCandidates = null },
            onConfirm = { outer, inner ->
                viewModel.saveFoldPair(outer, inner)
                foldPairCandidates = null
            }
        )
    }

    BackHandler(enabled = inSelectionMode) { dragSelectState.disableSelectionMode() }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    // Refresh image pool every time this screen enters composition so wallpapers
    // downloaded from Collections show up immediately without restarting the app.
    LaunchedEffect(Unit) { viewModel.refreshFromFeeds() }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { dragSelectState.disableSelectionMode() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    },
                    title = { Text("${dragSelectState.selected.size} selected") },
                    actions = {
                        val selection = dragSelectState.selected.toList()
                        if (viewModel.foldPairsSupported && selection.size == 2) {
                            TextButton(onClick = {
                                foldPairCandidates = selection[0] to selection[1]
                                dragSelectState.disableSelectionMode()
                            }) { Text("Fold pair") }
                        }
                        if (selection.any { it.absolutePath in pairedPaths }) {
                            TextButton(onClick = {
                                viewModel.unpair(selection.toSet())
                                dragSelectState.disableSelectionMode()
                            }) { Text("Unpair") }
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteSelected(dragSelectState.selected.toSet())
                                dragSelectState.disableSelectionMode()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                    text = { Text("Library") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    text = { Text("History") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    text = { Text("Stats") }
                )
            }

            when (selectedTab) {
                0 -> LibraryContent(
                    viewModel = viewModel,
                    images = images,
                    settings = settings,
                    isLoading = isLoading,
                    setNowState = setNowState,
                    lastRotationMs = lastRotationMs,
                    linkedCollections = linkedCollections,
                    collections = collections,
                    dragSelectState = dragSelectState,
                    inSelectionMode = inSelectionMode,
                    onPhotoPick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        },
                        onGoToDiscover = onBrowseFeed
                )
                1 -> HistoryScreen(modifier = Modifier.fillMaxSize())
                2 -> StatsContent(stats = stats, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun LibraryContent(
    viewModel: HomeViewModel,
    images: List<File>,
    settings: RotatoSettings,
    isLoading: Boolean,
    setNowState: SetNowState,
    lastRotationMs: Long,
    linkedCollections: List<LocalList>,
    collections: List<LocalList>,
    dragSelectState: DragSelectState<File>,
    inSelectionMode: Boolean,
    onPhotoPick: () -> Unit,
    onGoToDiscover: () -> Unit = {},
) {
    val saveToListInProgress by viewModel.saveToListInProgress.collectAsStateWithLifecycle()
    val rotationErrors by viewModel.rotationErrors.collectAsStateWithLifecycle()
    val wallpaperRatings by viewModel.wallpaperRatings.collectAsStateWithLifecycle()
    val nsfwFileNames by viewModel.nsfwFileNames.collectAsStateWithLifecycle()
    val nsfwBlurEnabled by viewModel.nsfwBlurEnabled.collectAsStateWithLifecycle()
    val lastSkipReason by viewModel.lastSkipReason.collectAsStateWithLifecycle()
    val setNowErrorMessage by viewModel.setNowErrorMessage.collectAsStateWithLifecycle()
    val hasPreviousWallpaper by viewModel.hasPreviousWallpaper.collectAsStateWithLifecycle()
    val pairedPaths by viewModel.pairedPaths.collectAsStateWithLifecycle()
    var showSaveToListDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // Colour filter: each image's colours are worked out once (cached) the first time it's used.
    var colourFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedColour = colourFilter?.let { n -> com.chrisalvis.rotato.data.ImageColour.entries.find { it.name == n } }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var looksLoading by remember { mutableStateOf(false) }
    // Match a photo: pick any picture and the Library shows what goes with it, best match first.
    var matchLook by remember { mutableStateOf<com.chrisalvis.rotato.data.ImageLook?>(null) }
    var matchLoading by remember { mutableStateOf(false) }
    // Rainbow order: lay the Library out by each image's main colour, like a bookshelf sorted by spine.
    var rainbow by rememberSaveable { mutableStateOf(false) }
    val matchScope = rememberCoroutineScope()
    val matchPicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        matchLoading = true
        matchScope.launch {
            val look = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.chrisalvis.rotato.data.ImageAnalysis.lookForUri(appContext, uri)
            }
            matchLoading = false
            if (look == null || look.colours.isEmpty()) {
                android.widget.Toast.makeText(appContext, "Couldn't read that photo's colours", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                colourFilter = null
                matchLook = look
            }
        }
    }
    val looks by produceState(emptyMap<String, com.chrisalvis.rotato.data.ImageLook>(), images, selectedColour != null || matchLook != null || rainbow) {
        if (selectedColour == null && matchLook == null && !rainbow) return@produceState
        looksLoading = true
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.chrisalvis.rotato.data.ImageAnalysis.looksFor(appContext, images)
        }
        looksLoading = false
    }
    val gridImages = remember(images, selectedColour, looks, matchLook, rainbow) {
        val target = matchLook
        val filtered = when {
            target != null -> images
                .mapNotNull { f -> looks[f.name]?.let { com.chrisalvis.rotato.data.ImageAnalysis.matchScore(target, it) }?.let { f to it } }
                .sortedByDescending { it.second }
                .map { it.first }
            selectedColour == null -> images
            else -> images.filter { looks[it.name]?.colours?.contains(selectedColour) == true }
        }
        if (rainbow && target == null) com.chrisalvis.rotato.data.ImageAnalysis.rainbowOrder(filtered, looks) else filtered
    }

    if (showSaveToListDialog) {
        SaveToCollectionDialog(
            collections = collections,
            onDismiss = { showSaveToListDialog = false },
            onSelect = { listId ->
                showSaveToListDialog = false
                viewModel.saveRotationToList(listId)
            }
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        RotationStatusCard(
            isEnabled = settings.isEnabled,
            imageCount = images.size,
            intervalMinutes = settings.intervalMinutes,
            lastRotationMs = lastRotationMs,
            linkedCollections = linkedCollections,
            onToggle = { viewModel.setRotationEnabled(!settings.isEnabled) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (rotationErrors.isNotEmpty()) {
            RotationErrorPane(
                errors = rotationErrors,
                onClearAll = { viewModel.clearRotationErrors() },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
            )
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (lastSkipReason != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        lastSkipReason!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        val duplicateScan by viewModel.duplicateScan.collectAsStateWithLifecycle()
        DuplicatesDialog(
            scan = duplicateScan,
            onDismiss = { viewModel.dismissDuplicates() },
            onRemove = { viewModel.removeDuplicates(it) },
        )

        var selectedFile by remember { mutableStateOf<File?>(null) }
        var ratingDialogFile by remember { mutableStateOf<File?>(null) }
        val pullRefreshState = rememberPullToRefreshState()

        ratingDialogFile?.let { file ->
            WallpaperRatingDialog(
                filename = file.name,
                currentRating = wallpaperRatings[file.name] ?: 0,
                onDismiss = { ratingDialogFile = null },
                onSelect = { rating ->
                    viewModel.setRating(file, rating)
                    ratingDialogFile = null
                }
            )
        }

        selectedFile?.takeIf { images.isNotEmpty() }?.let { file ->
            ImagePreviewDialog(
                images = images,
                initialFile = file,
                onDismiss = { selectedFile = null },
                onSetWallpaper = { currentFile ->
                    viewModel.setSpecificWallpaper(currentFile)
                    selectedFile = null
                },
                onRemove = { currentFile ->
                    viewModel.removeImage(currentFile)
                    if (images.size == 1) selectedFile = null
                },
                onSaveToGallery = { currentFile -> viewModel.saveFileToGallery(currentFile) },
                wallpaperRatings = wallpaperRatings,
                onSetRating = { currentFile, rating -> viewModel.setRating(currentFile, rating) }
            )
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = viewModel::refreshImages,
            state = pullRefreshState,
            modifier = Modifier.weight(1f)
        ) {
            if (images.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onGoToDiscover = onGoToDiscover,
                    onAddPhotos = onPhotoPick
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                if (!inSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp)
                    ) {
                        Text(
                            text = "Long press an image to select",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (images.size > 1) {
                            TextButton(onClick = { viewModel.findDuplicates() }) {
                                Text("Find duplicates", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (images.size > 1) {
                        ColourFilterRow(
                            selected = selectedColour,
                            loading = looksLoading || matchLoading,
                            matchCount = if ((selectedColour != null || matchLook != null) && !looksLoading) gridImages.size else null,
                            onSelect = { c -> matchLook = null; colourFilter = if (c == selectedColour) null else c?.name },
                            matchingPhoto = matchLook != null,
                            onMatchPhoto = {
                                matchPicker.launch(PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onClearMatch = { matchLook = null },
                            rainbow = rainbow,
                            onRainbow = { rainbow = !rainbow; if (rainbow) matchLook = null },
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    state = dragSelectState.gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .gridDragSelect(items = gridImages, state = dragSelectState),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(gridImages, key = { it.absolutePath }) { file ->
                        val isSelected by remember { derivedStateOf { dragSelectState.isSelected(file) } }
                        val thumbnailContent: @Composable () -> Unit = {
                            ImageThumbnail(
                                file = file,
                                rating = wallpaperRatings[file.name] ?: 0,
                                isSelected = isSelected,
                                inSelectionMode = inSelectionMode,
                                isNsfw = file.name in nsfwFileNames,
                                nsfwBlurEnabled = nsfwBlurEnabled,
                                isPaired = file.absolutePath in pairedPaths,
                                onClick = { if (!inSelectionMode) selectedFile = file },
                            )
                        }

                        thumbnailContent()
                    }
                }
                } // Column
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setPreviousWallpaper()
                },
                enabled = hasPreviousWallpaper && setNowState == SetNowState.IDLE,
                contentPadding = PaddingValues(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Previous wallpaper", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back")
            }
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setWallpaperNow()
                },
                enabled = images.isNotEmpty() && setNowState == SetNowState.IDLE,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp)
            ) {
                when (setNowState) {
                    SetNowState.SETTING -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Setting...")
                    }
                    SetNowState.DONE -> {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Done!")
                    }
                    SetNowState.ERROR -> {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(setNowErrorMessage ?: "Failed — retry?")
                    }
                    SetNowState.IDLE -> {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Set Now")
                    }
                }
            }
            Button(
                onClick = onPhotoPick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Photos")
            }
        }
        if (images.isNotEmpty()) {
            OutlinedButton(
                onClick = { showSaveToListDialog = true },
                enabled = !saveToListInProgress && collections.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                if (saveToListInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save to collection…")
                }
            }
        }
    }
}

@Composable
private fun SaveToCollectionDialog(
    collections: List<LocalList>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 560.dp)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
                Text(
                    text = "Save library to collection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Images already in the collection will be skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = androidx.compose.ui.Modifier.padding(bottom = 12.dp)
                )
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false)
                ) {
                    collections.forEach { list ->
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(list.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(20.dp))
                            Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun RotationStatusCard(
    isEnabled: Boolean,
    imageCount: Int,
    intervalMinutes: Int,
    lastRotationMs: Long,
    linkedCollections: List<LocalList>,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isEnabled)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val nextRotationMs = if (isEnabled && lastRotationMs > 0L) lastRotationMs + intervalMinutes * 60_000L else 0L
    var countdownText by remember(nextRotationMs) { mutableStateOf("") }
    LaunchedEffect(nextRotationMs) {
        if (nextRotationMs <= 0L) return@LaunchedEffect
        while (true) {
            val remaining = nextRotationMs - System.currentTimeMillis()
            countdownText = if (remaining <= 0L) "any moment" else {
                val totalSec = remaining / 1000L
                if (totalSec < 60) "${totalSec}s" else "${totalSec / 60}m ${totalSec % 60}s"
            }
            delay(1_000L)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isEnabled) "Rotating" else "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        imageCount == 0 -> "No photos added"
                        isEnabled -> "$imageCount photo${if (imageCount == 1) "" else "s"} · every ${formatInterval(intervalMinutes)}"
                        else -> "$imageCount photo${if (imageCount == 1) "" else "s"} ready"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastRotationMs > 0L) {
                    Text(
                        text = "Last set: ${formatAgo(lastRotationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (isEnabled && countdownText.isNotEmpty()) {
                    Text(
                        text = "Next: $countdownText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (linkedCollections.isNotEmpty()) {
                    val namesPreview = linkedCollections.take(3).joinToString(", ") { "\"${it.name}\"" }
                    val moreCount = (linkedCollections.size - 3).coerceAtLeast(0)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = if (linkedCollections.size == 1) {
                                "Linked to $namesPreview"
                            } else {
                                "${linkedCollections.size} linked collections"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = buildString {
                            append(namesPreview)
                            if (moreCount > 0) append(" +$moreCount more")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                enabled = imageCount > 0
            )
        }
    }
}

@Composable
private fun ImageThumbnail(
    file: File,
    rating: Int = 0,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    isNsfw: Boolean = false,
    nsfwBlurEnabled: Boolean = true,
    isPaired: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val badgeInfo = remember(file.absolutePath, file.length(), file.lastModified()) {
        readImageBadgeInfo(file)
    }
    var revealed by rememberNsfwRevealed(file.absolutePath)
    val isBlurred = isNsfw && nsfwBlurEnabled && !revealed
    val foldCanvas = rememberFoldCanvas()
    val foldFriendly = foldCanvas != null && badgeInfo != null &&
        com.chrisalvis.rotato.data.isFoldFriendly(badgeInfo.width, badgeInfo.height, foldCanvas)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(
                if (!inSelectionMode) Modifier.combinedClickable(
                    onClick = { if (isBlurred) revealed = true else onClick() },
                    onLongClick = onLongClick
                ) else Modifier
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .crossfade(300)
                .build(),
            contentDescription = if (isSelected) "Wallpaper (selected)" else "Wallpaper",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().nsfwContentBlur(isNsfw, nsfwBlurEnabled, revealed)
        )

        NsfwBlurLayer(isNsfw, nsfwBlurEnabled, revealed, compact = true)

        if (foldFriendly) {
            FoldBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        }

        if (isPaired) {
            Icon(
                Icons.Default.Link,
                contentDescription = "Fold pair",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    .padding(3.dp)
                    .size(14.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
        }

        badgeInfo?.let { info ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${info.width}×${info.height} · ${info.fileSizeText}",
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        if (rating > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = rating.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        if (inSelectionMode) {
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
    }
}

@Composable
private fun WallpaperRatingDialog(
    filename: String,
    currentRating: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate wallpaper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { rating ->
                        IconButton(onClick = { onSelect(rating) }) {
                            Icon(
                                imageVector = if (rating <= currentRating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$rating stars",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(0) }) { Text("Clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class ImageBadgeInfo(
    val width: Int,
    val height: Int,
    val fileSizeText: String
)

private fun readImageBadgeInfo(file: File): ImageBadgeInfo? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return ImageBadgeInfo(
        width = options.outWidth,
        height = options.outHeight,
        fileSizeText = formatFileSize(file.length())
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return String.format(Locale.US, "%.1f KB", kib)
    val mib = kib / 1024.0
    if (mib < 1024) return String.format(Locale.US, "%.1f MB", mib)
    val gib = mib / 1024.0
    return String.format(Locale.US, "%.1f GB", gib)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePreviewDialog(
    images: List<File>,
    initialFile: File,
    onDismiss: () -> Unit,
    onSetWallpaper: (File) -> Unit,
    onRemove: (File) -> Unit,
    onSaveToGallery: (File) -> Unit = {},
    wallpaperRatings: Map<String, Int> = emptyMap(),
    onSetRating: (File, Int) -> Unit = { _, _ -> }
) {
    BackHandler(onBack = onDismiss)
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 150.dp.toPx() } }
    val initialPage = remember(images, initialFile.absolutePath) {
        images.indexOfFirst { it.absolutePath == initialFile.absolutePath }
            .takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { images.size }
    val currentFile by remember(images, pagerState) {
        derivedStateOf { images.getOrNull(pagerState.currentPage) }
    }
    var showZoom by remember { mutableStateOf(false) }
    val offsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(images.size, pagerState.currentPage) {
        if (images.isEmpty()) {
            onDismiss()
            return@LaunchedEffect
        }
        if (pagerState.currentPage > images.lastIndex) {
            pagerState.scrollToPage(images.lastIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) { showZoom = false }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f)
                }
                .pointerInput(isDismissing, showZoom) {
                    if (isDismissing || showZoom) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var totalDy = 0f
                            var totalDx = 0f
                            var dragActive = false
                            detect@ while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                if (event.changes.count { it.pressed } >= 2) {
                                    coroutineScope.launch { showZoom = true }
                                    break@detect
                                }
                                val delta = change.position - change.previousPosition
                                totalDy += delta.y
                                totalDx += delta.x
                                val absX = kotlin.math.abs(totalDx)
                                val absY = kotlin.math.abs(totalDy)
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
                                        onDismiss()
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
                beyondViewportPageCount = 1
            ) { page ->
                val pageFile = images.getOrNull(page) ?: return@HorizontalPager
                AsyncImage(
                    model = pageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { showZoom = true })
                        }
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Page counter
            if (images.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            // Bottom gradient panel
            currentFile?.let { file ->
                val fileSize = remember(file) { "%.1f MB".format(file.length() / 1_048_576.0) }
                val imageDimens = remember(file) {
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        if (opts.outWidth > 0) "${opts.outWidth}×${opts.outHeight}" else null
                    } catch (_: Exception) { null }
                }
                val currentRating = wallpaperRatings[file.name] ?: 0

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 40.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metadata badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        imageDimens?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            fileSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Star rating
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= currentRating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Rate $star",
                                tint = if (star <= currentRating) Color(0xFFFFD700) else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { onSetRating(file, star) }
                            )
                        }
                    }

                    // Action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { onSetWallpaper(file) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Wallpaper,
                                contentDescription = "Set as Wallpaper",
                                modifier = Modifier.size(26.dp),
                                tint = Color.White
                            )
                        }

                        OutlinedIconButton(
                            onClick = { onSaveToGallery(file) },
                            modifier = Modifier.size(44.dp),
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.SaveAlt,
                                contentDescription = "Save to gallery",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        OutlinedIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemove(file)
                            },
                            modifier = Modifier.size(44.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (showZoom) {
                currentFile?.let { file ->
                    ZoomFileImageDialog(file = file, onDismiss = { showZoom = false })
                }
            }
        }
    }
}

@Composable
private fun ZoomFileImageDialog(file: File, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 8f)
        scale = next
        panOffset = if (next > 1f) panOffset + panChange * next else Offset.Zero
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1.5f) {
                            scale = 1f
                            panOffset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    })
                }
        ) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .transformable(state = transformState)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close zoom", tint = Color.White)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onGoToDiscover: () -> Unit = {}, onAddPhotos: () -> Unit = {}) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Wallpaper,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Photos saved here rotate as your wallpaper",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onGoToDiscover) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browse Discover")
            }
            OutlinedButton(onClick = onAddPhotos) {
                Text("Add from Photos")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsContent(stats: RotationStats, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stats.totalRotations.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "total rotations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                if (stats.recentCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stats.recentCount} in recent history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }

        if (!stats.recap.isEmpty) {
            WeeklyRecapCard(stats.recap)
        }

        if (stats.topSources.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val total = stats.topSources.sumOf { it.second }.coerceAtLeast(1)
                    stats.topSources.forEach { (source, count) ->
                        val fraction = count.toFloat() / total
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = source.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }

        if (stats.topTags.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Top tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stats.topTags.forEach { (tag, count) ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text("$tag ($count)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        if (stats.totalRotations == 0L && stats.recentCount == 0) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.BarChart, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "No rotations yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    minutes < 1440 -> "${minutes / 60} hours"
    else -> "day"
}

private fun formatAgo(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else -> "${diffMin / 1440}d ago"
    }
}

@Composable
private fun RotationErrorPane(
    errors: List<RotationError>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    val errorColor = MaterialTheme.colorScheme.errorContainer
    val onErrorColor = MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = errorColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = onErrorColor
                    )
                    Text(
                        text = "${errors.size} rotation issue${if (errors.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = onErrorColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear all errors",
                            modifier = Modifier.size(14.dp),
                            tint = onErrorColor.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = onErrorColor
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    errors.forEach { error ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                errorIconFor(error.type),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                tint = onErrorColor.copy(alpha = 0.8f)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = error.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onErrorColor
                                )
                                Text(
                                    text = formatErrorTime(error.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onErrorColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun errorIconFor(type: RotationErrorType) = when (type) {
    RotationErrorType.POOL_EMPTY -> Icons.Default.Warning
    RotationErrorType.IMAGE_MISSING -> Icons.Default.ErrorOutline
    RotationErrorType.IMAGE_CORRUPT -> Icons.Default.ErrorOutline
    RotationErrorType.SET_FAILED -> Icons.Default.ErrorOutline
    RotationErrorType.DOWNLOAD_FAILED -> Icons.Default.ErrorOutline
}

private fun formatErrorTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
}

@Composable
private fun DuplicatesDialog(
    scan: DuplicateScan,
    onDismiss: () -> Unit,
    onRemove: (List<com.chrisalvis.rotato.data.DuplicateGroup>) -> Unit,
) {
    when (scan) {
        DuplicateScan.Idle -> Unit
        DuplicateScan.Scanning -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Looking for duplicates") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Comparing your library…")
                }
            },
            confirmButton = {},
        )
        is DuplicateScan.Done -> {
            val groups = scan.groups
            if (groups.isEmpty()) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text("No duplicates") },
                    text = { Text("Every image in your library is unique.") },
                    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
                )
            } else {
                val count = groups.sumOf { it.duplicates.size }
                AlertDialog(
                    onDismissRequest = onDismiss,
                    modifier = Modifier.widthIn(max = 560.dp),
                    title = { Text("$count duplicate${if (count == 1) "" else "s"} found") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "The first image in each row is kept (the highest resolution copy). The rest are removed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 360.dp)
                            ) {
                                items(groups.size) { index ->
                                    val group = groups[index]
                                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items((listOf(group.keep) + group.duplicates).size) { i ->
                                            val file = if (i == 0) group.keep else group.duplicates[i - 1]
                                            Box {
                                                AsyncImage(
                                                    model = file,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .alpha(if (i == 0) 1f else 0.55f)
                                                )
                                                if (i == 0) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Kept",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { onRemove(groups) }) { Text("Remove $count") }
                    },
                    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                )
            }
        }
    }
}

@Composable
private fun FoldPairDialog(
    first: File,
    second: File,
    onDismiss: () -> Unit,
    onConfirm: (outer: File, inner: File) -> Unit,
) {
    var swapped by remember { mutableStateOf(false) }
    val outer = if (swapped) second else first
    val inner = if (swapped) first else second
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text("Make a fold pair") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "One image shows on the cover screen and the other when you unfold. Rotation sets them together whenever either comes up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.8f)) {
                        AsyncImage(
                            model = outer,
                            contentDescription = "Cover screen image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.45f).clip(MaterialTheme.shapes.small)
                        )
                        Text("Cover screen", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { swapped = !swapped }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.6f)) {
                        AsyncImage(
                            model = inner,
                            contentDescription = "Inner screen image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.95f).clip(MaterialTheme.shapes.small)
                        )
                        Text("Unfolded", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(outer, inner) }) { Text("Pair and set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WeeklyRecapCard(recap: WeeklyRecap) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("This week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${recap.shownThisWeek} wallpaper${if (recap.shownThisWeek == 1) "" else "s"} shown in the last 7 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recap.mostShown.isNotEmpty()) {
                RecapRow("Most shown", recap.mostShown.map { (url, count) -> url as Any to "×$count" })
            }
            if (recap.topRated.isNotEmpty()) {
                RecapRow("Highest rated", recap.topRated.map { (file, rating) -> file as Any to "★$rating" })
            }
            if (recap.newest.isNotEmpty()) {
                RecapRow("New this week", recap.newest.map { it as Any to null })
            }
        }
    }
}

@Composable
private fun RecapRow(title: String, items: List<Pair<Any, String?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (model, label) ->
                Box(modifier = Modifier.weight(1f, fill = false).size(64.dp)) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                    )
                    if (label != null) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A row of colour swatches; picking one shows only Library images where that colour stands out. */
@Composable
private fun ColourFilterRow(
    selected: com.chrisalvis.rotato.data.ImageColour?,
    loading: Boolean,
    matchCount: Int?,
    onSelect: (com.chrisalvis.rotato.data.ImageColour?) -> Unit,
    matchingPhoto: Boolean = false,
    onMatchPhoto: () -> Unit = {},
    onClearMatch: () -> Unit = {},
    rainbow: Boolean = false,
    onRainbow: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Match a photo: the picked picture's colours become the filter.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (matchingPhoto) 30.dp else 26.dp)
                .clip(CircleShape)
                .background(if (matchingPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = "Match a photo") { onMatchPhoto() }
        ) {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = "Match a photo",
                tint = if (matchingPhoto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        // Rainbow order: sorts the grid by colour instead of filtering it.
        Box(
            modifier = Modifier
                .size(if (rainbow) 30.dp else 26.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935))
                    )
                )
                .border(
                    width = if (rainbow) 3.dp else 1.dp,
                    color = if (rainbow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(onClickLabel = if (rainbow) "Back to normal order" else "Sort by colour") { onRainbow() }
        )
        com.chrisalvis.rotato.data.ImageColour.entries.forEach { colour ->
            val isSelected = colour == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) 30.dp else 26.dp)
                    .clip(CircleShape)
                    .background(Color(colour.swatch))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable(onClickLabel = colour.label) { onSelect(colour) }
            )
        }
        when {
            loading -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            rainbow && !matchingPhoto && selected == null -> AssistChip(
                onClick = onRainbow,
                label = { Text("Rainbow order", style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Back to normal order", modifier = Modifier.size(16.dp)) }
            )
            matchingPhoto && matchCount != null -> AssistChip(
                onClick = onClearMatch,
                label = { Text("Matches your photo · $matchCount", style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear photo match", modifier = Modifier.size(16.dp)) }
            )
            selected != null && matchCount != null -> AssistChip(
                onClick = { onSelect(null) },
                label = { Text("${selected.label} · $matchCount", style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear colour filter", modifier = Modifier.size(16.dp)) }
            )
        }
    }
}
