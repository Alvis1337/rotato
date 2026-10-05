package com.chrisalvis.rotato.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridGridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.InterestProfile
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.MediaType
import com.chrisalvis.rotato.data.TagTier
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.chrisalvis.rotato.data.MinResolution
import com.chrisalvis.rotato.data.plugins.PluginRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

private fun sourceDisplayName(source: String): String =
    source.replaceFirstChar { it.uppercase() }

/** Parses "WxH" resolution string to aspect ratio. Falls back to 16:9 on any parse error. */
private fun parseAspectRatio(resolution: String): Float {
    if (resolution.isBlank()) return 16f / 9f
    val parts = resolution.lowercase().split("x")
    if (parts.size != 2) return 16f / 9f
    val w = parts[0].trim().toFloatOrNull() ?: return 16f / 9f
    val h = parts[1].trim().toFloatOrNull() ?: return 16f / 9f
    if (w <= 0f || h <= 0f) return 16f / 9f
    return w / h
}

private fun completeQuery(current: String, tag: String): String {
    val beforeLastToken = current.trimEnd().substringBeforeLast(' ', missingDelimiterValue = "")
    return if (beforeLastToken.isBlank()) tag else "$beforeLastToken $tag"
}

private fun Int.toComposeColor(): Color = Color((this and 0xFFFFFF) or 0xFF000000.toInt())

private fun nextSourceNsfw(current: Boolean?): Boolean? = when (current) {
    null -> true
    true -> false
    false -> null
}

@Composable
private fun LoadingMoreIndicator(modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Loading more...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainrotScreen(
    externalViewModel: BrainrotViewModel? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSources: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: BrainrotViewModel = externalViewModel ?: viewModel()

    val gridItems by vm.gridItems.collectAsStateWithLifecycle()
    val selectedItem by vm.selectedItem.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val savedListIds by vm.savedListIds.collectAsStateWithLifecycle()
    val lockedHiddenCount by vm.lockedHiddenCount.collectAsStateWithLifecycle()
    val foldPairOuter by vm.foldPairOuter.collectAsStateWithLifecycle()
    val forYouEnabled by vm.forYouEnabled.collectAsStateWithLifecycle()
    val foldPairBusy by vm.foldPairBusy.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val endReached by vm.endReached.collectAsStateWithLifecycle()
    val noResults by vm.noResults.collectAsStateWithLifecycle()
    val noResultsReason by vm.noResultsReason.collectAsStateWithLifecycle()
    val noSources by vm.noSources.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val selectedListId by vm.selectedListId.collectAsStateWithLifecycle()
    val nsfwMode by vm.nsfwMode.collectAsStateWithLifecycle()
    val nsfwHidden = LocalNsfwHidden.current
    val videoPreviewMode by vm.videoPreviewMode.collectAsStateWithLifecycle()
    val nsfwBlurEnabled by vm.nsfwBlurEnabled.collectAsStateWithLifecycle()
    val brainrotFilters by vm.brainrotFilters.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val batchSelected by vm.batchSelected.collectAsStateWithLifecycle()
    val batchMode by vm.batchMode.collectAsStateWithLifecycle()
    val downloadingIds by vm.downloadingIds.collectAsStateWithLifecycle()
    val handsFreeInterval by vm.handsFreeInterval.collectAsStateWithLifecycle()
    val savedSourceIds by vm.savedSourceIds.collectAsStateWithLifecycle()
    val resetVersion by vm.resetVersion.collectAsStateWithLifecycle()
    val danbooruEnabled by vm.danbooruEnabled.collectAsStateWithLifecycle()
    val allSources by vm.allSources.collectAsStateWithLifecycle()
    val pluginRepository = remember(context) { PluginRepository(context.applicationContext) }
    val manifests by pluginRepository.manifests.collectAsStateWithLifecycle(emptyList())
    val pinnedSearches by vm.pinnedSearches.collectAsStateWithLifecycle()
    val tagSuggestions by vm.tagSuggestions.collectAsStateWithLifecycle()
    val gridMode by vm.gridMode.collectAsStateWithLifecycle()
    val dataSaverActive by vm.dataSaverActive.collectAsStateWithLifecycle()
    val discoverHintSeen by vm.discoverHintSeen.collectAsStateWithLifecycle()
    val interestAlignEnabled by vm.interestAlignEnabled.collectAsStateWithLifecycle()
    val interestProfiles by vm.interestProfiles.collectAsStateWithLifecycle()
    val manifestMap = remember(manifests) { manifests.associateBy { it.id } }

    val gridState = rememberLazyStaggeredGridState()
    val compactGridState = rememberLazyGridState()
    // Lazy layouts keep a small off-screen buffer mounted for smooth scrolling; video autoplay
    // should only kick in for items actually within the viewport, not the whole mounted buffer.
    val visibleStaggeredKeys by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
    }
    val visibleCompactKeys by remember {
        derivedStateOf { compactGridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
    }
    val coroutineScope = rememberCoroutineScope()
    val showScrollTop by remember(gridMode) {
        derivedStateOf {
            if (gridMode) compactGridState.firstVisibleItemIndex > 8 else gridState.firstVisibleItemIndex > 5
        }
    }
    val shouldLoadMore by remember(gridMode) {
        derivedStateOf {
            if (gridMode) {
                val lastVisible = compactGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = compactGridState.layoutInfo.totalItemsCount
                total > 0 && lastVisible >= total - 16
            } else {
                // Staggered lanes don't list visible items in index order; use the furthest one.
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
                val total = gridState.layoutInfo.totalItemsCount
                total > 0 && lastVisible >= total - 10
            }
        }
    }
    // Re-evaluated whenever any input changes, not just shouldLoadMore. Keyed on shouldLoadMore
    // alone, a page that finished (or came back empty) while the user was already at the bottom
    // never triggered the next one until they scrolled away and back.
    LaunchedEffect(gridMode) {
        snapshotFlow { shouldLoadMore && !loading && !loadingMore && !endReached && selectedItem == null }
            .collect { ready -> if (ready) vm.loadMore() }
    }
    LaunchedEffect(resetVersion, gridMode) {
        if (resetVersion > 0) {
            if (gridMode) compactGridState.scrollToItem(0) else gridState.scrollToItem(0)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.skipEvent.collect {
            // The viewer shows its own undo pill.
            if (vm.selectedItem.value != null) return@collect
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Wallpaper skipped",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo()
        }
    }

    LaunchedEffect(Unit) {
        vm.blockEvent.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "Image blocked — won't appear again",
                duration = SnackbarDuration.Short
            )
        }
    }

    BackHandler(enabled = batchMode && selectedItem == null) {
        vm.clearBatchSelection()
    }

    var showSettings by remember { mutableStateOf(false) }
    var showHandsFree by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchText = searchQuery.ifBlank { "" }
            delay(100)
            runCatching { searchFocusRequester.requestFocus() }
        } else {
            vm.clearTagSuggestions()
        }
    }
    LaunchedEffect(showSearch, searchText) {
        if (!showSearch) return@LaunchedEffect
        delay(300)
        vm.fetchTagSuggestions(searchText)
    }
    // Track position in grid while user browses the detail overlay; restore on dismiss
    var restoreScrollIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedItem, gridMode) {
        if (selectedItem == null && restoreScrollIndex >= 0) {
            if (gridMode) compactGridState.scrollToItem(restoreScrollIndex) else gridState.scrollToItem(restoreScrollIndex)
            restoreScrollIndex = -1
        } else if (selectedItem != null) {
            val idx = gridItems.indexOfFirst { it.id == selectedItem!!.id && it.source == selectedItem!!.source }
            if (idx >= 0) restoreScrollIndex = idx
        }
    }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var pendingAddWallpaper by remember { mutableStateOf<BrainrotWallpaper?>(null) }
    var pendingBatchSave by remember { mutableStateOf(false) }
    var showBatchSaveMenu by remember { mutableStateOf(false) }

    if (showCreateListDialog) {
        var newListName by remember { mutableStateOf("") }
        val createListFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(100)
            runCatching { createListFocus.requestFocus() }
        }
        val doCreate = {
            if (newListName.isNotBlank()) {
                vm.createList(newListName)
                showCreateListDialog = false
            }
        }
        AlertDialog(
            onDismissRequest = {
                showCreateListDialog = false
                pendingAddWallpaper = null
                pendingBatchSave = false
            },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { doCreate() }),
                    modifier = Modifier.focusRequester(createListFocus)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = doCreate,
                    enabled = newListName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateListDialog = false
                    pendingAddWallpaper = null
                    pendingBatchSave = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    fun onAddToList(wp: BrainrotWallpaper, list: LocalList? = null) {
        when {
            lists.isEmpty() -> {
                pendingAddWallpaper = wp
                showCreateListDialog = true
            }
            list != null -> vm.addToList(list.id, wp)
            else -> vm.addToList(selectedListId ?: lists.first().id, wp)
        }
    }

    fun startBatchSave() {
        when {
            lists.isEmpty() -> {
                pendingBatchSave = true
                showCreateListDialog = true
            }
            else -> showBatchSaveMenu = true
        }
    }

    // React to newly created list by completing pending add
    LaunchedEffect(selectedListId, lists) {
        val currentListId = selectedListId
        if (currentListId != null && lists.isNotEmpty()) {
            pendingAddWallpaper?.let { pending ->
                vm.addToList(currentListId, pending)
                pendingAddWallpaper = null
            }
            if (pendingBatchSave) {
                vm.saveBatchToList(currentListId)
                pendingBatchSave = false
            }
        }
    }

    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = settingsSheetState
        ) {
            DiscoverSettingsSheetContent(
                nsfwMode = nsfwMode,
                filters = brainrotFilters,
                lists = lists,
                selectedListId = selectedListId,
                interestAlignEnabled = interestAlignEnabled,
                interestProfiles = interestProfiles,
                onSelectList = { vm.setSelectedList(it) },
                onSetNsfwMode = { vm.setNsfwMode(it) },
                onSetMinResolution = { vm.setMinResolution(it) },
                onSetAspectRatio = { vm.setAspectRatio(it) },
                isFoldable = vm.isFoldable,
                onSetFoldFriendly = { vm.setFoldFriendly(it) },
                forYouEnabled = forYouEnabled,
                onSetForYou = { vm.setForYouEnabled(it) },
                onResetLearned = { vm.resetLearnedTaste() },
                onSetUseMalFilter = { vm.setUseMalFilter(it) },
                onSetInterestAlign = { vm.setInterestAlignEnabled(it) },
                onToggleProfile = { vm.toggleDiscoverProfile(it) },
                onDismiss = { showSettings = false }
            )
        }
    }

    // Report sheet — hoisted so it survives selectedItem becoming null after block/dismiss
    var reportingWallpaper by remember { mutableStateOf<BrainrotWallpaper?>(null) }
    var showReportSheet by remember { mutableStateOf(false) }
    val reportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showReportSheet && reportingWallpaper != null) {
        val wp = reportingWallpaper!!
        ModalBottomSheet(
            onDismissRequest = { showReportSheet = false },
            sheetState = reportSheetState
        ) {
            ReportSheetContent(
                wallpaperUrl = wp.fullUrl,
                onReport = { reason ->
                    vm.blockAndRemove(wp)
                    val subject = "Rotato Image Report"
                    val body = "Reason: $reason\n\nImage URL: ${wp.fullUrl}\nPage URL: ${wp.pageUrl}"
                    val mailto = android.net.Uri.parse(
                        "mailto:alvisleet@gmail.com?subject=${android.net.Uri.encode(subject)}&body=${android.net.Uri.encode(body)}"
                    )
                    context.startActivity(Intent(Intent.ACTION_SENDTO, mailto))
                    showReportSheet = false
                    reportingWallpaper = null
                },
                onDismiss = { showReportSheet = false }
            )
        }
    }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!noSources) {
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 8.dp, bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Discover",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = { vm.surpriseMe() },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("Refresh")
                                }
                                IconButton(onClick = { vm.toggleGridMode() }) {
                                    Icon(
                                        imageVector = if (gridMode) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = if (gridMode) "Card layout" else "Grid layout"
                                    )
                                }
                            }
                            if (allSources.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 2.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sources", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(allSources, key = { "${it.pluginId}:${it.instanceId}" }) { src ->
                                        val manifest = manifestMap[src.pluginId]
                                        val missingCreds = manifest?.needsApiKey == true && src.apiKey.isBlank()
                                        val hasCreds = manifest?.needsApiKey == true && src.apiKey.isNotBlank()
                                        val nsfwIcon = when (src.nsfwEnabled) {
                                            null -> Icons.Outlined.Visibility
                                            true -> Icons.Default.Visibility
                                            false -> Icons.Default.VisibilityOff
                                        }
                                        val nsfwDescription = when (src.nsfwEnabled) {
                                            null -> "Inherit global NSFW"
                                            true -> if (nsfwMode) "NSFW enabled for this source" else "NSFW enabled for this source (inactive — global NSFW is off)"
                                            false -> "NSFW disabled for this source"
                                        }
                                        FilterChip(
                                            selected = src.enabled,
                                            onClick = { vm.toggleSource(src) },
                                            label = { Text(if (src.pluginId == "REDDIT" && src.instanceId.isNotBlank()) "r/${src.instanceId}" else manifest?.name ?: src.pluginId.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                                            // Show the per-source NSFW icon whenever there's an active override
                                            // to see/reset, not just while the global toggle happens to be on —
                                            // otherwise a stale override becomes invisible (and unfixable) the
                                            // moment NSFW mode is turned off.
                                            trailingIcon = if (missingCreds || hasCreds || (!nsfwHidden && (nsfwMode || src.nsfwEnabled != null))) {
                                                {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        when {
                                                            missingCreds -> Icon(
                                                                Icons.Default.Warning,
                                                                contentDescription = "API key missing",
                                                                modifier = Modifier.size(12.dp),
                                                                tint = MaterialTheme.colorScheme.error,
                                                            )
                                                            hasCreds -> Icon(
                                                                Icons.Default.VpnKey,
                                                                contentDescription = "API key set",
                                                                modifier = Modifier.size(12.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            )
                                                        }
                                                        if (!nsfwHidden && (nsfwMode || src.nsfwEnabled != null)) {
                                                            Icon(
                                                                imageVector = nsfwIcon,
                                                                contentDescription = nsfwDescription,
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clip(CircleShape)
                                                                    .clickable {
                                                                        vm.setSourceNsfw(
                                                                            src.pluginId,
                                                                            src.instanceId,
                                                                            nextSourceNsfw(src.nsfwEnabled),
                                                                        )
                                                                    },
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                    item {
                                        IconButton(
                                            onClick = onNavigateToSources,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = "Configure sources", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
                    if (!discoverHintSeen) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Tips for Discover",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { vm.dismissDiscoverHint() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text("• Tap source chips to toggle which sources appear", style = MaterialTheme.typography.bodySmall)
                                Text("• Tap ⚙️ (bottom-right) to set ${if (nsfwHidden) "" else "NSFW mode, "}filters & search tags", style = MaterialTheme.typography.bodySmall)
                                Text("• Tap a card to preview · Long-press to batch-select", style = MaterialTheme.typography.bodySmall)
                                Text("• Tap the bookmark icon on any image to save it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    when {
                    noSources -> NoSourcesState(onNavigateToSources = onNavigateToSources)
                    noResults -> NoResultsState(
                        searchQuery = searchQuery,
                        noResultsReason = noResultsReason,
                        onRetry = { vm.retry() },
                        onClearSearch = { vm.setSearchQuery("") },
                        onDisableWifiOnly = { vm.setWifiOnly(false) },
                        onOpenSearch = { showSearch = true },
                        onOpenSettings = { showSettings = true }
                    )
                    loading && gridItems.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(3) {
                                    ShimmerBox(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(MaterialTheme.shapes.medium)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val pullRefreshState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = loading,
                            onRefresh = { vm.loadMore(reset = true) },
                            state = pullRefreshState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (gridMode) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 100.dp),
                                    state = compactGridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    gridGridItems(gridItems, key = { "${it.source}:${it.id}" }) { wp ->
                                        DiscoverThumbnailGridItem(
                                            wallpaper = wp,
                                            isDownloading = downloadingIds.contains(wp.id),
                                            isSaved = "${wp.source}:${wp.id}" in savedSourceIds,
                                            isSelected = wp.id in batchSelected,
                                            selectionMode = batchMode,
                                            videoPreviewMode = videoPreviewMode,
                                            isVisible = "${wp.source}:${wp.id}" in visibleCompactKeys,
                                            nsfwBlurEnabled = nsfwBlurEnabled,
                                            onClick = {
                                                if (batchMode) vm.toggleBatchSelect(wp.id) else vm.selectItem(wp)
                                            },
                                            onLongPress = { vm.toggleBatchSelect(wp.id) }
                                        )
                                    }
                                    if (loadingMore) {
                                        gridGridItems(
                                            items = listOf("loading-more"),
                                            key = { it },
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            LoadingMoreIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                            )
                                        }
                                    }
                                    if (endReached && gridItems.isNotEmpty()) {
                                        gridGridItems(
                                            items = listOf("end-reached"),
                                            key = { it },
                                            span = { GridItemSpan(maxLineSpan) },
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                Text(
                                                    "No more matches — try different filters",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                OutlinedButton(onClick = { vm.forceLoadMore() }) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Load more anyway")
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
                                    state = gridState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalItemSpacing = 8.dp
                                ) {
                                    items(gridItems, key = { "${it.source}:${it.id}" }) { wp ->
                                        DiscoverGridItem(
                                            wallpaper = wp,
                                            isDownloading = downloadingIds.contains(wp.id),
                                            isSaved = "${wp.source}:${wp.id}" in savedSourceIds,
                                            isSelected = wp.id in batchSelected,
                                            selectionMode = batchMode,
                                            videoPreviewMode = videoPreviewMode,
                                            isVisible = "${wp.source}:${wp.id}" in visibleStaggeredKeys,
                                            nsfwBlurEnabled = nsfwBlurEnabled,
                                            dataSaver = dataSaverActive,
                                            onClick = {
                                                if (batchMode) vm.toggleBatchSelect(wp.id) else vm.selectItem(wp)
                                            },
                                            onLongPress = { vm.toggleBatchSelect(wp.id) }
                                        )
                                    }
                                    if (loadingMore) {
                                        items(listOf("loading-more"), span = { StaggeredGridItemSpan.FullLine }) {
                                            LoadingMoreIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                            )
                                        }
                                    }
                                    if (endReached && gridItems.isNotEmpty()) {
                                        items(listOf("end-reached"), span = { StaggeredGridItemSpan.FullLine }) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                Text(
                                                    "No more matches — try different filters",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                OutlinedButton(onClick = { vm.forceLoadMore() }) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Load more anyway")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } // closes Column
            if (!noSources) {
                if (batchMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        tonalElevation = 4.dp,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${batchSelected.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                FilledTonalButton(onClick = { startBatchSave() }) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save all")
                                }
                                DropdownMenu(
                                    expanded = showBatchSaveMenu,
                                    onDismissRequest = { showBatchSaveMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    lists.forEach { list ->
                                        DropdownMenuItem(
                                            text = { Text(list.name) },
                                            onClick = {
                                                vm.saveBatchToList(list.id)
                                                showBatchSaveMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = {
                                showBatchSaveMenu = false
                                vm.clearBatchSelection()
                            }) {
                                Text("Clear")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showScrollTop) {
                            SmallFloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (gridMode) compactGridState.scrollToItem(0) else gridState.scrollToItem(0)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (searchQuery.isNotBlank()) {
                            AssistChip(
                                onClick = { showSearch = true },
                                label = { Text(searchQuery, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { vm.setSearchQuery("") }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(12.dp))
                                    }
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        SmallFloatingActionButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FloatingActionButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Discover settings")
                        }
                    }
                }
                                if (showSearch) {
                                    SearchBar(
                                        inputField = {
                                            SearchBarDefaults.InputField(
                                                query = searchText,
                                                onQueryChange = { searchText = it },
                                                onSearch = {
                                                    val trimmed = it.trim()
                                                    searchText = trimmed
                                                    vm.setSearchQuery(trimmed)
                                                    vm.clearTagSuggestions()
                                                    showSearch = false
                                                },
                                                expanded = true,
                                                onExpandedChange = { expanded ->
                                                    if (!expanded) {
                                                        vm.clearTagSuggestions()
                                                        showSearch = false
                                                    }
                                                },
                                                placeholder = { Text("e.g. blue_hair 1girl") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                trailingIcon = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (searchText.isNotBlank()) {
                                                            IconButton(onClick = { vm.pinCurrentSearch() }) {
                                                                Icon(Icons.Default.PushPin, contentDescription = "Pin search", modifier = Modifier.size(20.dp))
                                                            }
                                                        }
                                                        if (searchText.isNotEmpty()) {
                                                            IconButton(onClick = { searchText = "" }) {
                                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.focusRequester(searchFocusRequester)
                                            )
                                        },
                                        expanded = true,
                                        onExpandedChange = { expanded ->
                                            if (!expanded) {
                                                vm.clearTagSuggestions()
                                                showSearch = false
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "Use spaces between tags — e.g. \"blue_hair 1girl\" (use underscores within multi-word tags). Wallhaven also supports keyword search.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (tagSuggestions.isNotEmpty()) {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    contentPadding = PaddingValues(vertical = 2.dp)
                                                ) {
                                                    items(tagSuggestions, key = { it }) { tag ->
                                                        SuggestionChip(
                                                            onClick = {
                                                                val completed = completeQuery(searchText, tag)
                                                                searchText = completed
                                                                vm.setSearchQuery(completed)
                                                                vm.clearTagSuggestions()
                                                            },
                                                            label = { Text(tag) }
                                                        )
                                                    }
                                                }
                                            }
                                            val searchTokens = searchText.split(" ").filter { it.isNotBlank() }
                                            if (danbooruEnabled && searchTokens.size > 2) {
                                                Text(
                                                    "⚠️ Danbooru limits most accounts to 2 tags — extra tags may be ignored.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            val activeTokens = searchQuery.split(" ").filter { it.isNotBlank() }
                                            if (activeTokens.isNotEmpty()) {
                                                Text(
                                                    "Active filters — tap to remove:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    activeTokens.forEach { token ->
                                                        InputChip(
                                                            selected = false,
                                                            onClick = {
                                                                val updated = activeTokens.filter { it != token }.joinToString(" ")
                                                                searchText = updated
                                                                vm.setSearchQuery(updated)
                                                            },
                                                            label = { Text(token, style = MaterialTheme.typography.labelSmall) },
                                                            trailingIcon = {
                                                                Icon(
                                                                    Icons.Default.Close,
                                                                    contentDescription = "Remove",
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            if (pinnedSearches.isNotEmpty()) {
                                                Text(
                                                    "Pinned searches:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    pinnedSearches.forEach { pinned ->
                                                        InputChip(
                                                            selected = searchQuery == pinned,
                                                            onClick = {
                                                                searchText = pinned
                                                                vm.setSearchQuery(pinned)
                                                                vm.clearTagSuggestions()
                                                                showSearch = false
                                                            },
                                                            label = { Text(pinned, style = MaterialTheme.typography.labelSmall) },
                                                            trailingIcon = {
                                                                IconButton(
                                                                    onClick = { vm.unpinSearch(pinned) },
                                                                    modifier = Modifier.size(18.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Close,
                                                                        contentDescription = "Unpin",
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
            } // closes if (!noSources)
            selectedItem?.let { selected ->
                        val startIndex = gridItems.indexOfFirst { it.id == selected.id && it.source == selected.source }.coerceAtLeast(0)
                        WallpaperDetailOverlay(
                            items = gridItems,
                            startIndex = startIndex,
                            onPageChanged = { vm.selectItem(it) },
                            selectedListName = lists.find { it.id == selectedListId }?.name,
                            lists = lists,
                            isDownloadingFn = { downloadingIds.contains(it.id) },
                            isSavingToGalleryFn = { downloadingIds.contains("gallery:${it.id}") },
                            onSkip = { w -> vm.skip(w, closeViewer = false) },
                            onUndoSkip = { vm.undo() },
                            onAddToList = { w, list -> onAddToList(w, list) },
                            onDownloadToRotation = { w -> vm.downloadToRotation(w) },
                            onSaveToGallery = { w -> vm.saveToGallery(w) },
                            onSetWallpaper = { w -> vm.setWallpaperDirectly(w) },
                            onBlock = { w ->
                                vm.blockImage(w)
                            },
                            onReport = { w ->
                                reportingWallpaper = w
                                showReportSheet = true
                            },
                            nsfwMode = nsfwMode,
                            onTagSearch = { tag ->
                                vm.searchByTag(tag)
                                vm.selectItem(null)
                            },
                            onAddTagToSearch = { tag ->
                                vm.addTagToSearch(tag)
                                vm.selectItem(null)
                            },
                            onAddTagToTier = { tag, tier, isNsfw ->
                                vm.addTagToTier(tag, tier, isNsfw)
                            },
                            onDismiss = { vm.selectItem(null) },
                            loadingMore = loadingMore,
                            endReached = endReached,
                            onLoadMore = { vm.loadMore() },
                            savedListIdsFn = { w -> savedListIds["${w.source}:${w.id}"].orEmpty() },
                            onToggleInList = { w, list -> vm.toggleInList(list.id, w) },
                            canFoldPair = vm.canFoldPair,
                            foldPairOuter = foldPairOuter,
                            foldPairBusy = foldPairBusy,
                            onPickFoldOuter = { vm.pickFoldOuter(it) },
                            onSaveFoldPair = { o, i -> vm.saveFoldPair(o, i) },
                            lockedHiddenCount = lockedHiddenCount,
                            onUnlockLists = {
                                (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
                                    BiometricHelper.authenticate(
                                        activity = activity,
                                        title = "Unlock collections",
                                        onSuccess = { vm.unlockLockedLists() }
                                    )
                                }
                            },
                        )
                    }

            if (showHandsFree) {
                HandsFreeOverlay(
                    items = gridItems,
                    intervalSecs = handsFreeInterval,
                    onIntervalChange = { vm.setHandsFreeInterval(it) },
                    onLoadMore = { vm.loadMore() },
                    onDismiss = { showHandsFree = false }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
}

@Composable
private fun LowResOrShimmer(url: String?) {
    if (url == null) {
        ShimmerBox(Modifier.fillMaxSize())
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = { ShimmerBox(Modifier.fillMaxSize()) },
        error = { ShimmerBox(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun DiscoverThumbnailGridItem(
    wallpaper: BrainrotWallpaper,
    isDownloading: Boolean,
    isSaved: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    videoPreviewMode: com.chrisalvis.rotato.data.VideoPreviewMode,
    isVisible: Boolean,
    nsfwBlurEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    var revealed by rememberNsfwRevealed(wallpaper.id)
    val isBlurred = wallpaper.isNsfw && nsfwBlurEnabled && !revealed
    val hasStaticThumb = wallpaper.thumbUrl.isNotBlank() && !MediaType.isVideoUrl(wallpaper.thumbUrl)
    // No usable static thumbnail from the API for a video post — fall back to decoding a frame
    // straight from the video file itself (coil-video's VideoFrameDecoder, registered app-wide).
    val imageUrl = wallpaper.thumbUrl.ifBlank { wallpaper.sampleUrl.ifBlank { wallpaper.fullUrl } }
        .let { if (wallpaper.isVideo && !hasStaticThumb) wallpaper.fullUrl else it }
    val previewSlot = wallpaper.isVideo && !isBlurred &&
        rememberVideoPreviewSlot(enabled = isVisible && videoPreviewMode == com.chrisalvis.rotato.data.VideoPreviewMode.AUTOPLAY)
    val showStaticThumb = wallpaper.isVideo && videoPreviewMode != com.chrisalvis.rotato.data.VideoPreviewMode.OFF

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .pointerInput(wallpaper.id, selectionMode, isSelected, isBlurred) {
                detectTapGestures(
                    onTap = { if (isBlurred) revealed = true else onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        if (previewSlot) {
            VideoPlayerView(
                url = wallpaper.fullUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else if (showStaticThumb || !wallpaper.isVideo) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .crossfade(true)
                    .size(256, 256)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().nsfwContentBlur(wallpaper.isNsfw, nsfwBlurEnabled, revealed),
                loading = { ShimmerBox(Modifier.fillMaxSize()) },
                error = {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = "Couldn't load image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }

        NsfwBlurLayer(wallpaper.isNsfw, nsfwBlurEnabled, revealed, compact = true)

        if (isSelected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(sourceColor(wallpaper.source).copy(alpha = 0.88f), MaterialTheme.shapes.small)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                sourceDisplayName(wallpaper.source),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }

        if (wallpaper.isVideo) {
            if (previewSlot) {
                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription = "Video (muted preview)",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp)
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

        if (isSaved) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = "Saved",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(if (selectionMode) Alignment.TopStart else Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
            )
        }

        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(
                        if (isSelected) Color.White else Color.Black.copy(alpha = 0.35f),
                        CircleShape
                    )
            )
        }

        if (isDownloading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun DiscoverGridItem(
    wallpaper: BrainrotWallpaper,
    isDownloading: Boolean,
    isSaved: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    videoPreviewMode: com.chrisalvis.rotato.data.VideoPreviewMode,
    isVisible: Boolean,
    nsfwBlurEnabled: Boolean,
    dataSaver: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val ratio = parseAspectRatio(wallpaper.resolution)
        .coerceIn(0.25f, 4f)
        .let { if (it == 16f / 9f && wallpaper.resolution.isBlank()) 0.75f else it }
    val context = LocalContext.current
    var revealed by rememberNsfwRevealed(wallpaper.id)
    val isBlurred = wallpaper.isNsfw && nsfwBlurEnabled && !revealed

    // Fall back to fullUrl if sampleUrl fails (e.g. 404 on Danbooru restricted posts)
    var useFullUrl by remember(wallpaper.id) { mutableStateOf(false) }
    val gridUrl = if (dataSaver) wallpaper.dataSaverUrl else wallpaper.gridUrl
    val lowResUrl = if (dataSaver) null else wallpaper.lowResPreviewUrl
    val imageUrl = (if (useFullUrl || gridUrl.isBlank()) wallpaper.fullUrl else gridUrl)
        .ifBlank { null }
    val hasStaticThumb = wallpaper.thumbUrl.isNotBlank() && !MediaType.isVideoUrl(wallpaper.thumbUrl)
    val previewSlot = wallpaper.isVideo && !isBlurred &&
        rememberVideoPreviewSlot(enabled = isVisible && videoPreviewMode == com.chrisalvis.rotato.data.VideoPreviewMode.AUTOPLAY)
    val showStaticThumb = wallpaper.isVideo && videoPreviewMode != com.chrisalvis.rotato.data.VideoPreviewMode.OFF && hasStaticThumb

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.medium)
            .pointerInput(wallpaper.id, selectionMode, isSelected, isBlurred) {
                detectTapGestures(
                    onTap = { if (isBlurred) revealed = true else onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        if (previewSlot) {
            VideoPlayerView(
                url = wallpaper.fullUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else if (showStaticThumb) {
            AsyncImage(
                model = wallpaper.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().nsfwContentBlur(wallpaper.isNsfw, nsfwBlurEnabled, revealed)
            )
        } else if (wallpaper.isVideo && videoPreviewMode != com.chrisalvis.rotato.data.VideoPreviewMode.OFF) {
            // No usable static thumbnail from the API — decode a frame straight from the
            // video file itself (coil-video's VideoFrameDecoder, registered app-wide).
            AsyncImage(
                model = wallpaper.fullUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().nsfwContentBlur(wallpaper.isNsfw, nsfwBlurEnabled, revealed)
            )
        } else if (wallpaper.isVideo) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().nsfwContentBlur(wallpaper.isNsfw, nsfwBlurEnabled, revealed),
                // The small preview appears almost at once and the sharper sample replaces it,
                // instead of a tile sitting on a shimmer until the large image arrives.
                loading = { LowResOrShimmer(lowResUrl) },
                error = {
                    if (!useFullUrl && gridUrl.isNotBlank() && wallpaper.fullUrl != gridUrl) {
                        LaunchedEffect(Unit) { useFullUrl = true }
                        LowResOrShimmer(lowResUrl)
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = "Couldn't load image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            )
        }

        NsfwBlurLayer(wallpaper.isNsfw, nsfwBlurEnabled, revealed)

        if (isSelected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        }

        // Source color badge (and Fold badge on foldables) — bottom-left
        val foldCanvas = rememberFoldCanvas()
        val foldFriendly = foldCanvas != null && remember(wallpaper.resolution, foldCanvas) {
            val (w, h) = wallpaper.resolution.split('x').mapNotNull { it.trim().toIntOrNull() }.let {
                if (it.size == 2) it[0] to it[1] else 0 to 0
            }
            com.chrisalvis.rotato.data.isFoldFriendly(w, h, foldCanvas)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(sourceColor(wallpaper.source).copy(alpha = 0.88f), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    sourceDisplayName(wallpaper.source),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            if (foldFriendly) FoldBadge()
        }

        if (wallpaper.isVideo) {
            if (previewSlot) {
                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription = "Video (muted preview)",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(18.dp)
                )
            } else {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }
        }

        if (isSaved) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = "Saved",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(if (selectionMode) Alignment.TopStart else Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
            )
        }

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

        if (isDownloading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun WallpaperDetailOverlay(
    items: List<BrainrotWallpaper>,
    startIndex: Int,
    onPageChanged: (BrainrotWallpaper) -> Unit,
    selectedListName: String?,
    lists: List<LocalList>,
    isDownloadingFn: (BrainrotWallpaper) -> Boolean,
    isSavingToGalleryFn: (BrainrotWallpaper) -> Boolean,
    onSkip: (BrainrotWallpaper) -> Unit,
    onUndoSkip: () -> BrainrotWallpaper?,
    onAddToList: (BrainrotWallpaper, LocalList?) -> Unit,
    onDownloadToRotation: (BrainrotWallpaper) -> Unit,
    onSaveToGallery: (BrainrotWallpaper) -> Unit,
    onSetWallpaper: (BrainrotWallpaper) -> Unit,
    onBlock: (BrainrotWallpaper) -> Unit,
    onReport: (BrainrotWallpaper) -> Unit,
    nsfwMode: Boolean,
    onTagSearch: (String) -> Unit,
    onAddTagToSearch: (String) -> Unit,
    onAddTagToTier: (String, TagTier, Boolean) -> Unit,
    onDismiss: () -> Unit,
    loadingMore: Boolean,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    savedListIdsFn: (BrainrotWallpaper) -> Set<String>,
    onToggleInList: (BrainrotWallpaper, LocalList) -> Unit,
    lockedHiddenCount: Int,
    onUnlockLists: () -> Unit,
    canFoldPair: Boolean,
    foldPairOuter: BrainrotWallpaper?,
    foldPairBusy: Boolean,
    onPickFoldOuter: (BrainrotWallpaper?) -> Unit,
    onSaveFoldPair: (BrainrotWallpaper, BrainrotWallpaper) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var tagActionTag by remember { mutableStateOf<String?>(null) }
    // Zoom happens in place on the current page; one tap hides/shows everything around the image.
    val zoom = remember { ZoomState() }
    var showFoldPreview by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    val zoomed = zoom.zoomed
    var showInfoExpanded by remember { mutableStateOf(false) }
    var dockHinted by rememberSaveable { mutableStateOf(false) }
    // Skip moves on to the next image; this offers a few seconds to take it back.
    var undoVisible by remember { mutableStateOf(false) }
    var undoTick by remember { mutableIntStateOf(0) }
    var restoreTo by remember { mutableStateOf<BrainrotWallpaper?>(null) }
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 150.dp.toPx() } }

    // Guard: if all items are removed (last image blocked/skipped), close the overlay cleanly.
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val safeStart = startIndex.coerceIn(0, items.size - 1)
    val pagerState = rememberPagerState(initialPage = safeStart) { items.size }
    // Key on items so the closure captures the latest list after any item is removed.
    val wallpaper by remember(items) { derivedStateOf { items.getOrNull(pagerState.currentPage) ?: items[safeStart] } }

    // Fire whenever wallpaper changes — covers both manual swipes and list-shrink advances.
    LaunchedEffect(wallpaper) {
        zoom.reset()
        showInfoExpanded = false
        onPageChanged(wallpaper)
    }

    // Swiping toward the end of what's loaded fetches the next page, same as scrolling the grid,
    // so the viewer doesn't dead-end at "65 / 65". Re-evaluated as items arrive, so a page that
    // lands while the user is already on the last image keeps the chain going.
    val latestItems by rememberUpdatedState(items)
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage >= latestItems.size - 5 }
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
    LaunchedEffect(items.size, loadingMore, endReached) {
        if (!loadingMore && !endReached && pagerState.currentPage >= items.size - 5) onLoadMore()
    }

    // The pager already composes one page either side; warm the disk cache two ahead as well so
    // a quick run of swipes lands on images that are ready rather than on loading states.
    LaunchedEffect(pagerState.currentPage, items) {
        val loader = coil.Coil.imageLoader(context)
        listOf(pagerState.currentPage + 2).forEach { i ->
            val next = items.getOrNull(i) ?: return@forEach
            if (next.isVideo) return@forEach
            val url = next.sampleUrl.takeIf { it.isNotBlank() && !MediaType.isVideoUrl(it) } ?: next.fullUrl.ifBlank { next.thumbUrl }
            loader.enqueue(
                coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .diskCacheKey(url)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                    .build()
            )
        }
    }

    // Unfolded (or any wide window): a rail of collections on the right for one-tap saving.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val foldCanvas = rememberFoldCanvas()

    val offsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    // Measured height of the bottom info/actions panel below — the video's seek bar reads this
    // so it renders above the panel instead of sitting underneath its (touchable) rows.
    var bottomPanelHeight by remember { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY.value
                alpha = 1f - (offsetY.value / 600f).coerceIn(0f, 1f)
            }
            .pointerInput(isDismissing, zoomed) {
                if (isDismissing || zoomed) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val downChange = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        var totalDy = 0f
                        var totalDx = 0f
                        var dragConfirmed = false

                        trackGesture@ while (true) {
                            val eventI = awaitPointerEvent(PointerEventPass.Initial)
                            val changeI = eventI.changes.firstOrNull { it.id == downChange.id }
                            if (changeI == null || !changeI.pressed) break

                            // Two-finger pinch: leave it to the page's in-place zoom
                            if (eventI.changes.count { it.pressed } >= 2) break@trackGesture

                            totalDy += (changeI.position - changeI.previousPosition).y
                            totalDx += (changeI.position - changeI.previousPosition).x
                            val totalDxAbs = kotlin.math.abs(totalDx)

                            // Upward swipe opens the details panel
                            if (-totalDy > viewConfiguration.touchSlop * 2 && -totalDy > totalDxAbs) {
                                changeI.consume()
                                showInfoExpanded = true
                                break@trackGesture
                            }
                            if (totalDy > viewConfiguration.touchSlop && totalDy > totalDxAbs) {
                                // Downward vertical drag confirmed — consume before children see it
                                changeI.consume()
                                dragConfirmed = true
                                coroutineScope.launch { offsetY.snapTo(totalDy.coerceAtLeast(0f)) }
                                break@trackGesture
                            }

                            // Horizontal movement — let the pager handle it, don't intercept
                            if (totalDxAbs > viewConfiguration.touchSlop && totalDxAbs > kotlin.math.abs(totalDy)) {
                                break@trackGesture
                            }
                        }

                        if (dragConfirmed) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == downChange.id }
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

                        // Drain remaining events until all pointers are up
                        while (true) {
                            val evt = awaitPointerEvent(PointerEventPass.Final)
                            if (evt.changes.all { !it.pressed }) break
                        }
                    }
                }
            }
    ) {
        // Black scrim that fades only when dismissing downward
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - (offsetY.value / 600f).coerceIn(0f, 1f)))
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.aboveTabletopFold().fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            // Placeholder is whatever the grid tile already has in memory, so the pager never
            // opens on a black screen.
            val placeholderKey = item.gridUrl
            val fullImageUrl = item.fullUrl.ifBlank { item.thumbUrl }
            // The screen-sized sample is plenty here (and far faster than the original); the
            // double-tap zoom view still loads the full resolution.
            val pagerImageUrl = item.sampleUrl.takeIf { it.isNotBlank() && !MediaType.isVideoUrl(it) } ?: fullImageUrl

            // beyondViewportPageCount keeps neighbor pages mounted for smooth swiping — only the
            // page actually on screen should stream video, or we'd silently buffer clips no one is watching.
            if (item.isVideo && page == pagerState.currentPage) {
                VideoPlayerView(
                    url = fullImageUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scaleFactor = 1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                        },
                    allowTapToToggle = true,
                    showMuteButton = true,
                    showSeekBar = true,
                    allowDoubleTapSeek = true,
                    seekBarBottomInset = bottomPanelHeight
                )
            } else if (item.isVideo) {
                val posterUrl = item.thumbUrl.ifBlank { item.sampleUrl }.takeUnless { it.isBlank() || MediaType.isVideoUrl(it) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scaleFactor = 1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                            scaleX = scaleFactor
                            scaleY = scaleFactor
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
                val isCurrent = page == pagerState.currentPage
                FullscreenImage(
                    // Zoomed in, swap to the original resolution; the screen-sized sample stays
                    // up (unblurred) until it arrives, so zooming never drops to a black screen.
                    url = if (isCurrent && zoomed) fullImageUrl else pagerImageUrl,
                    placeholderKey = if (isCurrent && zoomed) pagerImageUrl else placeholderKey,
                    blurPlaceholder = !(isCurrent && zoomed),
                    indicatorTopPadding = 56.dp,
                    modifier = Modifier.onSizeChanged { zoom.size = it },
                    imageModifier = Modifier
                        .zoomTransform(zoom, active = isCurrent) {
                            1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                        }
                        .zoomGestures(
                            zoom,
                            active = isCurrent,
                            onTap = { chromeVisible = !chromeVisible },
                            onLongPress = {
                                    val url = item.pageUrl.ifBlank { item.fullUrl }
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Wallpaper URL", url)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                                }
                        )
                )
            }
        } // end HorizontalPager

        if (wide && chromeVisible) {
            ListRail(
                lists = lists,
                savedIn = savedListIdsFn(wallpaper),
                lockedHiddenCount = lockedHiddenCount,
                onToggle = { list -> onToggleInList(wallpaper, list) },
                onCreateList = { onAddToList(wallpaper, null) },
                onUnlock = onUnlockLists,
                // Floats over the image (which keeps the full width) and stays clear of the
                // info pills at the top and the tags/actions at the bottom.
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, bottom = bottomPanelHeight + 8.dp, end = 12.dp)
                    .widthIn(max = 220.dp)
            )
        }

        val isDownloading = isDownloadingFn(wallpaper)
        val isSavingToGallery = isSavingToGalleryFn(wallpaper)
        val savedIn = savedListIdsFn(wallpaper)
        val shareWallpaper = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, wallpaper.pageUrl.takeIf { it.startsWith("http") } ?: wallpaper.fullUrl)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share"))
        }
        val foldPairState = if (canFoldPair && !wallpaper.isVideo) {
            val outer = foldPairOuter
            when {
                foldPairBusy -> FoldPairState.Busy
                outer == null -> FoldPairState.Idle
                outer.id == wallpaper.id && outer.source == wallpaper.source -> FoldPairState.PickedHere
                else -> FoldPairState.ReadyToPair
            }
        } else null
        val onFoldPair = {
            when (foldPairState) {
                FoldPairState.Idle -> {
                    onPickFoldOuter(wallpaper)
                    Toast.makeText(context, "Cover screen picked. Swipe to an image for the big screen.", Toast.LENGTH_LONG).show()
                }
                FoldPairState.PickedHere -> onPickFoldOuter(null)
                FoldPairState.ReadyToPair -> showFoldPreview = true
                else -> Unit
            }
        }
        // One set of actions, shown in the dock and again at the top of the details sheet, so
        // the expanded view reads as the dock grown taller rather than a different screen.
        val actions: @Composable (Modifier) -> Unit = { mod ->
            ViewerActions(
                wallpaper = wallpaper,
                saved = savedIn.isNotEmpty(),
                isDownloading = isDownloading,
                foldPairState = foldPairState,
                lists = lists,
                savedIn = savedIn,
                lockedHiddenCount = lockedHiddenCount,
                onToggleInList = { list -> onToggleInList(wallpaper, list) },
                onCreateList = { onAddToList(wallpaper, null) },
                onUnlockLists = onUnlockLists,
                onSetWallpaper = { onSetWallpaper(wallpaper) },
                onLibrary = { onDownloadToRotation(wallpaper) },
                onFoldPair = onFoldPair,
                onShare = shareWallpaper,
                onSkip = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    showInfoExpanded = false
                    onSkip(wallpaper)
                    undoVisible = true
                    undoTick++
                },
                modifier = mod,
            )
        }

        // Facts about the image float as glass pills over the image itself; no scrim, so the
        // picture runs edge to edge. Tapping them opens the details.
        AnimatedVisibility(
            visible = chromeVisible && !zoomed,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            ImageInfoPills(
                onClick = { showInfoExpanded = true },
                wallpaper = wallpaper,
                foldCanvas = foldCanvas,
                position = if (items.size > 1) "${pagerState.currentPage + 1} / ${items.size}" +
                    (if (loadingMore && pagerState.currentPage >= items.size - 3) " · loading more…" else "") else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // The dock: a floating glass card holding the image's name and the main actions, with a
        // handle that says it can be pulled up. Pulling it (or swiping up anywhere) opens the
        // same card at full height with everything else.
        AnimatedVisibility(
            visible = chromeVisible && !zoomed,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 },
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
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                ViewerDock(
                    title = wallpaper.tags.firstOrNull()?.replace('_', ' ') ?: sourceDisplayName(wallpaper.source),
                    subtitle = wallpaper.tags.drop(1).let { rest ->
                        if (rest.isEmpty()) null
                        else rest.take(3).joinToString(" · ") { it.replace('_', ' ') } + if (rest.size > 3) " · +${rest.size - 3}" else ""
                    },
                    onExpand = { showInfoExpanded = true },
                    actions = actions,
                    hint = !dockHinted,
                    onHinted = { dockHinted = true },
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                )
            }
        }

        LaunchedEffect(undoTick) {
            if (undoTick == 0) return@LaunchedEffect
            kotlinx.coroutines.delay(4_000)
            undoVisible = false
        }
        // After an undo, land back on the restored image.
        LaunchedEffect(items, restoreTo) {
            val target = restoreTo ?: return@LaunchedEffect
            val idx = items.indexOfFirst { it.id == target.id && it.source == target.source }
            if (idx >= 0) {
                pagerState.scrollToPage(idx)
                restoreTo = null
            }
        }
        AnimatedVisibility(
            visible = undoVisible && !zoomed,
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (chromeVisible) bottomPanelHeight + 4.dp else 24.dp)
                .navigationBarsPadding()
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                onClick = {
                    undoVisible = false
                    restoreTo = onUndoSkip()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Skipped", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        tagActionTag?.let { tag ->
            TagActionSheet(
                tag = tag,
                nsfwMode = nsfwMode,
                onSearch = { onTagSearch(tag); tagActionTag = null },
                onAddToSearch = { onAddTagToSearch(tag); tagActionTag = null },
                onAddToTier = { tier, isNsfw -> onAddTagToTier(tag, tier, isNsfw); tagActionTag = null },
                onDismiss = { tagActionTag = null },
            )
        }

        val pairOuter = foldPairOuter
        if (showFoldPreview && pairOuter != null) {
            FoldPairPreviewDialog(
                outer = pairOuter,
                inner = wallpaper,
                foldCanvas = foldCanvas,
                onConfirm = { o, i -> showFoldPreview = false; onSaveFoldPair(o, i) },
                onDismiss = { showFoldPreview = false },
            )
        }

        if (showInfoExpanded) {
            ImageDetailsSheet(
                wallpaper = wallpaper,
                foldCanvas = foldCanvas,
                actions = actions,
                lists = lists,
                savedIn = savedIn,
                lockedHiddenCount = lockedHiddenCount,
                onToggleInList = { list -> onToggleInList(wallpaper, list) },
                onCreateList = { onAddToList(wallpaper, null) },
                onUnlockLists = onUnlockLists,
                isSavingToGallery = isSavingToGallery,
                onSaveToGallery = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onSaveToGallery(wallpaper)
                },
                onReport = { showInfoExpanded = false; onReport(wallpaper) },
                onBlock = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    showInfoExpanded = false
                    onBlock(wallpaper)
                },
                onTagSearch = { tag -> showInfoExpanded = false; onTagSearch(tag) },
                onTagActions = { tag -> tagActionTag = tag },
                onDismiss = { showInfoExpanded = false },
            )
        }

        // Back while zoomed zooms out instead of closing the viewer.
        BackHandler(enabled = zoomed) { zoom.reset() }
    }
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "shimmerOffset"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surface
    Box(modifier = modifier.background(
        brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(offset * 1000f - 500f, 0f),
            end = Offset(offset * 1000f + 500f, 0f)
        )
    ))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagActionSheet(
    tag: String,
    nsfwMode: Boolean,
    onSearch: () -> Unit,
    onAddToSearch: () -> Unit,
    onAddToTier: (TagTier, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                tag.replace('_', ' '),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) { Text("Search") }
                OutlinedButton(onClick = onAddToSearch, modifier = Modifier.weight(1f)) { Text("Add to search") }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SFW Tier", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TagTier.LOVE to "♥ Love", TagTier.LIKE to "↑ Like", TagTier.DISLIKE to "↓ Dislike", TagTier.NEVER to "✕ Never").forEach { (tier, label) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onAddToTier(tier, false)
                                android.widget.Toast.makeText(context, "${tag.replace('_',' ')} → $label (SFW)", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (nsfwMode) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NSFW Tier", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TagTier.LOVE to "♥ Love", TagTier.LIKE to "↑ Like", TagTier.DISLIKE to "↓ Dislike", TagTier.NEVER to "✕ Never").forEach { (tier, label) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onAddToTier(tier, true)
                                    android.widget.Toast.makeText(context, "${tag.replace('_',' ')} → $label (NSFW)", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sourceColor(source: String): Color = when (source.lowercase()) {
    "wallhaven"  -> Color(0xFF1565C0)
    "konachan"   -> Color(0xFF6A1B9A)
    "danbooru"   -> Color(0xFF2E7D32)
    "rule34"     -> Color(0xFFAD1457)
    "zerochan"   -> Color(0xFF00838F)
    "gelbooru"   -> Color(0xFF880E4F)
    "safebooru"  -> Color(0xFF1B5E20)
    "reddit"     -> Color(0xFFBF360C)
    "yandere"    -> Color(0xFF311B92)
    "e621"       -> Color(0xFF4A148C)
    else          -> Color(0xFF37474F)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DiscoverSettingsSheetContent(
    nsfwMode: Boolean,
    filters: BrainrotFilters,
    lists: List<LocalList>,
    selectedListId: String?,
    interestAlignEnabled: Boolean,
    interestProfiles: List<InterestProfile>,
    onSelectList: (String) -> Unit,
    onSetNsfwMode: (Boolean) -> Unit,
    onSetMinResolution: (MinResolution) -> Unit,
    onSetAspectRatio: (AspectRatio) -> Unit,
    isFoldable: Boolean = false,
    onSetFoldFriendly: (Boolean) -> Unit = {},
    forYouEnabled: Boolean = true,
    onSetForYou: (Boolean) -> Unit = {},
    onResetLearned: () -> Unit = {},
    onSetUseMalFilter: (Boolean) -> Unit,
    onSetInterestAlign: (Boolean) -> Unit,
    onToggleProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var listExpanded by remember { mutableStateOf(false) }
    var resExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Discover Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (lists.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Save to list", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownMenuBox(expanded = listExpanded, onExpandedChange = { listExpanded = it }) {
                    OutlinedTextField(
                        value = lists.find { it.id == selectedListId }?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(listExpanded) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = listExpanded, onDismissRequest = { listExpanded = false }) {
                        lists.forEach { list ->
                            DropdownMenuItem(
                                text = { Text(list.name) },
                                onClick = { onSelectList(list.id); listExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        if (!LocalNsfwHidden.current) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("NSFW", style = MaterialTheme.typography.bodyMedium)
                Text("Enable adult content", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = nsfwMode, onCheckedChange = onSetNsfwMode)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("MAL filter", style = MaterialTheme.typography.bodyMedium)
                Text("Limit results to your anime list", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = filters.useMalFilter, onCheckedChange = onSetUseMalFilter)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Align with interests", style = MaterialTheme.typography.bodyMedium)
                Text("Boost & filter by your Taste profiles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = interestAlignEnabled, onCheckedChange = onSetInterestAlign)
        }

        if (interestAlignEnabled && interestProfiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Active profiles", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    interestProfiles.forEach { profile ->
                        FilterChip(
                            selected = profile.isActive,
                            onClick = { onToggleProfile(profile.id) },
                            label = { Text(profile.name) }
                        )
                    }
                }
                Text(
                    if (interestProfiles.none { it.isActive }) "No profiles selected · using tag tiers"
                    else "${interestProfiles.count { it.isActive }} profile(s) active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else if (interestAlignEnabled) {
            Text(
                "No profiles yet · using tag tiers. Create profiles in the Taste tab.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        SettingsToggleRow(
            title = "For you",
            subtitle = "Order the feed by what you save, set and skip, favouring sharp images" +
                if (isFoldable) " that fill both screens" else "",
            checked = forYouEnabled,
            onCheckedChange = onSetForYou,
        )
        if (forYouEnabled) {
            TextButton(onClick = onResetLearned) { Text("Forget what Discover has learned") }
        }

        if (isFoldable) {
            SettingsToggleRow(
                title = "Fold-friendly only",
                subtitle = "Large images shaped to fill both the outer and inner screen",
                checked = filters.isFoldFriendly,
                onCheckedChange = onSetFoldFriendly,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Min resolution", style = MaterialTheme.typography.labelMedium)
            ExposedDropdownMenuBox(expanded = resExpanded, onExpandedChange = { resExpanded = it }) {
                OutlinedTextField(
                    value = filters.minResolution.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(resExpanded) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = resExpanded, onDismissRequest = { resExpanded = false }) {
                    MinResolution.entries.filter { it != MinResolution.MY_PHONE }.forEach { res ->
                        DropdownMenuItem(
                            text = { Text(res.label) },
                            onClick = { onSetMinResolution(res); resExpanded = false }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Aspect ratio", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatio.entries.forEach { ratio ->
                    if (ratio == AspectRatio.MY_PHONE) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text("Shows images shaped for your screen (both screens on a foldable). Your phone crops them to fit at set time.") } },
                            state = rememberTooltipState()
                        ) {
                            FilterChip(
                                selected = filters.aspectRatio == ratio,
                                onClick = { onSetAspectRatio(ratio) },
                                label = { Text(ratio.label) }
                            )
                        }
                    } else {
                        FilterChip(
                            selected = filters.aspectRatio == ratio,
                            onClick = { onSetAspectRatio(ratio) },
                            label = { Text(ratio.label) }
                        )
                    }
                }
            }
        }

        Text(
            "Tag blacklist → use the Taste tab to set tags to Never tier.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )

        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Done")
        }
    }
}

@Composable
private fun NoSourcesState(onNavigateToSources: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Welcome to Rotato!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Get started in 3 steps:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OnboardingStep("1", "Go to Sources and enable one or more image sources")
                OnboardingStep("2", "Tap any image to open it, zoom in, or save to a collection")
                OnboardingStep("3", "Go to Library → start rotation to have wallpapers change automatically")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onNavigateToSources,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get Started — Manage Sources")
            }
        }
    }
}

@Composable
private fun OnboardingStep(number: String, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NoResultsState(
    searchQuery: String,
    noResultsReason: NoResultsReason?,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    onDisableWifiOnly: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val subtitle = when (noResultsReason) {
        NoResultsReason.WIFI_ONLY -> "Wi-Fi only mode is on — connect to Wi-Fi or disable it in Settings."
        NoResultsReason.SEARCH_EMPTY -> "No wallpapers match your search. Try clearing the query or changing filters."
        NoResultsReason.EXHAUSTED -> "All sources have been exhausted. Try refreshing or adjusting your filters."
        null -> "Check Settings → Sources for red health indicators, or enable more sources."
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        ) {
            Icon(Icons.Default.ImageNotSupported, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text("No wallpapers found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (searchQuery.isNotBlank()) {
                Text(
                    "Searching for: \"$searchQuery\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = onClearSearch) { Text("Clear search") }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            if (noResultsReason == NoResultsReason.WIFI_ONLY) {
                Button(onClick = onDisableWifiOnly) { Text("Disable Wi-Fi only") }
            } else {
                Button(onClick = onRetry) { Text("Retry") }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedIconButton(onClick = onOpenSearch, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            OutlinedIconButton(onClick = onOpenSettings, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}

@Composable
private fun ReportSheetContent(
    wallpaperUrl: String,
    onReport: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        "Inappropriate / explicit content",
        "Not wallpaper material",
        "Copyright / stolen content",
        "Other"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Report image",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "Select a reason. The image will be hidden immediately and a report will be sent via email.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        reasons.forEach { reason ->
            OutlinedButton(
                onClick = { onReport(reason) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(reason)
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Cancel")
        }
    }
}


/**
 * Collections as pills down the right edge of the unfolded viewer. Tapping one saves the current
 * image there (or takes it back out) without leaving it, so it can go into several at once.
 */
@Composable
private fun ListRail(
    lists: List<LocalList>,
    savedIn: Set<String>,
    lockedHiddenCount: Int,
    onToggle: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lists.forEach { list ->
            val saved = list.id in savedIn
            FilterChip(
                selected = saved,
                onClick = { onToggle(list) },
                label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        when {
                            saved -> Icons.Default.Check
                            list.isLocked -> Icons.Default.LockOpen
                            else -> Icons.Default.Add
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    labelColor = Color.White,
                    iconColor = Color.White.copy(alpha = 0.85f),
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = saved,
                    borderColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.height(40.dp)
            )
        }
        if (lists.isEmpty()) {
            AssistChip(
                onClick = onCreateList,
                label = { Text("New collection") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(50),
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.55f), labelColor = Color.White, leadingIconContentColor = Color.White),
                modifier = Modifier.height(40.dp)
            )
        }
        if (lockedHiddenCount > 0) {
            FilledTonalButton(
                onClick = onUnlock,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Unlock $lockedHiddenCount", maxLines = 1)
            }
        }
    }
}


/**
 * Facts about the image as pills: where it's from, its size class and exact resolution, shape,
 * whether it covers both Fold screens, and the position in the feed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageInfoPills(
    onClick: () -> Unit,
    wallpaper: BrainrotWallpaper,
    foldCanvas: com.chrisalvis.rotato.data.WallpaperCanvas?,
    position: String?,
    modifier: Modifier = Modifier,
) {
    val (w, h) = remember(wallpaper.resolution) {
        wallpaper.resolution.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }
            .let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
    }
    val foldFriendly = foldCanvas != null && w > 0 && com.chrisalvis.rotato.data.isFoldFriendly(w, h, foldCanvas)
    FlowRow(
        modifier = modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InfoPill(sourceDisplayName(wallpaper.source), container = sourceColor(wallpaper.source).copy(alpha = 0.9f), bold = true)
        if (w > 0 && h > 0) {
            val longSide = maxOf(w, h)
            val sizeClass = when {
                longSide >= 7680 -> "8K"
                longSide >= 3840 -> "4K"
                longSide >= 2560 -> "QHD"
                longSide >= 1920 -> "FHD"
                longSide >= 1280 -> "HD"
                else -> "Low res"
            }
            InfoPill(sizeClass, bold = true)
            InfoPill("$w × $h")
            val ratio = w.toFloat() / h
            InfoPill(
                when {
                    ratio > 1.15f -> "Landscape"
                    ratio < 0.87f -> "Portrait"
                    else -> "Square"
                }
            )
        }
        if (foldFriendly) {
            InfoPill("Fold-friendly", icon = Icons.Default.Smartphone, container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f), content = MaterialTheme.colorScheme.onTertiary)
        }
        if (wallpaper.isVideo) InfoPill("Video", icon = Icons.Default.PlayArrow)
        if (wallpaper.isNsfw) InfoPill("NSFW", container = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
        if (position != null) InfoPill(position)
    }
}



/** The main actions as labelled buttons; used by the dock and by the details sheet. */
@Composable
private fun ViewerActions(
    wallpaper: BrainrotWallpaper,
    saved: Boolean,
    isDownloading: Boolean,
    foldPairState: FoldPairState?,
    lists: List<LocalList>,
    savedIn: Set<String>,
    lockedHiddenCount: Int,
    onToggleInList: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    onUnlockLists: () -> Unit,
    onSetWallpaper: () -> Unit,
    onLibrary: () -> Unit,
    onFoldPair: () -> Unit,
    onShare: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            var menu by remember { mutableStateOf(false) }
            DockAction(
                icon = if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (saved) "Saved" else "Save",
                highlighted = true,
                onClick = { if (lists.isEmpty() && lockedHiddenCount == 0) onCreateList() else menu = true }
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                lists.forEach { list ->
                    val inList = list.id in savedIn
                    DropdownMenuItem(
                        text = { Text(list.name) },
                        leadingIcon = {
                            Icon(if (inList) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                        },
                        onClick = { onToggleInList(list) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("New collection…") },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = { menu = false; onCreateList() }
                )
                if (lockedHiddenCount > 0) {
                    DropdownMenuItem(
                        text = { Text("Unlock $lockedHiddenCount locked") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        onClick = { menu = false; onUnlockLists() }
                    )
                }
            }
        }
        if (!wallpaper.isVideo) {
            DockAction(Icons.Outlined.Wallpaper, "Set", onClick = onSetWallpaper, modifier = Modifier.weight(1f))
            DockAction(
                Icons.Default.Download,
                if (isDownloading) "Adding…" else "Library",
                enabled = !isDownloading,
                onClick = onLibrary,
                modifier = Modifier.weight(1f)
            )
        }
        if (foldPairState != null) {
            DockAction(
                Icons.Default.Smartphone,
                when (foldPairState) {
                    FoldPairState.Busy -> "Pairing…"
                    FoldPairState.Idle -> if (LocalConfiguration.current.screenWidthDp < 400) "Pair" else "Fold pair"
                    FoldPairState.PickedHere -> "Cover ✓"
                    FoldPairState.ReadyToPair -> "Pair"
                },
                highlighted = foldPairState == FoldPairState.PickedHere || foldPairState == FoldPairState.ReadyToPair,
                enabled = foldPairState != FoldPairState.Busy,
                onClick = onFoldPair,
                modifier = Modifier.weight(1f)
            )
        }
        DockAction(Icons.Default.Share, "Share", onClick = onShare, modifier = Modifier.weight(1f))
        DockAction(Icons.Default.SkipNext, "Skip", onClick = onSkip, modifier = Modifier.weight(1f))
    }
}

/** Collections as toggle pills, the same idea as the unfolded rail, for the details sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionPills(
    lists: List<LocalList>,
    savedIn: Set<String>,
    lockedHiddenCount: Int,
    onToggle: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    onUnlock: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lists.forEach { list ->
            val saved = list.id in savedIn
            FilterChip(
                selected = saved,
                onClick = { onToggle(list) },
                label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        when {
                            saved -> Icons.Default.Check
                            list.isLocked -> Icons.Default.LockOpen
                            else -> Icons.Default.Add
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(50)
            )
        }
        AssistChip(
            onClick = onCreateList,
            label = { Text("New") },
            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(50)
        )
        if (lockedHiddenCount > 0) {
            AssistChip(
                onClick = onUnlock,
                label = { Text("Unlock $lockedHiddenCount") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * The dock at full height: the image's facts, the same actions, every collection, the post, all
 * tags, and the rarer actions (gallery, report, never show again). Opened by pulling up the dock,
 * swiping up on the image, or tapping the info pills.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ImageDetailsSheet(
    wallpaper: BrainrotWallpaper,
    foldCanvas: com.chrisalvis.rotato.data.WallpaperCanvas?,
    actions: @Composable (Modifier) -> Unit,
    lists: List<LocalList>,
    savedIn: Set<String>,
    lockedHiddenCount: Int,
    onToggleInList: (LocalList) -> Unit,
    onCreateList: () -> Unit,
    onUnlockLists: () -> Unit,
    isSavingToGallery: Boolean,
    onSaveToGallery: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onTagSearch: (String) -> Unit,
    onTagActions: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                    wallpaper.tags.firstOrNull()?.replace('_', ' ') ?: sourceDisplayName(wallpaper.source),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                ImageInfoPills(onClick = {}, wallpaper = wallpaper, foldCanvas = foldCanvas, position = null)
            }

            actions(Modifier.fillMaxWidth())

            SheetSection("Collections") {
                CollectionPills(
                    lists = lists,
                    savedIn = savedIn,
                    lockedHiddenCount = lockedHiddenCount,
                    onToggle = onToggleInList,
                    onCreateList = onCreateList,
                    onUnlock = onUnlockLists,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val page = wallpaper.pageUrl.takeIf { it.startsWith("http") }
                if (page != null) {
                    FilledTonalButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(page))) }
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open post")
                    }
                }
                FilledTonalButton(onClick = onSaveToGallery, enabled = !isSavingToGallery) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSavingToGallery) "Saving…" else "Save to gallery")
                }
            }

            if (wallpaper.tags.isNotEmpty()) {
                SheetSection("Tags · tap to search, hold for options") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        wallpaper.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .combinedClickable(
                                        onClick = { onTagSearch(tag) },
                                        onLongClick = { onTagActions(tag) }
                                    )
                            ) {
                                Text(
                                    tag.replace('_', ' '),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val danger = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                TextButton(onClick = onReport, colors = danger) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Report")
                }
                TextButton(onClick = onBlock, colors = danger) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Never show again")
                }
            }
        }
    }
}

/**
 * Shows the two images the way they'll appear: the cover image on a tall narrow screen and the
 * inner image on the near-square big screen, sized from this phone's real panels when known.
 */
@Composable
private fun FoldPairPreviewDialog(
    outer: BrainrotWallpaper,
    inner: BrainrotWallpaper,
    foldCanvas: com.chrisalvis.rotato.data.WallpaperCanvas?,
    onConfirm: (BrainrotWallpaper, BrainrotWallpaper) -> Unit,
    onDismiss: () -> Unit,
) {
    var swapped by remember { mutableStateOf(false) }
    val cover = if (swapped) inner else outer
    val big = if (swapped) outer else inner
    val screens = foldCanvas?.screens.orEmpty().sortedBy { it.width.toFloat() / it.height }
    val coverRatio = screens.firstOrNull()?.let { it.width.toFloat() / it.height } ?: 0.45f
    val bigRatio = screens.lastOrNull()?.let { it.width.toFloat() / it.height } ?: 0.9f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fold pair preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                ) {
                    listOf(Triple(cover, coverRatio, "Folded"), Triple(big, bigRatio, "Unfolded")).forEach { (wp, ratio, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                            AsyncImage(
                                model = wp.gridUrl,
                                contentDescription = label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(ratio)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            )
                            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                Text(
                    "Both images are saved to your Library and set now. Rotation keeps them together.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(cover, big) }) { Text("Set pair") } },
        dismissButton = {
            Row {
                TextButton(onClick = { swapped = !swapped }) { Text("Swap") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
