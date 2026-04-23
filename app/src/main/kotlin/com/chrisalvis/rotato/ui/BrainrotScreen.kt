package com.chrisalvis.rotato.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
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
import coil.request.ImageRequest
import com.chrisalvis.rotato.data.AspectRatio
import com.chrisalvis.rotato.data.BrainrotFilters
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.LocalList
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.chrisalvis.rotato.data.MinResolution
import kotlinx.coroutines.launch

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

private fun Int.toComposeColor(): Color = Color((this and 0xFFFFFF) or 0xFF000000.toInt())

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
    val endReached by vm.endReached.collectAsStateWithLifecycle()
    val noResults by vm.noResults.collectAsStateWithLifecycle()
    val noSources by vm.noSources.collectAsStateWithLifecycle()
    val sessionSaved by vm.sessionSaved.collectAsStateWithLifecycle()
    val sessionSkipped by vm.sessionSkipped.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val selectedListId by vm.selectedListId.collectAsStateWithLifecycle()
    val nsfwMode by vm.nsfwMode.collectAsStateWithLifecycle()
    val brainrotFilters by vm.brainrotFilters.collectAsStateWithLifecycle()
    val globalBlacklist by vm.globalBlacklist.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val downloadingIds by vm.downloadingIds.collectAsStateWithLifecycle()

    val gridState = rememberLazyStaggeredGridState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 5 } }
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !loading && !loadingMore && !endReached && selectedItem == null) vm.loadMore()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarHostState) {
        vm.skipEvent.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Wallpaper skipped",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo()
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    LaunchedEffect(showSearch) { if (showSearch) searchText = searchQuery.ifBlank { "" } }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var pendingAddWallpaper by remember { mutableStateOf<BrainrotWallpaper?>(null) }

    if (showCreateListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false; pendingAddWallpaper = null },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            vm.createList(newListName)
                            showCreateListDialog = false
                        }
                    },
                    enabled = newListName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false; pendingAddWallpaper = null }) {
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

    // React to newly created list by completing pending add
    LaunchedEffect(selectedListId) {
        val pending = pendingAddWallpaper
        if (pending != null && selectedListId != null && lists.isNotEmpty()) {
            vm.addToList(selectedListId!!, pending)
            pendingAddWallpaper = null
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
                globalBlacklist = globalBlacklist,
                lists = lists,
                selectedListId = selectedListId,
                onSelectList = { vm.setSelectedList(it) },
                onSetNsfwMode = { vm.setNsfwMode(it) },
                onSetMinResolution = { vm.setMinResolution(it) },
                onSetAspectRatio = { vm.setAspectRatio(it) },
                onSetGlobalBlacklist = { vm.setGlobalBlacklist(it) },
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

    SharedTransitionLayout {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                noSources -> NoSourcesState(onNavigateToSources = onNavigateToSources)
                noResults -> NoResultsState(
                    searchQuery = searchQuery,
                    onRetry = { vm.retry() },
                    onClearSearch = { vm.setSearchQuery("") },
                    onOpenSearch = { showSearch = true },
                    onOpenSettings = { showSettings = true }
                )
                loading && gridItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                else -> {
                    AnimatedContent(
                        targetState = selectedItem,
                        transitionSpec = { fadeIn(tween(0)) togetherWith fadeOut(tween(0)) },
                        label = "discover"
                    ) { selected ->
                        if (selected == null) {
                            val pullRefreshState = rememberPullToRefreshState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                PullToRefreshBox(
                                    isRefreshing = loading,
                                    onRefresh = { vm.loadMore(reset = true) },
                                    state = pullRefreshState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Fixed(2),
                                        state = gridState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalItemSpacing = 8.dp
                                    ) {
                                        if (sessionSaved > 0 || sessionSkipped > 0) {
                                            item(span = StaggeredGridItemSpan.FullLine) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (sessionSaved > 0) StatChip("📌 $sessionSaved")
                                                    if (sessionSkipped > 0) StatChip("✕ $sessionSkipped")
                                                }
                                            }
                                        }
                                        items(gridItems, key = { "${it.source}:${it.id}" }) { wp ->
                                            DiscoverGridItem(
                                                wallpaper = wp,
                                                isDownloading = downloadingIds.contains(wp.id),
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = this@AnimatedContent,
                                                onClick = { vm.selectItem(wp) }
                                            )
                                        }
                                        if (loadingMore) {
                                            item(span = StaggeredGridItemSpan.FullLine) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                                // Bottom action bar
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
                                            onClick = { coroutineScope.launch { gridState.scrollToItem(0) } },
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
                                                containerColor = Color.Black.copy(alpha = 0.65f),
                                                labelColor = Color.White,
                                                leadingIconContentColor = Color.White,
                                                trailingIconContentColor = Color.White
                                            ),
                                            border = AssistChipDefaults.assistChipBorder(
                                                enabled = true,
                                                borderColor = Color.White.copy(alpha = 0.25f)
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
                                if (showSearch) {
                                    SearchBar(
                                        inputField = {
                                            SearchBarDefaults.InputField(
                                                query = searchText,
                                                onQueryChange = { searchText = it },
                                                onSearch = {
                                                    vm.setSearchQuery(it.trim())
                                                    showSearch = false
                                                },
                                                expanded = true,
                                                onExpandedChange = { expanded ->
                                                    if (!expanded) showSearch = false
                                                },
                                                placeholder = { Text("e.g. anime 1girl landscape") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                trailingIcon = {
                                                    if (searchText.isNotEmpty()) {
                                                        IconButton(onClick = { searchText = "" }) {
                                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                        expanded = true,
                                        onExpandedChange = { expanded ->
                                            if (!expanded) showSearch = false
                                        },
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "Use spaces to combine tags — e.g. \"anime 1girl\" searches for posts tagged with both. Wallhaven supports keyword search too.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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
                                        }
                                    }
                                }
                            }
                        } else {
                            WallpaperDetailOverlay(
                                wallpaper = selected,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                sessionSaved = sessionSaved,
                                sessionSkipped = sessionSkipped,
                                selectedListName = lists.find { it.id == selectedListId }?.name,
                                lists = lists,
                                isDownloading = downloadingIds.contains(selected.id),
                                isSavingToGallery = downloadingIds.contains("gallery:${selected.id}"),
                                onSkip = { vm.skip(selected) },
                                onAddToList = { list -> onAddToList(selected, list) },
                                onDownloadToRotation = { vm.downloadToRotation(selected) },
                                onSaveToGallery = { vm.saveToGallery(selected) },
                                onShare = {
                                    val url = selected.pageUrl.ifBlank { selected.fullUrl }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share wallpaper"))
                                },
                                onReport = {
                                    reportingWallpaper = selected
                                    showReportSheet = true
                                },
                                onTagSearch = { tag ->
                                    val current = searchQuery
                                    val newQuery = if (current.isBlank()) tag else "$current $tag"
                                    vm.setSearchQuery(newQuery)
                                    vm.selectItem(null)
                                },
                                onDismiss = { vm.selectItem(null) }
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun DiscoverGridItem(
    wallpaper: BrainrotWallpaper,
    isDownloading: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit
) {
    val ratio = parseAspectRatio(wallpaper.resolution)
        .coerceIn(0.25f, 4f)
        .let { if (it == 16f / 9f && wallpaper.resolution.isBlank()) 0.75f else it }
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.medium)
    ) {
        val imageUrl = wallpaper.sampleUrl.ifBlank { wallpaper.fullUrl }
        val imageKey = "wp-image-${wallpaper.source}:${wallpaper.id}"
        with(sharedTransitionScope) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl.ifBlank { null })
                    .memoryCacheKey(imageUrl.ifBlank { null })
                    .diskCacheKey(imageUrl.ifBlank { null })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = imageKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> tween(350) }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onClick() })
                    }
            )
        }

        // Source color badge — bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(sourceColor(wallpaper.source).copy(alpha = 0.88f), MaterialTheme.shapes.small)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                wallpaper.source.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
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
    wallpaper: BrainrotWallpaper,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sessionSaved: Int,
    sessionSkipped: Int,
    selectedListName: String?,
    lists: List<LocalList>,
    isDownloading: Boolean,
    isSavingToGallery: Boolean,
    onSkip: () -> Unit,
    onAddToList: (LocalList?) -> Unit,
    onDownloadToRotation: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onTagSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var showZoom by remember { mutableStateOf(false) }
    var showInfoExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Smooth swipe-to-dismiss with proper animation
    val offsetY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY.value
                alpha = (1f - (offsetY.value / 600f).coerceIn(0f, 1f))
            }
            .pointerInput(isDismissing, showZoom) {
                if (isDismissing || showZoom) return@pointerInput
                // Use PointerEventPass.Initial so the outer Box intercepts drag events
                // BEFORE children (AsyncImage's detectTapGestures) can consume them.
                // Initial pass flows parent→child, giving parent priority over gestures.
                awaitPointerEventScope {
                    while (true) {
                        // Wait for a fresh finger-down in the Initial pass
                        val downChange = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        val startX = downChange.position.x
                        var totalDy = 0f
                        var dragConfirmed = false

                        // Track movement: confirm vertical drag before children see it
                        trackGesture@ while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == downChange.id }
                            if (change == null || !change.pressed) break

                            totalDy += (change.position - change.previousPosition).y
                            val totalDx = kotlin.math.abs(change.position.x - startX)

                            if (totalDy > viewConfiguration.touchSlop && totalDy > totalDx) {
                                // Downward vertical drag confirmed — consume to cancel children
                                change.consume()
                                dragConfirmed = true
                                coroutineScope.launch {
                                    offsetY.snapTo(totalDy.coerceAtLeast(0f))
                                }
                                break@trackGesture
                            }
                            // Clearly horizontal or upward — not our gesture
                            if (totalDx > kotlin.math.abs(totalDy) + viewConfiguration.touchSlop) {
                                break@trackGesture
                            }
                        }

                        if (dragConfirmed) {
                            // Stay in Initial pass for the full drag — using drag() would
                            // switch to Main pass and get cancelled by child gesture consumers.
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
                                if (offsetY.value > 150f) {
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
        // Black overlay that fades as user swipes (shows discover grid behind)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (offsetY.value / 600f).coerceIn(0f, 1f))))
        )
        val imageKey = "wp-image-${wallpaper.source}:${wallpaper.id}"
        val placeholderKey = wallpaper.sampleUrl.ifBlank { wallpaper.thumbUrl }
        val fullImageUrl = wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl }

        with(sharedTransitionScope) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fullImageUrl)
                    .memoryCacheKey(fullImageUrl)
                    .diskCacheKey(fullImageUrl)
                    .placeholderMemoryCacheKey(placeholderKey)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scaleFactor = 1f - ((offsetY.value / 600f).coerceIn(0f, 1f)) * 0.3f
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                    }
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = imageKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> tween(350) }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { showZoom = true },
                            onLongPress = {
                                val url = wallpaper.pageUrl.ifBlank { wallpaper.fullUrl }
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Wallpaper URL", url)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures(
                            onGesture = { _, _, gestureZoom, _ ->
                                if (gestureZoom > 1.1f && !isDismissing) {
                                    showZoom = true
                                }
                            }
                        )
                    }
            )
        }

        // Top stats bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sessionSaved > 0) StatChip("📌 $sessionSaved")
                if (sessionSkipped > 0) {
                    Spacer(Modifier.width(8.dp))
                    StatChip("✕ $sessionSkipped")
                }
            }
        }

        // Bottom info + actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.5f),
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp, top = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = sourceColor(wallpaper.source).copy(alpha = 0.85f)
                ) {
                    Text(
                        wallpaper.source.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                if (wallpaper.resolution.isNotBlank()) {
                    Text(
                        wallpaper.resolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            val titleTags = wallpaper.tags
                .map { it.replace('_', ' ').split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } } }
                .take(3)
            if (titleTags.isNotEmpty()) {
                Text(
                    titleTags.joinToString("  ·  "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showInfoExpanded && wallpaper.tags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(wallpaper.tags) { tag ->
                        SuggestionChip(
                            onClick = { onTagSearch(tag) },
                            label = {
                                Text(
                                    tag.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                labelColor = Color.White
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = Color.White.copy(alpha = 0.25f),
                                disabledBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info button
                OutlinedIconButton(
                    onClick = { showInfoExpanded = !showInfoExpanded },
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (showInfoExpanded) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = if (showInfoExpanded) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Main action - Bookmark with list dropdown
                var showBookmarkMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    FilledIconButton(
                        onClick = { 
                            if (lists.isNotEmpty()) {
                                showBookmarkMenu = !showBookmarkMenu
                            } else {
                                onAddToList(null)
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Save to list", modifier = Modifier.size(28.dp))
                    }
                    
                    DropdownMenu(
                        expanded = showBookmarkMenu,
                        onDismissRequest = { showBookmarkMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (lists.isEmpty()) {
                            Text("  No lists yet  ", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text(list.name, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = { onAddToList(list); showBookmarkMenu = false },
                                    leadingIcon = { 
                                        if (!list.isLocked) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Download
                OutlinedIconButton(
                    onClick = onDownloadToRotation,
                    enabled = !isDownloading,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Download, contentDescription = "Rotation", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Save to gallery
                OutlinedIconButton(
                    onClick = onSaveToGallery,
                    enabled = !isSavingToGallery,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    if (isSavingToGallery) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Share + Report in overflow menu
                var showMore by remember { mutableStateOf(false) }
                Box {
                    OutlinedIconButton(
                        onClick = { showMore = !showMore },
                        modifier = Modifier.size(44.dp),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    
                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onShare(); showMore = false },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Report", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { onReport(); showMore = false },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { onSkip(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Skip", color = Color.White)
            }
        }

        if (showZoom) {
            ZoomImageDialog(wallpaper = wallpaper, onDismiss = { showZoom = false })
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun sourceColor(source: String): Color = when (source.lowercase()) {
    "wallhaven" -> Color(0xFF1565C0)
    "konachan"  -> Color(0xFF6A1B9A)
    "danbooru"  -> Color(0xFF2E7D32)
    else        -> Color(0xFF37474F)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverSettingsSheetContent(
    nsfwMode: Boolean,
    filters: BrainrotFilters,
    globalBlacklist: Set<String>,
    lists: List<LocalList>,
    selectedListId: String?,
    onSelectList: (String) -> Unit,
    onSetNsfwMode: (Boolean) -> Unit,
    onSetMinResolution: (MinResolution) -> Unit,
    onSetAspectRatio: (AspectRatio) -> Unit,
    onSetGlobalBlacklist: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var listExpanded by remember { mutableStateOf(false) }
    var resExpanded by remember { mutableStateOf(false) }
    var blacklistText by remember(globalBlacklist) { mutableStateOf(globalBlacklist.joinToString(", ")) }

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

        Row(
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
                    MinResolution.entries.forEach { res ->
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
                    FilterChip(
                        selected = filters.aspectRatio == ratio,
                        onClick = { onSetAspectRatio(ratio) },
                        label = { Text(ratio.label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tag blacklist", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = blacklistText,
                onValueChange = { blacklistText = it },
                label = { Text("Comma-separated tags") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
            )
            Text(
                "Wallpapers containing any of these tags will be hidden.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        TextButton(
            onClick = {
                val tags = blacklistText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                onSetGlobalBlacklist(tags)
                onDismiss()
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun NoSourcesState(onNavigateToSources: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text("No sources enabled", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Enable image sources in Settings → Manage Sources to start discovering wallpapers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Button(onClick = onNavigateToSources) { Text("Manage Sources") }
        }
    }
}

@Composable
private fun NoResultsState(
    searchQuery: String,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                "Check Settings → Sources for red health indicators, adjust your search query, or enable more sources.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) { Text("Retry") }
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
private fun ZoomImageDialog(
    wallpaper: BrainrotWallpaper,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        offset += panChange * scale
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
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else { scale = 2.5f }
                        }
                    )
                }
        ) {
            val imageUrl = wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(state = transformState)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
