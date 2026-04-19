package com.chrisalvis.rotato.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chrisalvis.rotato.data.BrainrotWallpaper
import com.chrisalvis.rotato.data.FeedConfig
import com.chrisalvis.rotato.data.RemoteList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainrotScreen(
    feed: FeedConfig,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: BrainrotViewModel = viewModel(
        key = feed.id,
        factory = BrainrotViewModelFactory(context.applicationContext as Application, feed)
    )

    val current by vm.current.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val noResults by vm.noResults.collectAsStateWithLifecycle()
    val sessionSaved by vm.sessionSaved.collectAsStateWithLifecycle()
    val sessionSkipped by vm.sessionSkipped.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val selectedListId by vm.selectedListId.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val discoverSettings by vm.discoverSettings.collectAsStateWithLifecycle()
    val settingsSaving by vm.settingsSaving.collectAsStateWithLifecycle()

    var showListPicker by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val onAddToList: () -> Unit = {
        when {
            lists.isEmpty() -> Toast.makeText(context, "No lists found — create one on the website first", Toast.LENGTH_LONG).show()
            lists.size == 1 -> vm.addToList(lists.first().id)
            else -> showListPicker = true
        }
    }

    if (showListPicker) {
        ListPickerDialog(
            lists = lists,
            selectedListId = selectedListId,
            onSelect = { listId ->
                showListPicker = false
                vm.addToList(listId)
            },
            onDismiss = { showListPicker = false }
        )
    }

    if (showSettings) {
        DiscoverSettingsDialog(
            settings = discoverSettings,
            saving = settingsSaving,
            onSave = { updated ->
                vm.saveSettings(updated)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Discover", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sessionSaved > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("📌 $sessionSaved", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (sessionSkipped > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("✕ $sessionSkipped", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Discover settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                noResults -> NoResultsState(onRetry = { vm.retry() })
                loading && current == null -> CircularProgressIndicator()
                current != null -> {
                    key(current!!.id) {
                        SwipeCard(
                            wallpaper = current!!,
                            loading = loading,
                            busy = busy,
                            showInfo = showInfo,
                            onToggleInfo = { showInfo = !showInfo },
                            onSkip = { vm.skip() },
                            onAddToList = onAddToList,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeCard(
    wallpaper: BrainrotWallpaper,
    loading: Boolean,
    busy: Boolean,
    showInfo: Boolean,
    onToggleInfo: () -> Unit,
    onSkip: () -> Unit,
    onAddToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val xOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = remember(config) { with(density) { config.screenWidthDp.dp.toPx() } }
    val threshold = screenWidthPx * 0.35f

    val saveAlpha = (xOffset.value / threshold).coerceIn(0f, 1f)
    val skipAlpha = (-xOffset.value / threshold).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        // Swipeable card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationX = xOffset.value
                    rotationZ = (xOffset.value / screenWidthPx) * 10f
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch { xOffset.snapTo(xOffset.value + dragAmount.x) }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    xOffset.value > threshold -> {
                                        xOffset.animateTo(screenWidthPx * 2, spring(stiffness = 200f))
                                        onAddToList()
                                        xOffset.snapTo(0f)
                                    }
                                    xOffset.value < -threshold -> {
                                        xOffset.animateTo(-screenWidthPx * 2, spring(stiffness = 200f))
                                        onSkip()
                                        xOffset.snapTo(0f)
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
                },
            contentAlignment = Alignment.Center
        ) {
            // Parse resolution into aspect ratio so the inner card is always the right shape
            val aspectRatio = remember(wallpaper.resolution) {
                val parts = wallpaper.resolution.split("x", "×")
                val w = parts.getOrNull(0)?.trim()?.toFloatOrNull()
                val h = parts.getOrNull(1)?.trim()?.toFloatOrNull()
                if (w != null && h != null && h > 0f) w / h else 16f / 9f
            }

            // Inner card sized by image aspect ratio — avoids top/bottom clipping
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(MaterialTheme.shapes.extraLarge)
            ) {
                // Main image — use fullUrl so we get the actual wallpaper, not the cropped thumb
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(wallpaper.fullUrl.ifBlank { wallpaper.thumbUrl })
                        .placeholderMemoryCacheKey(wallpaper.thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

            // Loading shimmer when fetching next card
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // Save to list overlay (right swipe)
            if (saveAlpha > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B5E20).copy(alpha = saveAlpha * 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "Save to List",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            // Skip overlay (left swipe)
            if (skipAlpha > 0.02f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF7F0000).copy(alpha = skipAlpha * 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "Skip",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            // Source badge (top-left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    wallpaper.source.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            // Info overlay (bottom, toggled)
            if (showInfo) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (wallpaper.resolution.isNotBlank()) {
                        Text(
                            wallpaper.resolution,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF90CAF9)
                        )
                    }
                    if (wallpaper.tags.isNotEmpty()) {
                        Text(
                            wallpaper.tags.take(8).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (wallpaper.pageUrl.isNotBlank()) {
                        Text(
                            wallpaper.pageUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                }
            }
        }

        // Hint text
        Text(
            text = "← skip  ·  swipe right or 📌 to save to list",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 4.dp)
        )

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip
            FilledTonalIconButton(
                onClick = {
                    if (!busy) coroutineScope.launch {
                        xOffset.animateTo(-screenWidthPx * 2, spring(stiffness = 200f))
                        onSkip()
                        xOffset.snapTo(0f)
                    }
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                enabled = !busy
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Skip",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Info toggle
            FilledTonalIconButton(
                onClick = onToggleInfo,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(20.dp))
            }

            // Save to list (big center button)
            FilledIconButton(
                onClick = {
                    if (!busy) coroutineScope.launch {
                        xOffset.animateTo(screenWidthPx * 2, spring(stiffness = 200f))
                        onAddToList()
                        xOffset.snapTo(0f)
                    }
                },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Save to list",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListPickerDialog(
    lists: List<RemoteList>,
    selectedListId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to list") },
        text = {
            LazyColumn {
                items(lists, key = { it.id }) { list ->
                    ListItem(
                        headlineContent = { Text(list.name) },
                        supportingContent = { Text("${list.count} wallpapers") },
                        leadingContent = {
                            if (list.id == selectedListId) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(list.id) }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverSettingsDialog(
    settings: com.chrisalvis.rotato.data.DiscoverSettings,
    saving: Boolean,
    onSave: (com.chrisalvis.rotato.data.DiscoverSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var sorting by remember(settings) { mutableStateOf(settings.sorting) }
    var minResolution by remember(settings) { mutableStateOf(settings.minResolution) }
    var aspectRatio by remember(settings) { mutableStateOf(settings.aspectRatio) }
    var nsfwMode by remember(settings) { mutableStateOf(settings.nsfwMode) }

    val sortOptions = listOf("relevance", "date_added", "views", "favorites", "random")
    val resolutionOptions = listOf("1920x1080", "2560x1440", "3840x2160", "1280x720")
    val aspectOptions = listOf("" to "Any", "16x9" to "16:9", "9x16" to "9:16", "4x3" to "4:3", "1x1" to "1:1")

    var sortExpanded by remember { mutableStateOf(false) }
    var resExpanded by remember { mutableStateOf(false) }
    var arExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Sort by
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sort by", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(expanded = sortExpanded, onExpandedChange = { sortExpanded = it }) {
                        OutlinedTextField(
                            value = sorting,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            sortOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = { sorting = opt; sortExpanded = false })
                            }
                        }
                    }
                }

                // Min resolution
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Min resolution", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(expanded = resExpanded, onExpandedChange = { resExpanded = it }) {
                        OutlinedTextField(
                            value = minResolution,
                            onValueChange = { minResolution = it },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(resExpanded) },
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = resExpanded, onDismissRequest = { resExpanded = false }) {
                            resolutionOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt) }, onClick = { minResolution = opt; resExpanded = false })
                            }
                        }
                    }
                }

                // Aspect ratio
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Aspect ratio", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(expanded = arExpanded, onExpandedChange = { arExpanded = it }) {
                        OutlinedTextField(
                            value = aspectOptions.find { it.first == aspectRatio }?.second ?: aspectRatio.ifBlank { "Any" },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(arExpanded) },
                            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = arExpanded, onDismissRequest = { arExpanded = false }) {
                            aspectOptions.forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { aspectRatio = value; arExpanded = false })
                            }
                        }
                    }
                }

                // NSFW toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("NSFW", style = MaterialTheme.typography.bodyMedium)
                        Text("Enable adult content", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(checked = nsfwMode, onCheckedChange = { nsfwMode = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(com.chrisalvis.rotato.data.DiscoverSettings(
                        sorting = sorting,
                        minResolution = minResolution,
                        aspectRatio = aspectRatio,
                        nsfwMode = nsfwMode
                    ))
                },
                enabled = !saving
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NoResultsState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ImageNotSupported,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No wallpapers found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Check that sources are enabled in your animebacks settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
