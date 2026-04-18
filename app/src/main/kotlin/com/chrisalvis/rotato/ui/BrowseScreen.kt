package com.chrisalvis.rotato.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.BrowseWallpaper
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.RemoteList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    feed: FeedConfig,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val browseViewModel: BrowseViewModel = viewModel(
        key = feed.id,
        factory = BrowseViewModelFactory(context.applicationContext as Application, feed)
    )

    val lists by browseViewModel.lists.collectAsStateWithLifecycle()
    val listsLoading by browseViewModel.listsLoading.collectAsStateWithLifecycle()
    val listsError by browseViewModel.listsError.collectAsStateWithLifecycle()
    val selectedList by browseViewModel.selectedList.collectAsStateWithLifecycle()
    val wallpapers by browseViewModel.wallpapers.collectAsStateWithLifecycle()
    val wallpapersLoading by browseViewModel.wallpapersLoading.collectAsStateWithLifecycle()
    val hasMore by browseViewModel.hasMore.collectAsStateWithLifecycle()
    val inRotation by browseViewModel.inRotation.collectAsStateWithLifecycle()
    val downloading by browseViewModel.downloading.collectAsStateWithLifecycle()

    BackHandler(enabled = selectedList != null) {
        browseViewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedList != null) browseViewModel.clearSelection()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(selectedList?.name ?: feed.name, fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (selectedList == null) {
            ListPickerContent(
                lists = lists,
                loading = listsLoading,
                error = listsError,
                onRetry = { browseViewModel.loadLists() },
                onSelectList = { browseViewModel.selectList(it) },
                modifier = Modifier.padding(padding)
            )
        } else {
            WallpaperGridContent(
                wallpapers = wallpapers,
                loading = wallpapersLoading,
                hasMore = hasMore,
                inRotation = inRotation,
                downloading = downloading,
                onLoadMore = { browseViewModel.loadMoreIfNeeded() },
                onToggle = { browseViewModel.toggleRotation(it) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ListPickerContent(
    lists: List<RemoteList>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelectList: (RemoteList) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
        lists.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No lists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lists, key = { it.id }) { list ->
                ListItem(
                    headlineContent = { Text(list.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("${list.count} wallpapers") },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
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
    loading: Boolean,
    hasMore: Boolean,
    inRotation: Set<String>,
    downloading: Set<String>,
    onLoadMore: () -> Unit,
    onToggle: (BrowseWallpaper) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !loading) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(wallpapers, key = { it.sourceId }) { wp ->
            val sanitizedKey = wp.sourceId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val isInRotation = inRotation.contains(sanitizedKey)
            val isDownloading = downloading.contains(wp.sourceId)
            WallpaperThumbnail(
                wallpaper = wp,
                isInRotation = isInRotation,
                isDownloading = isDownloading,
                onClick = { if (!isInRotation && !isDownloading) onToggle(wp) }
            )
        }

        if (loading) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumbnail(
    wallpaper: BrowseWallpaper,
    isInRotation: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !isInRotation && !isDownloading, onClick = onClick)
    ) {
        AsyncImage(
            model = wallpaper.thumbUrl.ifBlank { wallpaper.fullUrl },
            contentDescription = wallpaper.animeTitle.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        when {
            isInRotation -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "In rotation",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
            isDownloading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
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
