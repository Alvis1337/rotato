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
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
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
import java.util.Date
import java.util.Locale

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = RotatoPreferences(app)
    private val feedRepo = FeedRepository(File(app.filesDir, "rotato_images").also { it.mkdirs() })

    val history: StateFlow<List<WallpaperHistoryItem>> = prefs.historyJson
        .map { historyFromJson(it).reversed() }
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

    fun downloadToRotation(item: WallpaperHistoryItem) {
        if (item.fullUrl.startsWith("/")) return  // local file — already in rotation pool
        val key = item.fullUrl
        if (_downloading.value.contains(key)) return
        viewModelScope.launch {
            _downloading.value = _downloading.value + key
            val sourceId = item.fullUrl.substringAfterLast('/').substringBeforeLast('.')
            feedRepo.downloadWallpaper(sourceId, item.fullUrl)
            _downloading.value = _downloading.value - key
        }
    }

    fun saveToGallery(item: WallpaperHistoryItem) {
        val ctx = getApplication<Application>().applicationContext
        val key = item.fullUrl
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
    val downloading by vm.downloading
    val context = LocalContext.current

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(history, key = { "${it.timestamp}_${it.fullUrl}" }) { item ->
            val isLocal = item.fullUrl.startsWith("/")
            val openUri = item.pageUrl.ifBlank { if (!isLocal) item.fullUrl else null }
            HistoryCard(
                item = item,
                isDownloading = downloading.contains(item.fullUrl),
                onDownload = { vm.saveToGallery(item) },
                onRemove = { vm.removeItem(item) },
                onOpenPage = if (openUri != null) ({
                    context.startActivity(Intent(Intent.ACTION_VIEW, openUri.toUri()))
                }) else null
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: WallpaperHistoryItem,
    isDownloading: Boolean,
    onDownload: () -> Unit,
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
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Save to gallery")
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
