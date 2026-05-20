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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onBrowseFeed: () -> Unit = {}
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val setNowState by viewModel.setNowState.collectAsStateWithLifecycle()
    val lastRotationMs by viewModel.lastRotationMs.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val linkedCollection = collections.firstOrNull { it.useAsRotation }

    val dragSelectState = rememberDragSelectState<File>()
    val inSelectionMode = dragSelectState.inSelectionMode

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
                    linkedCollection = linkedCollection,
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
    linkedCollection: LocalList?,
    collections: List<LocalList>,
    dragSelectState: DragSelectState<File>,
    inSelectionMode: Boolean,
    onPhotoPick: () -> Unit,
    onGoToDiscover: () -> Unit = {},
) {
    val saveToListInProgress by viewModel.saveToListInProgress.collectAsStateWithLifecycle()
    val rotationErrors by viewModel.rotationErrors.collectAsStateWithLifecycle()
    var showSaveToListDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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
            linkedCollection = linkedCollection,
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

        if (images.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f), onGoToDiscover = onGoToDiscover, onAddPhotos = onPhotoPick)
        } else {
            var selectedFile by remember { mutableStateOf<File?>(null) }
            var contextMenuFile by remember { mutableStateOf<File?>(null) }
            selectedFile?.let { file ->
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
                    onSaveToGallery = { currentFile -> viewModel.saveFileToGallery(currentFile) }
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = dragSelectState.gridState,
                modifier = Modifier
                    .weight(1f)
                    .gridDragSelect(items = images, state = dragSelectState),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(images, key = { it.absolutePath }) { file ->
                    val isSelected by remember { derivedStateOf { dragSelectState.isSelected(file) } }
                    Box {
                        ImageThumbnail(
                            file = file,
                            isSelected = isSelected,
                            inSelectionMode = inSelectionMode,
                            onClick = { if (!inSelectionMode) selectedFile = file },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                contextMenuFile = file
                            }
                        )
                        DropdownMenu(
                            expanded = contextMenuFile == file,
                            onDismissRequest = { contextMenuFile = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Set as wallpaper") },
                                leadingIcon = { Icon(Icons.Filled.Wallpaper, null) },
                                onClick = {
                                    contextMenuFile = null
                                    viewModel.setSpecificWallpaper(file)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to gallery") },
                                leadingIcon = { Icon(Icons.Filled.SaveAlt, null) },
                                onClick = {
                                    contextMenuFile = null
                                    viewModel.saveFileToGallery(file)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    contextMenuFile = null
                                    viewModel.removeImage(file)
                                }
                            )
                        }
                    }
                }
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
                        Text("Failed — retry?")
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
    linkedCollection: LocalList?,
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
                if (linkedCollection != null) {
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
                            text = "Linked to \"${linkedCollection.name}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
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
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val badgeInfo = remember(file.absolutePath, file.length(), file.lastModified()) {
        readImageBadgeInfo(file)
    }

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
                    onClick = onClick,
                    onLongClick = onLongClick
                ) else Modifier
            )
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

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
    onSaveToGallery: (File) -> Unit = {}
) {
    val initialPage = remember(images, initialFile.absolutePath) {
        images.indexOfFirst { it.absolutePath == initialFile.absolutePath }
            .takeIf { it >= 0 }
            ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { images.size }
    val currentFile by remember(images, pagerState) {
        derivedStateOf { images.getOrNull(pagerState.currentPage) }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale > 1f) offset + (panChange * nextScale) else Offset.Zero
    }

    LaunchedEffect(images.size, pagerState.currentPage) {
        if (images.isEmpty()) {
            onDismiss()
            return@LaunchedEffect
        }
        if (pagerState.currentPage > images.lastIndex) {
            pagerState.scrollToPage(images.lastIndex)
        }
    }

    LaunchedEffect(currentFile?.absolutePath) {
        scale = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = scale == 1f,
                beyondViewportPageCount = 1
            ) { page ->
                val pageFile = images.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = page == pagerState.currentPage

                AsyncImage(
                    model = pageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (isCurrentPage) scale else 1f
                            scaleY = if (isCurrentPage) scale else 1f
                            translationX = if (isCurrentPage) offset.x else 0f
                            translationY = if (isCurrentPage) offset.y else 0f
                        }
                        .transformable(
                            state = transformState,
                            enabled = isCurrentPage
                        )
                )
            }

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

            currentFile?.let { file ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { onRemove(file) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove")
                    }
                    OutlinedButton(onClick = { onSaveToGallery(file) }) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save to Gallery")
                    }
                    Button(onClick = { onSetWallpaper(file) }) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Set as Wallpaper")
                    }
                }
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
                text = "Add wallpapers to start rotating",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
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
