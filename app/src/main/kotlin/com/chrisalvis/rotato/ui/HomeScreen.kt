package com.chrisalvis.rotato.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Saved tab: "In rotation" (the Library pool) above the collections. Tapping the
 * hero card opens the Library grid as a sub-screen with a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBrowseFeed: () -> Unit = {},
    /** No longer shown: the Saved tab lists collections itself. Kept so existing callers compile. */
    collectionsContent: @Composable () -> Unit = {},
    /** Opens Discover from the empty states. */
    onGoToDiscover: () -> Unit = onBrowseFeed,
    /** A tag tapped in a collection's viewer, searched on Discover. */
    onSearchDiscover: (String) -> Unit = {},
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
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val saveToListInProgress by viewModel.saveToListInProgress.collectAsStateWithLifecycle()
    val nsfwBlurEnabled by viewModel.nsfwBlurEnabled.collectAsStateWithLifecycle()

    val dragSelectState = rememberDragSelectState<File>()
    val inSelectionMode = dragSelectState.inSelectionMode

    // The Library grid is a sub-screen of Saved, opened from the "In rotation" card.
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var libraryFilterOpen by rememberSaveable { mutableStateOf(false) }
    var showSaveToListDialog by remember { mutableStateOf(false) }
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
    BackHandler(enabled = showLibrary && !inSelectionMode) { showLibrary = false }

    var batchCollectionPick by remember { mutableStateOf<List<File>?>(null) }
    batchCollectionPick?.let { picked ->
        SaveToCollectionDialog(
            collections = collections,
            onDismiss = { batchCollectionPick = null },
            onSelect = { listId ->
                viewModel.saveRotationToList(listId, picked)
                batchCollectionPick = null
                dragSelectState.disableSelectionMode()
            }
        )
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

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }
    val pickPhotos = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    // Refresh image pool every time this screen enters composition so wallpapers
    // downloaded from Collections show up immediately without restarting the app.
    LaunchedEffect(Unit) { viewModel.refreshFromFeeds() }

    if (!showLibrary) {
        BrowseScreen(
            onGoToDiscover = onGoToDiscover,
            onSearchDiscover = onSearchDiscover,
            savedHero = {
                SavedHeroCard(
                    images = images,
                    rotating = settings.isEnabled,
                    intervalMinutes = settings.intervalMinutes,
                    hiddenNames = if (nsfwBlurEnabled) nsfwNames else emptySet(),
                    onOpen = { showLibrary = true },
                )
            },
            onSavedSearch = { showLibrary = true; libraryFilterOpen = true },
            onAddPhotos = pickPhotos,
        )
    } else Scaffold(
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
                        IconButton(onClick = {
                            viewModel.queueAllUpNext(selection.sortedBy { it.name })
                            dragSelectState.disableSelectionMode()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Up next")
                        }
                        if (collections.isNotEmpty()) IconButton(onClick = { batchCollectionPick = selection }) {
                            Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Add to collection")
                        }
                        Box {
                            var rateOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { rateOpen = true }) {
                                Icon(Icons.Default.Star, contentDescription = "Rate selected")
                            }
                            DropdownMenu(expanded = rateOpen, onDismissRequest = { rateOpen = false }) {
                                (5 downTo 1).forEach { stars ->
                                    DropdownMenuItem(
                                        text = { Text("★".repeat(stars)) },
                                        onClick = {
                                            viewModel.setRatingFor(selection, stars)
                                            rateOpen = false
                                            dragSelectState.disableSelectionMode()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Clear rating") },
                                    onClick = {
                                        viewModel.setRatingFor(selection, 0)
                                        rateOpen = false
                                        dragSelectState.disableSelectionMode()
                                    },
                                )
                            }
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
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { showLibrary = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Saved")
                        }
                    },
                    title = {
                        Column {
                            Text("In rotation", fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                "Wallpapers Rotato cycles through · ${images.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Rarely used Library actions; Play and Filter sit in the row under the bar.
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Add photos from device") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = { menuOpen = false; pickPhotos() },
                                )
                                if (images.isNotEmpty() && collections.isNotEmpty()) DropdownMenuItem(
                                    text = { Text(if (saveToListInProgress) "Saving…" else "Save all to collection…") },
                                    leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                                    enabled = !saveToListInProgress,
                                    onClick = { menuOpen = false; showSaveToListDialog = true },
                                )
                                if (images.size > 1) DropdownMenuItem(
                                    text = { Text("Find duplicates") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = { menuOpen = false; viewModel.findDuplicates() },
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        LibraryContent(
            viewModel = viewModel,
            images = images,
            isLoading = isLoading,
            collections = collections,
            dragSelectState = dragSelectState,
            inSelectionMode = inSelectionMode,
            filterOpen = libraryFilterOpen,
            onFilterOpenChange = { libraryFilterOpen = it },
            onPhotoPick = pickPhotos,
            onGoToDiscover = onGoToDiscover,
            modifier = Modifier.padding(padding),
        )
    }
}

/** The wide "In rotation" card at the top of Saved: a peek at the Library pool. */
@Composable
private fun SavedHeroCard(
    images: List<File>,
    rotating: Boolean,
    intervalMinutes: Int,
    hiddenNames: Set<String>,
    onOpen: () -> Unit,
) {
    // Newest first; NSFW pictures stay out of the always-visible peek while blur is on.
    val peek = remember(images, hiddenNames) {
        images.asSequence()
            .filter { it.name !in hiddenNames }
            .sortedByDescending { it.lastModified() }
            .take(4)
            .toList()
    }
    val on = rotating && images.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Open In rotation", onClick = onOpen)
    ) {
        SavedMosaic(
            models = peek,
            grid = false,
            placeholder = Icons.Outlined.Wallpaper,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SavedStatusDot(on = on, modifier = Modifier.size(10.dp))
                Text(
                    "In rotation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Text(
                text = when {
                    images.isEmpty() -> "Wallpapers Rotato cycles through. Save from Discover or add photos."
                    on -> "Wallpapers Rotato cycles through · ${images.size} · every ${formatInterval(intervalMinutes)}"
                    else -> "Wallpapers Rotato cycles through · ${images.size} · paused"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun LibraryContent(
    viewModel: HomeViewModel,
    images: List<File>,
    isLoading: Boolean,
    collections: List<LocalList>,
    dragSelectState: DragSelectState<File>,
    inSelectionMode: Boolean,
    filterOpen: Boolean,
    onFilterOpenChange: (Boolean) -> Unit,
    onPhotoPick: () -> Unit,
    onGoToDiscover: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val saveToListInProgress by viewModel.saveToListInProgress.collectAsStateWithLifecycle()
    val wallpaperRatings by viewModel.wallpaperRatings.collectAsStateWithLifecycle()
    val nsfwFileNames by viewModel.nsfwFileNames.collectAsStateWithLifecycle()
    val nsfwBlurEnabled by viewModel.nsfwBlurEnabled.collectAsStateWithLifecycle()
    val lastSkipReason by viewModel.lastSkipReason.collectAsStateWithLifecycle()
    val pairedPaths by viewModel.pairedPaths.collectAsStateWithLifecycle()
    // Colour filter: each image's colours are worked out once (cached) the first time it's used.
    var colourFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedColour = colourFilter?.let { n -> com.chrisalvis.rotato.data.ImageColour.entries.find { it.name == n } }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var looksLoading by remember { mutableStateOf(false) }
    // Match a photo: pick any picture and the Library shows what goes with it, best match first.
    var matchLook by rememberSaveable(stateSaver = ImageLookSaver) { mutableStateOf<com.chrisalvis.rotato.data.ImageLook?>(null) }
    var matchLoading by remember { mutableStateOf(false) }
    // Rainbow order: lay the Library out by each image's main colour, like a bookshelf sorted by spine.
    var rainbow by rememberSaveable { mutableStateOf(false) }
    // Photo tags (Settings › Tools › Tag my photos): search the Library by what's in each picture.
    var tagQuery by rememberSaveable { mutableStateOf("") }
    // Re-read on return to the Library, since tagging happens on the Tools screen.
    var resumeTick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val photoTags by produceState(emptyMap<String, List<String>>(), images, resumeTick) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.chrisalvis.rotato.data.PhotoTagger.libraryTags(appContext) }
    }
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
    // Type, fold, NSFW and rating chips plus sort order, applied on top of colour and tag search.
    var libraryFilter by rememberSaveable(stateSaver = LibraryFilterSaver) { mutableStateOf(LibraryFilter()) }
    val foldCanvas = rememberFoldCanvas()
    val foldNames by produceState<Set<String>?>(null, images, libraryFilter.foldOnly, foldCanvas) {
        val canvas = foldCanvas
        if (!libraryFilter.foldOnly || canvas == null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { foldFriendlyNames(images, canvas) }
    }
    val hasVideos = remember(images) { images.any { com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(it) } }
    val nsfwInLibrary = remember(images, nsfwFileNames) { images.any { it.name in nsfwFileNames } }
    val baseGridImages = remember(images, selectedColour, looks, matchLook, rainbow, tagQuery, photoTags) {
        val target = matchLook
        val filtered = when {
            target != null -> images
                .mapNotNull { f -> looks[f.name]?.let { com.chrisalvis.rotato.data.ImageAnalysis.matchScore(target, it) }?.let { f to it } }
                .sortedByDescending { it.second }
                .map { it.first }
            selectedColour == null -> images
            else -> images.filter { looks[it.name]?.colours?.contains(selectedColour) == true }
        }
        val sorted = if (rainbow && target == null) com.chrisalvis.rotato.data.ImageAnalysis.rainbowOrder(filtered, looks) else filtered
        val words = tagQuery.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.replace(' ', '_') }
        if (words.isEmpty()) sorted
        else sorted.filter { f -> val tags = photoTags[f.name].orEmpty(); words.all { w -> tags.any { it.contains(w) } } }
    }
    val gridImages = remember(baseGridImages, libraryFilter, wallpaperRatings, nsfwFileNames, foldNames, matchLook, rainbow) {
        // A photo match or rainbow order already decides the order, so sorting only narrows then.
        val f = if (matchLook != null || rainbow) libraryFilter.copy(sort = LibrarySort.NAME) else libraryFilter
        f.apply(baseGridImages, wallpaperRatings, nsfwFileNames, foldNames)
    }
    // Shown as a count on the Filter pill, so a narrowed grid never looks like a missing image.
    val activeFilterCount = listOf(
        libraryFilter.kind != LibraryKind.ALL,
        libraryFilter.foldOnly,
        libraryFilter.nsfwOnly,
        libraryFilter.unratedOnly,
        libraryFilter.sort != LibrarySort.NAME,
        selectedColour != null,
        matchLook != null,
        rainbow,
        tagQuery.isNotBlank(),
    ).count { it }

    // Play: the Library (as currently filtered) in the full-screen feed.
    var reelStart by remember { mutableStateOf<Int?>(null) }
    reelStart?.let { start ->
        val reelItems = remember(gridImages) {
            gridImages.map { f ->
                val uri = f.toURI().toString()
                com.chrisalvis.rotato.data.BrainrotWallpaper(
                    id = f.absolutePath, source = "device", thumbUrl = uri, sampleUrl = uri, fullUrl = uri,
                    resolution = "", pageUrl = "", tags = photoTags[f.name].orEmpty(),
                    isVideo = com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(f), isNsfw = f.name in nsfwFileNames,
                )
            }
        }
        ReelPlayer(
            items = reelItems,
            startIndex = start,
            onDismiss = { reelStart = null },
            onSet = { viewModel.setSpecificWallpaper(File(it.id)) },
            onShare = { wp -> shareLocalFile(appContext, File(wp.id)) },
            title = { "Library" },
            nsfwBlurEnabled = nsfwBlurEnabled,
        )
    }

    if (filterOpen) {
        SavedFilterSheet(
            showReset = activeFilterCount > 0,
            onReset = {
                libraryFilter = LibraryFilter()
                colourFilter = null
                matchLook = null
                rainbow = false
                tagQuery = ""
            },
            onDismiss = { onFilterOpenChange(false) },
        ) {
            SavedSheetSection("Search your photos")
            if (photoTags.values.any { it.isNotEmpty() }) {
                androidx.compose.material3.OutlinedTextField(
                    value = tagQuery,
                    onValueChange = { tagQuery = it },
                    placeholder = { Text("beach, sunset, cat…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (tagQuery.isNotEmpty()) IconButton(onClick = { tagQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            } else {
                Text(
                    "Tag your photos in Settings › Tools › Tag my photos to search by what's in them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            SavedSheetSection("Colour")
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
            SavedSheetSection("Show")
            LibraryFilterChips(
                filter = libraryFilter,
                onChange = { libraryFilter = it },
                hasVideos = hasVideos,
                showFold = foldCanvas != null,
                showNsfw = nsfwInLibrary && !LocalNsfwHidden.current,
            )
            SavedSheetSection("Sort")
            LibrarySortChips(
                filter = libraryFilter,
                onChange = { libraryFilter = it },
                enabled = matchLook == null && !rainbow,
            )
            Text(
                "${gridImages.size} of ${images.size} shown",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading || saveToListInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

        // The viewer keeps the order it opened with: rating an image under "Top rated" or
        // "Unrated" would otherwise re-sort the pages and slide a different one under the finger.
        // Deleted images still drop out. It always opens full screen, even unfolded.
        val openedOrder = remember(selectedFile) { gridImages }
        val viewerImages = remember(openedOrder, images) { val live = images.toHashSet(); openedOrder.filter { it in live } }
        // Pages through the grid as filtered and sorted, starting at the tapped image.
        selectedFile?.takeIf { viewerImages.isNotEmpty() }?.let { file ->
            ImagePreviewDialog(
                images = viewerImages,
                initialFile = file,
                onDismiss = { selectedFile = null },
                onSetWallpaper = { currentFile ->
                    viewModel.setSpecificWallpaper(currentFile)
                    selectedFile = null
                },
                onRemove = { currentFile ->
                    viewModel.removeImage(currentFile)
                    if (viewerImages.size == 1) selectedFile = null
                },
                onSaveToGallery = { currentFile -> viewModel.saveFileToGallery(currentFile) },
                onPlayNext = { currentFile -> viewModel.queueUpNext(currentFile, first = true) },
                wallpaperRatings = wallpaperRatings,
                onSetRating = { currentFile, rating -> viewModel.setRating(currentFile, rating) },
                collections = collections,
                onAddToCollection = { currentFile, listId -> viewModel.saveRotationToList(listId, listOf(currentFile)) },
                // Same as "match a photo", with this image as the photo: the grid shows what goes with it.
                onFindSimilar = { currentFile ->
                    selectedFile = null
                    matchLoading = true
                    matchScope.launch {
                        val look = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.chrisalvis.rotato.data.ImageAnalysis.lookFor(appContext, currentFile)
                        }
                        matchLoading = false
                        if (look == null || look.colours.isEmpty()) {
                            android.widget.Toast.makeText(appContext, "Couldn't read this image's colours", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            colourFilter = null
                            matchLook = look
                        }
                    }
                },
            )
        }

        if (!inSelectionMode && images.isNotEmpty()) {
            // Health in context: one dismissible line, only when there's something to say.
            val freeBytes by produceState(Long.MAX_VALUE, resumeTick, images.size) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { appContext.filesDir.usableSpace }
            }
            var dismissedNotice by rememberSaveable { mutableStateOf<String?>(null) }
            val skipReason = lastSkipReason
            // (key, text, warning): the key keeps a dismissed storage warning gone as free space ticks.
            val notice: Triple<String, String, Boolean>? = when {
                freeBytes < 1_000_000_000L -> Triple(
                    "storage",
                    "Storage low (${com.chrisalvis.rotato.data.StorageTools.format(freeBytes)} free). Settings › Tools › Storage can clear caches.",
                    true,
                )
                skipReason != null -> Triple(skipReason, skipReason, false)
                else -> null
            }
            if (notice != null && notice.first != dismissedNotice) {
                SavedNoticeLine(
                    text = notice.second,
                    warning = notice.third,
                    onDismiss = { dismissedNotice = notice.first },
                )
            }
            SavedActionRow {
                SavedPill(
                    text = "Play",
                    onClick = { reelStart = 0 },
                    icon = Icons.Default.PlayArrow,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    enabled = gridImages.isNotEmpty(),
                )
                Spacer(Modifier.weight(1f))
                SavedPill(
                    text = "Filter",
                    onClick = { onFilterOpenChange(true) },
                    icon = Icons.Default.Tune,
                    badgeCount = activeFilterCount,
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = viewModel::refreshImages,
            state = pullRefreshState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (images.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onGoToDiscover = onGoToDiscover,
                    onAddPhotos = onPhotoPick
                )
            } else if (gridImages.isEmpty() && !looksLoading && !matchLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Text(
                        "Nothing in your Library matches these filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { onFilterOpenChange(true) }) { Text("Change filters") }
                }
            } else {
                // Edge to edge, portrait tiles; unfolded gets more columns, never a pane beside the grid.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(savedGridColumns(maxWidth)),
                        state = dragSelectState.gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .gridDragSelect(items = gridImages, state = dragSelectState),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(gridImages, key = { it.absolutePath }) { file ->
                            val isSelected by remember { derivedStateOf { dragSelectState.isSelected(file) } }
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
                    }
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
internal fun RotationStatusCard(
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
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
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

        // Size and resolution live in the viewer; tiles only flag videos, with their length.
        if (badgeInfo?.isVideo == true && !isBlurred) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(12.dp))
                val durationMs = badgeInfo?.durationMs ?: 0L
                if (durationMs > 0L) {
                    Text(formatVideoDuration(durationMs), color = Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
        }

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
    val fileSizeText: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
)

private fun formatVideoDuration(ms: Long): String {
    val totalSec = ms / 1000L
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

private fun readImageBadgeInfo(file: File): ImageBadgeInfo? {
    if (com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file)) return readVideoBadgeInfo(file)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return ImageBadgeInfo(
        width = options.outWidth,
        height = options.outHeight,
        fileSizeText = formatFileSize(file.length())
    )
}

/** Size of a Library video, turned the way it plays. */
private fun readVideoBadgeInfo(file: File): ImageBadgeInfo? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) return null
        val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val (width, height) = if (rotation % 180 != 0) h to w else w to h
        ImageBadgeInfo(width, height, formatFileSize(file.length()), isVideo = true, durationMs = duration)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
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

/** A readable caption from a file name: separators become spaces and long hash-like runs go. */
private fun friendlyFileTitle(file: File): String {
    val words = file.nameWithoutExtension
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
    val kept = words.filterNot { w ->
        val hasDigit = w.any { it.isDigit() }
        (w.length >= 12 && hasDigit) ||
            (w.length >= 8 && hasDigit && w.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
    }
    val title = kept.joinToString(" ").trim()
    return title.ifBlank {
        if (com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file)) "Saved video" else "Saved image"
    }
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
    onPlayNext: (File) -> Unit = {},
    wallpaperRatings: Map<String, Int> = emptyMap(),
    onSetRating: (File, Int) -> Unit = { _, _ -> },
    /** Same viewer actions as Discover and Collections: collection, screen preview, share. */
    collections: List<LocalList> = emptyList(),
    onAddToCollection: (File, String) -> Unit = { _, _ -> },
    /** Shows the Library images whose colours go with this one; the button is hidden when null. */
    onFindSimilar: ((File) -> Unit)? = null,
    /** Unfolded: shown in a pane beside the grid instead of a full-screen dialog. */
    inline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val viewerContext = LocalContext.current
    var collectionPickFor by remember { mutableStateOf<File?>(null) }
    var screenPreviewFor by remember { mutableStateOf<File?>(null) }
    collectionPickFor?.let { f ->
        SaveToCollectionDialog(
            collections = collections,
            onDismiss = { collectionPickFor = null },
            onSelect = { listId -> onAddToCollection(f, listId); collectionPickFor = null },
        )
    }
    screenPreviewFor?.let { f ->
        ScreenPreviewDialog(
            imageUrl = f.toURI().toString(),
            onSet = { onSetWallpaper(f); screenPreviewFor = null },
            onDismiss = { screenPreviewFor = null },
        )
    }
    val coroutineScope = rememberCoroutineScope()
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
    // Zoom happens in place on the current page; one tap hides/shows everything around the image.
    val zoom = remember { ZoomState() }
    val zoomed = zoom.zoomed
    var chromeVisible by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }
    val offsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    // Height of the bottom bar above the nav bar, so a video's seek bar sits above its buttons.
    // The bar pads for the nav bar itself and VideoPlayerView adds that inset again, so it's left out.
    var bottomPanelHeight by remember { mutableStateOf(0.dp) }
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)

    LaunchedEffect(images.size, pagerState.currentPage) {
        if (images.isEmpty()) {
            onDismiss()
            return@LaunchedEffect
        }
        if (pagerState.currentPage > images.lastIndex) {
            pagerState.scrollToPage(images.lastIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) { zoom.reset() }

    PreviewHost(inline = inline, onDismiss = onDismiss, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f)
                }
                .pointerInput(isDismissing, zoomed) {
                    // Swipe down to close; off while zoomed, where a drag pans the image instead.
                    if (isDismissing || zoomed) return@pointerInput
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
                                // A second finger is a pinch; leave it to the zoom.
                                if (event.changes.count { it.pressed } >= 2) break@detect
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
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
            ) { page ->
                val pageFile = images.getOrNull(page) ?: return@HorizontalPager
                val isCurrent = page == pagerState.currentPage
                if (com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(pageFile)) {
                    // Only the page in view plays; neighbours show their first frame.
                    if (isCurrent) {
                        VideoPlayerView(
                            url = android.net.Uri.fromFile(pageFile).toString(),
                            modifier = Modifier.fillMaxSize(),
                            allowTapToToggle = true,
                            showMuteButton = true,
                            showSeekBar = true,
                            allowDoubleTapSeek = true,
                            seekBarBottomInset = bottomPanelHeight,
                            controlsVisible = chromeVisible,
                            onControlsVisibleChange = { chromeVisible = it },
                        )
                    } else {
                        AsyncImage(model = pageFile, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    return@HorizontalPager
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { if (isCurrent) zoom.size = it }
                ) {
                    AsyncImage(
                        model = pageFile,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomTransform(zoom, active = isCurrent)
                            .zoomGestures(
                                zoom,
                                active = isCurrent,
                                onTap = { chromeVisible = !chromeVisible },
                            )
                    )
                }
            }

            AnimatedVisibility(
                visible = chromeVisible && !zoomed,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ViewerTopBar(
                    position = if (images.size > 1) "${pagerState.currentPage + 1} of ${images.size}" else null,
                    onBack = onDismiss,
                    onDetails = { showDetails = true },
                )
            }

            currentFile?.let { file ->
                val isVideo = remember(file) { com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file) }
                val info by produceState<ImageBadgeInfo?>(null, file) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { readImageBadgeInfo(file) }.getOrNull()
                    }
                }
                val fileSize = remember(file) { formatFileSize(file.length()) }
                val rating = wallpaperRatings[file.name] ?: 0
                val title = remember(file) { friendlyFileTitle(file) }
                val subtitle = listOfNotNull(
                    if (isVideo) {
                        val d = info?.durationMs ?: 0L
                        if (d > 0L) "Video · ${formatVideoDuration(d)}" else "Video"
                    } else null,
                    info?.let { i -> sizeClassOf(i.width, i.height)?.let { "$it · ${i.width} × ${i.height}" } },
                    fileSize,
                    if (rating > 0) "★ $rating" else null,
                ).joinToString(" · ")
                val actions = buildList {
                    add(ViewerAction(Icons.Outlined.Wallpaper, "Set", onClick = { onSetWallpaper(file) }, primary = true))
                    if (collections.isNotEmpty()) {
                        add(ViewerAction(Icons.Outlined.BookmarkBorder, "Save", onClick = { collectionPickFor = file }))
                    }
                    if (!isVideo) {
                        add(ViewerAction(Icons.Default.Smartphone, "Try it on", onClick = { screenPreviewFor = file }))
                    }
                    add(ViewerAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Play next", onClick = { onPlayNext(file) }))
                    add(ViewerAction(Icons.Default.Share, "Share", onClick = { shareLibraryFile(viewerContext, file) }))
                }

                AnimatedVisibility(
                    visible = chromeVisible && !zoomed,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ViewerBottomBar(
                        title = title,
                        subtitle = subtitle,
                        actions = actions,
                        onDetails = { showDetails = true },
                        filmstrip = images,
                        currentIndex = pagerState.currentPage,
                        onJump = { i -> coroutineScope.launch { pagerState.scrollToPage(i) } },
                        modifier = Modifier.onGloballyPositioned {
                            bottomPanelHeight = with(density) { (it.size.height - navBarBottomPx).coerceAtLeast(0).toDp() }
                        },
                    )
                }

                if (showDetails) {
                    LibraryImageDetailsSheet(
                        file = file,
                        title = title,
                        info = info,
                        isVideo = isVideo,
                        fileSize = fileSize,
                        rating = rating,
                        onSetRating = { star -> onSetRating(file, star) },
                        onFindSimilar = onFindSimilar?.let { cb -> { showDetails = false; cb(file) } },
                        onSaveToGallery = { onSaveToGallery(file) },
                        onRemove = { showDetails = false; onRemove(file) },
                        onDismiss = { showDetails = false },
                    )
                }
            }

            // Back while zoomed zooms out instead of closing the viewer.
            BackHandler(enabled = zoomed) { zoom.reset() }
        }
    }
}

private fun shareLibraryFile(context: android.content.Context, file: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val type = if (com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file)) "video/*" else "image/*"
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share wallpaper"))
    }.onFailure {
        android.widget.Toast.makeText(context, "Couldn't share this file", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Everything about one Saved image that doesn't need to be on screen all the time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryImageDetailsSheet(
    file: File,
    title: String,
    info: ImageBadgeInfo?,
    isVideo: Boolean,
    fileSize: String,
    rating: Int,
    onSetRating: (Int) -> Unit,
    onFindSimilar: (() -> Unit)?,
    onSaveToGallery: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val look by produceState<com.chrisalvis.rotato.data.ImageLook?>(null, file, isVideo) {
        if (isVideo) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.chrisalvis.rotato.data.ImageAnalysis.lookFor(context.applicationContext, file) }.getOrNull()
        }
    }
    val pillContainer = MaterialTheme.colorScheme.surfaceVariant
    val pillContent = MaterialTheme.colorScheme.onSurfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isVideo) {
                        val d = info?.durationMs ?: 0L
                        InfoPill(if (d > 0L) "Video · ${formatVideoDuration(d)}" else "Video", container = pillContainer, content = pillContent, bold = true)
                    }
                    if (info != null) {
                        sizeClassOf(info.width, info.height)?.let { InfoPill(it, container = pillContainer, content = pillContent, bold = true) }
                        InfoPill("${info.width} × ${info.height}", container = pillContainer, content = pillContent)
                        val ratio = info.width.toFloat() / info.height
                        InfoPill(
                            if (ratio > 1.15f) "Landscape" else if (ratio < 0.87f) "Portrait" else "Square",
                            container = pillContainer,
                            content = pillContent,
                        )
                    }
                    InfoPill(fileSize, container = pillContainer, content = pillContent)
                }
            }

            SheetSection("Your rating") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Rate $star",
                            tint = if (star <= rating) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onSetRating(star) }
                                .padding(2.dp)
                        )
                    }
                }
            }

            val colours = look?.colours.orEmpty()
            if (colours.isNotEmpty()) {
                SheetSection("Colours") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        colours.forEach { colour ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(colour.swatch))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(colour.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (onFindSimilar != null) {
                        FilledTonalButton(onClick = onFindSimilar) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Find similar")
                        }
                    }
                }
            }

            Column {
                DetailsSheetRow(Icons.Default.SaveAlt, "Save to phone gallery", onClick = onSaveToGallery)
                DetailsSheetRow(
                    Icons.Default.Delete,
                    "Remove from Saved",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailsSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** Full-screen dialog, or (unfolded) a plain pane that clips to its bounds. */
@Composable
internal fun PreviewHost(inline: Boolean, onDismiss: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    if (inline) {
        Box(modifier.clipToBounds().background(Color.Black)) { content() }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) { Box(Modifier.volumeUpUnmutes()) { content() } }
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
internal fun RotationErrorPane(
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


/** Shares a picture or video from the phone through the system share sheet. */
internal fun shareLocalFile(context: android.content.Context, file: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (com.chrisalvis.rotato.live.LiveWallpaper.isVideoFile(file)) "video/*" else "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share wallpaper").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        android.widget.Toast.makeText(context, "Couldn't share this file", android.widget.Toast.LENGTH_SHORT).show()
    }
}
