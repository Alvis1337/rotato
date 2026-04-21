package com.chrisalvis.rotato.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val downloadingIds by vm.downloadingIds.collectAsStateWithLifecycle()
    val lastTriedSources by vm.lastTriedSources.collectAsStateWithLifecycle()

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

    fun onAddToList(wp: BrainrotWallpaper) {
        when {
            lists.isEmpty() -> {
                pendingAddWallpaper = wp
                showCreateListDialog = true
            }
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

    if (showSearch) {
        SearchDialog(
            current = searchQuery,
            onSearch = { vm.setSearchQuery(it); showSearch = false },
            onDismiss = { showSearch = false }
        )
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
                onSelectList = { vm.setSelectedList(it) },
                onSetNsfwMode = { vm.setNsfwMode(it) },
                onSetMinResolution = { vm.setMinResolution(it) },
                onSetAspectRatio = { vm.setAspectRatio(it) },
                onDismiss = { showSettings = false }
            )
        }
    }

    // Fullscreen detail modal for selected item
    if (selectedItem != null) {
        val wp = selectedItem!!
        var showZoom by remember { mutableStateOf(false) }
        Dialog(
            onDismissRequest = { vm.selectItem(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                FullScreenSwipeCard(
                    wallpaper = wp,
                    busy = false,
                    showInfo = false,
                    sessionSaved = sessionSaved,
                    sessionSkipped = sessionSkipped,
                    selectedListName = lists.find { it.id == selectedListId }?.name,
                    searchQuery = searchQuery,
                    isDownloading = downloadingIds.contains(wp.id),
                    onToggleInfo = { /* handled internally */ },
                    onSkip = { vm.skip(wp) },
                    onAddToList = { onAddToList(wp) },
                    onDownloadToRotation = { vm.downloadToRotation(wp) },
                    onImageTap = { showZoom = true },
                    onShare = {
                        val url = wp.pageUrl.ifBlank { wp.fullUrl }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share wallpaper"))
                    },
                    onOpenSettings = { vm.selectItem(null); showSettings = true },
                    onOpenSearch = { vm.selectItem(null); showSearch = true },
                    onClose = { vm.selectItem(null) },
                    modifier = Modifier.fillMaxSize()
                )
                if (showZoom) {
                    ZoomImageDialog(wallpaper = wp, onDismiss = { showZoom = false })
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            noSources -> NoSourcesState(onNavigateToSources = onNavigateToSources)
            noResults -> NoResultsState(
                triedSources = lastTriedSources,
                searchQuery = searchQuery,
                onRetry = { vm.retry() },
                onClearSearch = { vm.setSearchQuery("") },
                onOpenSearch = { showSearch = true },
                onOpenSettings = { showSettings = true }
            )
            loading && gridItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                val gridState = rememberLazyStaggeredGridState()

                // Infinite scroll trigger: load more when near the bottom
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val total = gridState.layoutInfo.totalItemsCount
                        lastVisible >= total - 4
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore && !endReached) vm.loadMore()
                }

                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { vm.loadMore(reset = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    // Session stats header chip row
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
                } // end PullToRefreshBox

                // Bottom action bar: search + settings FAB row
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
private fun DiscoverGridItem(
    wallpaper: BrainrotWallpaper,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    // Always use the resolution-derived ratio so the card never resizes on scroll-back.
    // Falls back to 3:4 portrait if resolution is unknown.
    val ratio = parseAspectRatio(wallpaper.resolution)
        .coerceIn(0.25f, 4f)
        .let { if (it == 16f / 9f && wallpaper.resolution.isBlank()) 0.75f else it }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.medium)
    ) {
        // Never use thumbUrl in the grid — booru thumbs are square-cropped (150px)
        // and will appear heavily zoomed when Cropped into a non-square card.
        val imageUrl = wallpaper.sampleUrl.ifBlank { wallpaper.fullUrl }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl.ifBlank { null })
                .memoryCacheKey(imageUrl.ifBlank { null })
                .diskCacheKey(imageUrl.ifBlank { null })
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() })
                }
        )

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
private fun FullScreenSwipeCard(
    wallpaper: BrainrotWallpaper,
    busy: Boolean,
    showInfo: Boolean,
    sessionSaved: Int,
    sessionSkipped: Int,
    selectedListName: String?,
    searchQuery: String,
    isDownloading: Boolean,
    onToggleInfo: () -> Unit,
    onSkip: () -> Unit,
    onAddToList: () -> Unit,
    onDownloadToRotation: () -> Unit,
    onImageTap: () -> Unit,
    onShare: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val xOffset = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var showInfoLocal by remember { mutableStateOf(showInfo) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = remember(config) { with(density) { config.screenWidthDp.dp.toPx() } }
    val threshold = screenWidthPx * 0.35f

    val saveAlpha = (xOffset.value / threshold).coerceIn(0f, 1f)
    val skipAlpha = (-xOffset.value / threshold).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = xOffset.value
                    rotationZ = (xOffset.value / screenWidthPx) * 6f
                }
                .background(Color.Black)
                .pointerInput(isAnimating) {
                    if (isAnimating) return@pointerInput
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch { xOffset.snapTo(xOffset.value + dragAmount.x) }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    xOffset.value > threshold -> {
                                        isAnimating = true
                                        xOffset.animateTo(screenWidthPx * 2, tween(durationMillis = 180, easing = FastOutLinearInEasing))
                                        isAnimating = false
                                        onAddToList()
                                    }
                                    xOffset.value < -threshold -> {
                                        isAnimating = true
                                        xOffset.animateTo(-screenWidthPx * 2, tween(durationMillis = 180, easing = FastOutLinearInEasing))
                                        isAnimating = false
                                        onSkip()
                                    }
                                    else -> xOffset.animateTo(0f, spring(stiffness = 400f, dampingRatio = 0.65f))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                xOffset.animateTo(0f, spring(stiffness = 400f, dampingRatio = 0.65f))
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onImageTap() })
                }
        ) {
            val imageUrl = wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl }
            var imageLoading by remember { mutableStateOf(false) }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .memoryCacheKey(imageUrl)
                    .diskCacheKey(imageUrl)
                    .placeholderMemoryCacheKey(wallpaper.thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { imageLoading = it is AsyncImagePainter.State.Loading },
                modifier = Modifier.fillMaxSize()
            )

            if (imageLoading) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp).align(Alignment.Center)
                )
            }

            if (saveAlpha > 0.02f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20).copy(alpha = saveAlpha * 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
                        Text(
                            if (selectedListName != null) "Save to \"$selectedListName\"" else "Save to List",
                            color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }

            if (skipAlpha > 0.02f) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF7F0000).copy(alpha = skipAlpha * 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
                        Text("Skip", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }

        // Close button top-right
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Top stats
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

            if (showInfoLocal && wallpaper.tags.size > 3) {
                Text(
                    wallpaper.tags.drop(3).take(8).joinToString("  ·  ") { it.replace('_', ' ') },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(
                    onClick = {
                        if (!isAnimating) {
                            isAnimating = true
                            coroutineScope.launch {
                                xOffset.animateTo(-screenWidthPx * 2, spring(stiffness = 200f))
                                onSkip()
                            }
                        }
                    },
                    enabled = !isAnimating,
                    modifier = Modifier.size(52.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Skip", tint = Color.White, modifier = Modifier.size(22.dp))
                }

                OutlinedIconButton(
                    onClick = { showInfoLocal = !showInfoLocal },
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (showInfoLocal) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = if (showInfoLocal) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedIconButton(
                    onClick = onDownloadToRotation,
                    enabled = !isDownloading,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.Download, contentDescription = "Add to rotation", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                FilledIconButton(
                    onClick = {
                        if (!isAnimating) {
                            isAnimating = true
                            coroutineScope.launch {
                                xOffset.animateTo(screenWidthPx * 2, spring(stiffness = 200f))
                                onAddToList()
                            }
                        }
                    },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isAnimating
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = "Save to list", modifier = Modifier.size(32.dp))
                }

                OutlinedIconButton(
                    onClick = onShare,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                OutlinedIconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(18.dp))
                }

                OutlinedIconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(52.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
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

@Composable
private fun SearchDialog(current: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.ifBlank { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Discover") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Tags / keywords") },
                placeholder = { Text("e.g. anime, landscape, 4k") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSearch(text.trim()) }) { Text("Search") }
        },
        dismissButton = {
            if (current.isNotBlank()) {
                TextButton(onClick = { onSearch("") }) { Text("Clear") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverSettingsSheetContent(
    nsfwMode: Boolean,
    filters: BrainrotFilters,
    lists: List<LocalList>,
    selectedListId: String?,
    onSelectList: (String) -> Unit,
    onSetNsfwMode: (Boolean) -> Unit,
    onSetMinResolution: (MinResolution) -> Unit,
    onSetAspectRatio: (AspectRatio) -> Unit,
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AspectRatio.entries.forEach { ratio ->
                    FilterChip(
                        selected = filters.aspectRatio == ratio,
                        onClick = { onSetAspectRatio(ratio) },
                        label = { Text(ratio.label) }
                    )
                }
            }
        }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
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
    triedSources: List<String>,
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
            if (triedSources.isNotEmpty()) {
                Text(
                    "Tried: ${triedSources.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
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
