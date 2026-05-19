package com.chrisalvis.rotato.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.chrisalvis.rotato.data.FeedRepository
import com.chrisalvis.rotato.data.LocalList
import com.chrisalvis.rotato.data.LocalListsPreferences
import com.chrisalvis.rotato.data.LocalWallpaperEntry
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.WallpaperHistoryItem
import com.chrisalvis.rotato.data.historyFromJson
import com.chrisalvis.rotato.data.toJson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = RotatoPreferences(app)
    private val listsPrefs = LocalListsPreferences(app)
    private val feedRepo = FeedRepository(File(app.filesDir, "rotato_images").also { it.mkdirs() })

    val history: StateFlow<List<WallpaperHistoryItem>> = prefs.historyJson
        .map { historyFromJson(it).reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lists: StateFlow<List<LocalList>> = listsPrefs.lists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _downloading = mutableStateOf<Set<String>>(emptySet())
    val downloading: State<Set<String>> = _downloading

    fun removeItem(item: WallpaperHistoryItem) {
        viewModelScope.launch {
            val updated = history.value.filter {
                it.timestamp != item.timestamp || it.fullUrl != item.fullUrl
            }
            prefs.setHistoryJson(updated.reversed().toJson())
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            prefs.setHistoryJson(emptyList<WallpaperHistoryItem>().toJson())
        }
    }

    fun downloadToRotation(item: WallpaperHistoryItem) {
        if (item.fullUrl.startsWith("/")) return  // local file — already in rotation pool
        val key = "rotation:${item.fullUrl}"
        if (_downloading.value.contains(key)) return
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            _downloading.value = _downloading.value + key
            val sourceId = item.fullUrl.substringAfterLast('/').substringBeforeLast('.')
            val ok = feedRepo.downloadWallpaper(sourceId, item.fullUrl)
            Toast.makeText(ctx, if (ok) "Added to Library" else "Download failed", Toast.LENGTH_SHORT).show()
            _downloading.value = _downloading.value - key
        }
    }

    fun saveToGallery(item: WallpaperHistoryItem) {
        val ctx = getApplication<Application>().applicationContext
        val key = "gallery:${item.fullUrl}"
        if (_downloading.value.contains(key)) return
        viewModelScope.launch {
            _downloading.value = _downloading.value + key
            val ok = if (item.fullUrl.startsWith("/")) {
                saveLocalFileToGallery(ctx, java.io.File(item.fullUrl))
            } else {
                val sourceId = item.fullUrl.substringAfterLast('/').substringBeforeLast('.')
                feedRepo.saveToGallery(ctx, sourceId, item.fullUrl)
            }
            Toast.makeText(ctx, if (ok) "Saved to gallery" else "Save failed", Toast.LENGTH_SHORT).show()
            _downloading.value = _downloading.value - key
        }
    }

    fun saveToCollection(item: WallpaperHistoryItem, listId: String) {
        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            val entry = LocalWallpaperEntry(
                id = UUID.randomUUID().toString(),
                listId = listId,
                sourceId = item.fullUrl.substringAfterLast('/').substringBeforeLast('.'),
                source = item.source,
                thumbUrl = item.thumbUrl,
                fullUrl = item.fullUrl,
                resolution = "",
                pageUrl = item.pageUrl,
                tags = item.tags
            )
            listsPrefs.addWallpaperEntry(entry)
            Toast.makeText(ctx, "Saved to collection", Toast.LENGTH_SHORT).show()
        }
    }

    fun isDownloading(item: WallpaperHistoryItem): Boolean {
        val keys = _downloading.value
        return keys.contains("rotation:${item.fullUrl}") || keys.contains("gallery:${item.fullUrl}")
    }

    private fun saveLocalFileToGallery(ctx: android.content.Context, file: java.io.File): Boolean {
        if (!file.exists()) return false
        val ext = file.extension.lowercase()
        val mimeType = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        return try {
            val bytes = file.readBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Rotato")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Rotato")
                dir.mkdirs()
                java.io.File(dir, file.name).writeBytes(bytes)
            }
            true
        } catch (_: Exception) { false }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val vm: HistoryViewModel = viewModel()
    val history by vm.history.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var saveToCollectionFor by remember { mutableStateOf<WallpaperHistoryItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    saveToCollectionFor?.let { item ->
        AlertDialog(
            onDismissRequest = { saveToCollectionFor = null },
            title = { Text("Save to collection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lists.isEmpty()) {
                        Text("No collections yet. Create one in the Collections tab.")
                    } else {
                        lists.forEach { list ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        vm.saveToCollection(item, list.id)
                                        saveToCollectionFor = null
                                    },
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp
                            ) {
                                Text(
                                    text = list.name,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { saveToCollectionFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently remove all ${history.size} history items.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.clearAllHistory(); showClearConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (history.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "No rotation history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Wallpapers will appear here after rotation starts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val today = remember {
        Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
    val yesterday = remember { today - 86_400_000L }

    fun dateHeader(ts: Long): String = when {
        ts >= today -> "Today"
        ts >= yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(ts))
    }

    fun dayBucket(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return cal.timeInMillis
    }

    val grouped = remember(history) { history.groupBy { dayBucket(it.timestamp) } }
    val sortedDays = remember(grouped) { grouped.keys.sortedDescending() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "clear_all_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${history.size} items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all")
                }
            }
        }
        sortedDays.forEach { day ->
            item(key = "header_$day") {
                Text(
                    text = dateHeader(day),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (day == sortedDays.first()) 0.dp else 8.dp, bottom = 4.dp)
                )
            }
            items(grouped[day] ?: emptyList(), key = { "${it.timestamp}_${it.fullUrl}" }) { item ->
                val isLocal = item.fullUrl.startsWith("/")
                val openUri = item.pageUrl.ifBlank { if (!isLocal) item.fullUrl else null }
                HistoryCard(
                    item = item,
                    isDownloading = vm.isDownloading(item),
                    onAddToRotation = if (!isLocal) ({ vm.downloadToRotation(item) }) else null,
                    onDownload = { vm.saveToGallery(item) },
                    onSaveToCollection = { saveToCollectionFor = item },
                    onRemove = { vm.removeItem(item) },
                    onOpenPage = if (openUri != null) ({
                        context.startActivity(Intent(Intent.ACTION_VIEW, openUri.toUri()))
                    }) else null
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: WallpaperHistoryItem,
    isDownloading: Boolean,
    onAddToRotation: (() -> Unit)?,
    onDownload: () -> Unit,
    onSaveToCollection: () -> Unit,
    onRemove: () -> Unit,
    onOpenPage: (() -> Unit)?
) {
    val dateStr = remember(item.timestamp) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }
    var imageError by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (imageError) {
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 72.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = "Image unavailable",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = item.thumbUrl.ifBlank { item.fullUrl },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { if (it is AsyncImagePainter.State.Error) imageError = true },
                    modifier = Modifier
                        .size(width = 100.dp, height = 72.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .then(if (onOpenPage != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenPage) else Modifier)
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.source.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.tags.isNotEmpty()) {
                    Text(
                        item.tags.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (imageError) {
                    Text("Image unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row {
                if (imageError) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                } else if (isDownloading) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    if (onAddToRotation != null) {
                        IconButton(onClick = onAddToRotation) {
                            Icon(Icons.Outlined.Wallpaper, contentDescription = "Add to Library")
                        }
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Save to gallery")
                    }
                    IconButton(onClick = onSaveToCollection) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Save to collection")
                    }
                }
                if (onOpenPage != null && !imageError) {
                    IconButton(onClick = onOpenPage) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open")
                    }
                }
            }
        }
    }
}
