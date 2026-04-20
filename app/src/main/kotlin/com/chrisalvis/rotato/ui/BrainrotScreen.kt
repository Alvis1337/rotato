package com.chrisalvis.rotato.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainrotScreen(
    externalViewModel: BrainrotViewModel? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSources: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: BrainrotViewModel = externalViewModel ?: viewModel()

    val current by vm.current.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val noResults by vm.noResults.collectAsStateWithLifecycle()
    val noSources by vm.noSources.collectAsStateWithLifecycle()
    val sessionSaved by vm.sessionSaved.collectAsStateWithLifecycle()
    val sessionSkipped by vm.sessionSkipped.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val selectedListId by vm.selectedListId.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val nsfwMode by vm.nsfwMode.collectAsStateWithLifecycle()
    val brainrotFilters by vm.brainrotFilters.collectAsStateWithLifecycle()
    val nextWallpaper by vm.nextWallpaper.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val downloadingIds by vm.downloadingIds.collectAsStateWithLifecycle()

    var showInfo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    var showCreateListDialog by remember { mutableStateOf(false) }
    if (showCreateListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
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
                TextButton(onClick = { if (newListName.isNotBlank()) { vm.createList(newListName); showCreateListDialog = false } }, enabled = newListName.isNotBlank()) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { showCreateListDialog = false }) { Text("Cancel") } }
        )
    }

    val onAddToList: () -> Unit = {
        when {
            lists.isEmpty() -> showCreateListDialog = true
            else -> vm.addToList(selectedListId ?: lists.first().id)
        }
    }

    if (showSettings) {
        DiscoverSettingsDialog(
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            noSources -> NoSourcesState(onNavigateToSources = onNavigateToSources)
            noResults -> NoResultsState(onRetry = { vm.retry() })
            loading && current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            current != null -> {
                // Always render a background image behind the swipe card.
                // Prefer the preloaded next card; fall back to the current card
                // (already in Coil memory cache) so the background is never black.
                val bgWallpaper = nextWallpaper?.takeIf { it.id != current!!.id } ?: current!!
                val bgUrl = bgWallpaper.fullUrl.ifBlank { bgWallpaper.thumbUrl }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bgUrl)
                        .memoryCacheKey(bgUrl)
                        .diskCacheKey(bgUrl)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                key(current!!.id) {
                    FullScreenSwipeCard(
                        wallpaper = current!!,
                        loading = loading,
                        busy = busy,
                        showInfo = showInfo,
                        sessionSaved = sessionSaved,
                        sessionSkipped = sessionSkipped,
                        selectedListName = lists.find { it.id == selectedListId }?.name,
                        searchQuery = searchQuery,
                        isDownloading = downloadingIds.contains(current!!.id),
                        onToggleInfo = { showInfo = !showInfo },
                        onSkip = { vm.skip() },
                        onAddToList = onAddToList,
                        onDownloadToRotation = { vm.downloadToRotation(current!!) },
                        onImageTap = { showZoom = true },
                        onShare = {
                            val url = current!!.pageUrl.ifBlank { current!!.fullUrl }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share wallpaper"))
                        },
                        onOpenSettings = { showSettings = true },
                        onOpenSearch = { showSearch = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (showSearch) {
                    SearchDialog(
                        current = searchQuery,
                        onSearch = { vm.setSearchQuery(it); showSearch = false },
                        onDismiss = { showSearch = false }
                    )
                }
                if (showZoom) {
                    ZoomImageDialog(
                        wallpaper = current!!,
                        onDismiss = { showZoom = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenSwipeCard(
    wallpaper: BrainrotWallpaper,
    loading: Boolean,
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val xOffset = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = remember(config) { with(density) { config.screenWidthDp.dp.toPx() } }
    val threshold = screenWidthPx * 0.35f

    val saveAlpha = (xOffset.value / threshold).coerceIn(0f, 1f)
    val skipAlpha = (-xOffset.value / threshold).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        // ── Swipeable image layer (only this translates) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = xOffset.value
                    rotationZ = (xOffset.value / screenWidthPx) * 6f
                }
                // Black background fills the letterbox gaps from ContentScale.Fit
                // so the background layer never bleeds through
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
                                        xOffset.animateTo(screenWidthPx * 2, spring(stiffness = 200f))
                                        onAddToList()
                                    }
                                    xOffset.value < -threshold -> {
                                        isAnimating = true
                                        xOffset.animateTo(-screenWidthPx * 2, spring(stiffness = 200f))
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

            // Small non-blocking spinner — only shown when image genuinely isn't cached
            if (imageLoading) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }

            // Save overlay (right swipe)
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

            // Skip overlay (left swipe)
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
        } // end inner swipeable Box

        // Top gradient overlay — session stats (fixed)
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
                verticalAlignment = Alignment.CenterVertically,
                ) {
                if (sessionSaved > 0) StatChip("📌 $sessionSaved")
                if (sessionSkipped > 0) {
                    Spacer(Modifier.width(8.dp))
                    StatChip("✕ $sessionSkipped")
                }
            }
        }

        // Bottom gradient overlay — source/title + action buttons
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
            // Source chip + resolution
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

            // Anime/title from tags
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

            // Expanded tags row
            if (showInfo && wallpaper.tags.size > 3) {
                Text(
                    wallpaper.tags.drop(3).take(8).joinToString("  ·  ") { it.replace('_', ' ') },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip
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

                // Info
                OutlinedIconButton(
                    onClick = onToggleInfo,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (showInfo) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = if (showInfo) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Download to rotation pool
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

                // Save (primary action)
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

                // Share
                OutlinedIconButton(
                    onClick = onShare,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                // Search
                OutlinedIconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (searchQuery.isNotBlank() && searchQuery != "anime") MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = if (searchQuery.isNotBlank() && searchQuery != "anime") MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(18.dp))
                }

                // Settings
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
            if (current.isNotBlank() && current != "anime") {
                TextButton(onClick = { onSearch("") }) { Text("Clear") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverSettingsDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                // Min resolution
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

                // Aspect ratio chips
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
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
private fun NoResultsState(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.ImageNotSupported, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text("No wallpapers found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Try enabling more sources in Settings → Manage Sources, or adjust your search query",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) { Text("Retry") }
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
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
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
