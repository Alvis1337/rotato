package com.chrisalvis.rotato.data

import android.app.WallpaperManager
import android.content.Context
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Sets [file] as the wallpaper with the user's target, fit and effects, the same way a rotation
 * does. When [recordAsCurrent] is set it is pushed onto the "applied" list that Back walks.
 * Returns null on success, or a short error message.
 */
suspend fun applyWallpaperFile(context: Context, file: File, recordAsCurrent: Boolean = true): String? {
    val prefs = RotatoPreferences(context)
    val settings = prefs.settings.first()
    val pair = foldPairWallpaperFor(context, file, settings.wallpaperFit)
    val isNsfwHomeOnly = prefs.nsfwHomeOnly.first() && prefs.nsfwFileNames.first().contains(file.name)
    val target = if (isNsfwHomeOnly) WallpaperTarget.HOME_ONLY else settings.wallpaperTarget
    val flags = when (target) {
        WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }
    val screenBitmap = if (pair != null) {
        pair.bitmap
    } else {
        val bitmap = loadScaledBitmap(context, file.absolutePath) ?: return "Could not load image"
        fitWallpaperBitmap(bitmap, settings.wallpaperFit, wallpaperTargetSize(context)).also { bitmap.recycle() }
    }
    try {
        setWallpaperBitmap(
            context, WallpaperManager.getInstance(context), screenBitmap, flags,
            settings.wallpaperFit, settings.wallpaperEffects, crops = pair?.crops,
        )
    } finally {
        screenBitmap.recycle()
    }
    if (recordAsCurrent) prefs.pushAppliedWallpaper(file.absolutePath)
    val entry = LocalListsPreferences(context).allWallpapers.first()
        .firstOrNull { sanitizeFilename(it.sourceId) == file.nameWithoutExtension }
    prefs.recordWallpaperShown(
        WallpaperHistoryItem(
            thumbUrl = entry?.thumbUrl ?: file.absolutePath,
            sampleUrl = entry?.sampleUrl ?: "",
            fullUrl = entry?.fullUrl ?: file.absolutePath,
            source = entry?.source ?: "local",
            timestamp = System.currentTimeMillis(),
            tags = entry?.tags ?: emptyList(),
            pageUrl = entry?.pageUrl ?: "",
        )
    )
    return null
}

/** Goes back to the wallpaper shown before the current one. Returns an error message or null. */
suspend fun applyPreviousWallpaper(context: Context): String? {
    val path = RotatoPreferences(context).popPreviousWallpaper { File(it).exists() }
        ?: return "No earlier wallpaper to go back to"
    return applyWallpaperFile(context, File(path), recordAsCurrent = false)
}
