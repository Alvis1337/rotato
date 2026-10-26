package com.chrisalvis.rotato.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisalvis.rotato.data.WallpaperCanvas
import com.chrisalvis.rotato.live.LiveWallpaper
import java.io.File

enum class LibraryKind(val label: String) { ALL("All"), IMAGES("Images"), VIDEOS("Videos") }

enum class LibrarySort(val label: String) { NAME("Name"), NEWEST("Newest"), OLDEST("Oldest"), TOP_RATED("Top rated") }

/** What the Library grid is narrowed to. Defaults show everything in the usual order. */
data class LibraryFilter(
    val kind: LibraryKind = LibraryKind.ALL,
    val foldOnly: Boolean = false,
    val nsfwOnly: Boolean = false,
    val unratedOnly: Boolean = false,
    val sort: LibrarySort = LibrarySort.NAME,
) {
    val active: Boolean get() = kind != LibraryKind.ALL || foldOnly || nsfwOnly || unratedOnly

    fun apply(
        files: List<File>,
        ratings: Map<String, Int>,
        nsfwNames: Set<String>,
        foldFriendlyNames: Set<String>?,
    ): List<File> {
        val kept = files.filter { f ->
            val video = LiveWallpaper.isVideoFile(f)
            when (kind) {
                LibraryKind.ALL -> true
                LibraryKind.IMAGES -> !video
                LibraryKind.VIDEOS -> video
            } &&
                (!foldOnly || foldFriendlyNames == null || f.name in foldFriendlyNames) &&
                (!nsfwOnly || f.name in nsfwNames) &&
                (!unratedOnly || (ratings[f.name] ?: 0) == 0)
        }
        return when (sort) {
            LibrarySort.NAME -> kept
            LibrarySort.NEWEST -> kept.sortedByDescending { it.lastModified() }
            LibrarySort.OLDEST -> kept.sortedBy { it.lastModified() }
            LibrarySort.TOP_RATED -> kept.sortedByDescending { ratings[it.name] ?: 0 }
        }
    }
}

/** Keeps the Library chips and sort across rotation and fold/unfold. */
val LibraryFilterSaver: Saver<LibraryFilter, Any> = listSaver<LibraryFilter, Any>(
    save = { listOf(it.kind.name, it.foldOnly, it.nsfwOnly, it.unratedOnly, it.sort.name) },
    restore = { v ->
        LibraryFilter(
            kind = LibraryKind.entries.find { it.name == v.getOrNull(0) } ?: LibraryKind.ALL,
            foldOnly = v.getOrNull(1) == true,
            nsfwOnly = v.getOrNull(2) == true,
            unratedOnly = v.getOrNull(3) == true,
            sort = LibrarySort.entries.find { it.name == v.getOrNull(4) } ?: LibrarySort.NAME,
        )
    },
)

/** Keeps a "Match a photo" result across rotation and fold/unfold, as "brightness,COLOUR,…". */
val ImageLookSaver: Saver<com.chrisalvis.rotato.data.ImageLook?, String> = Saver(
    save = { look -> look?.let { l -> (listOf(l.brightness.toString()) + l.colours.map { it.name }).joinToString(",") } },
    restore = { saved ->
        val parts = saved.split(",")
        parts.firstOrNull()?.toIntOrNull()?.let { brightness ->
            com.chrisalvis.rotato.data.ImageLook(
                brightness,
                parts.drop(1).mapNotNullTo(HashSet()) { n -> com.chrisalvis.rotato.data.ImageColour.entries.find { it.name == n } },
            )
        }
    },
)

/** Names of the [files] big enough to fill both Fold screens without blurring. */
fun foldFriendlyNames(files: List<File>, canvas: WallpaperCanvas): Set<String> =
    files.mapNotNullTo(HashSet()) { f ->
        val (w, h) = mediaSize(f) ?: return@mapNotNullTo null
        if (com.chrisalvis.rotato.data.isFoldFriendly(w, h, canvas)) f.name else null
    }

private fun mediaSize(file: File): Pair<Int, Int>? {
    if (LiveWallpaper.isVideoFile(file)) {
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0) null else if (rot % 180 != 0) h to w else w to h
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, o)
    return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
}

@Composable
fun LibraryFilterRow(
    filter: LibraryFilter,
    onChange: (LibraryFilter) -> Unit,
    hasVideos: Boolean,
    showFold: Boolean,
    showNsfw: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasVideos || filter.kind != LibraryKind.ALL) {
            LibraryKind.entries.forEach { k ->
                FilterChip(
                    selected = filter.kind == k,
                    onClick = { onChange(filter.copy(kind = k)) },
                    label = { Text(k.label) },
                )
            }
        }
        if (showFold) FilterChip(
            selected = filter.foldOnly,
            onClick = { onChange(filter.copy(foldOnly = !filter.foldOnly)) },
            label = { Text("Fold-friendly") },
        )
        if (showNsfw) FilterChip(
            selected = filter.nsfwOnly,
            onClick = { onChange(filter.copy(nsfwOnly = !filter.nsfwOnly)) },
            label = { Text("NSFW") },
        )
        FilterChip(
            selected = filter.unratedOnly,
            onClick = { onChange(filter.copy(unratedOnly = !filter.unratedOnly)) },
            label = { Text("Unrated") },
        )
        var sortOpen by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = filter.sort != LibrarySort.NAME,
                onClick = { sortOpen = true },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(filter.sort.label) },
            )
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                LibrarySort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { onChange(filter.copy(sort = s)); sortOpen = false },
                    )
                }
            }
        }
    }
}

/** The Library's type, fold, NSFW and rating chips, wrapped onto lines for the filter sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterChips(
    filter: LibraryFilter,
    onChange: (LibraryFilter) -> Unit,
    hasVideos: Boolean,
    showFold: Boolean,
    showNsfw: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasVideos || filter.kind != LibraryKind.ALL) {
            LibraryKind.entries.forEach { k ->
                FilterChip(
                    selected = filter.kind == k,
                    onClick = { onChange(filter.copy(kind = k)) },
                    label = { Text(k.label) },
                )
            }
        }
        if (showFold) FilterChip(
            selected = filter.foldOnly,
            onClick = { onChange(filter.copy(foldOnly = !filter.foldOnly)) },
            label = { Text("Fold-friendly") },
        )
        if (showNsfw) FilterChip(
            selected = filter.nsfwOnly,
            onClick = { onChange(filter.copy(nsfwOnly = !filter.nsfwOnly)) },
            label = { Text("NSFW") },
        )
        FilterChip(
            selected = filter.unratedOnly,
            onClick = { onChange(filter.copy(unratedOnly = !filter.unratedOnly)) },
            label = { Text("Unrated") },
        )
    }
}

/** The Library's sort order as one chip per choice. Off while a photo match or rainbow decides the order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibrarySortChips(
    filter: LibraryFilter,
    onChange: (LibraryFilter) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySort.entries.forEach { s ->
            FilterChip(
                selected = filter.sort == s,
                onClick = { onChange(filter.copy(sort = s)) },
                enabled = enabled,
                label = { Text(s.label) },
            )
        }
    }
}
