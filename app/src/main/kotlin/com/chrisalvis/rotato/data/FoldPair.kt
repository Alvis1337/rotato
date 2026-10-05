package com.chrisalvis.rotato.data

import android.content.Context
import kotlinx.coroutines.flow.first
import java.io.File

/** Two library images shown together on a foldable: [outerPath] folded, [innerPath] unfolded. */
data class FoldPair(val outerPath: String, val innerPath: String) {
    fun contains(path: String): Boolean = path == outerPath || path == innerPath
}

/**
 * If [file] belongs to a fold pair and this device can show one, composes the pair's
 * wallpaper. Returns null when there is no usable pair (the caller then sets [file] alone).
 */
suspend fun foldPairWallpaperFor(context: Context, file: File, fit: WallpaperFit): FoldPairWallpaper? {
    if (!supportsFoldPairs(context)) return null
    val pair = RotatoPreferences(context).foldPairs.first().firstOrNull { it.contains(file.absolutePath) } ?: return null
    val outer = loadScaledBitmap(context, pair.outerPath) ?: return null
    val inner = loadScaledBitmap(context, pair.innerPath)
    if (inner == null) {
        outer.recycle()
        return null
    }
    return try {
        composeFoldPair(context, outer, inner, fit)
    } finally {
        outer.recycle()
        inner.recycle()
    }
}

/** Library files that belong to a fold pair whose other half still exists. */
fun List<FoldPair>.pairedPaths(): Set<String> =
    filter { File(it.outerPath).exists() && File(it.innerPath).exists() }
        .flatMap { listOf(it.outerPath, it.innerPath) }
        .toSet()
