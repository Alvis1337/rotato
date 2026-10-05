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
    val bitmap = loadScaledBitmap(context, file.absolutePath) ?: return "Could not load image"
    val settings = prefs.settings.first()
    val isNsfwHomeOnly = prefs.nsfwHomeOnly.first() && prefs.nsfwFileNames.first().contains(file.name)
    val target = if (isNsfwHomeOnly) WallpaperTarget.HOME_ONLY else settings.wallpaperTarget
    val flags = when (target) {
        WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }
    val screenBitmap = fitWallpaperBitmap(bitmap, settings.wallpaperFit, wallpaperTargetSize(context))
    bitmap.recycle()
    try {
        setWallpaperBitmap(context, WallpaperManager.getInstance(context), screenBitmap, flags, settings.wallpaperFit, settings.wallpaperEffects)
    } finally {
        screenBitmap.recycle()
    }
    if (recordAsCurrent) prefs.pushAppliedWallpaper(file.absolutePath)
    return null
}

/** Goes back to the wallpaper shown before the current one. Returns an error message or null. */
suspend fun applyPreviousWallpaper(context: Context): String? {
    val path = RotatoPreferences(context).popPreviousWallpaper { File(it).exists() }
        ?: return "No earlier wallpaper to go back to"
    return applyWallpaperFile(context, File(path), recordAsCurrent = false)
}
