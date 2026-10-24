package com.chrisalvis.rotato.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * Android 8–9 need WRITE_EXTERNAL_STORAGE to save into Pictures/Movies; 10+ save through
 * MediaStore without it. Gallery saves call [ensure] first, and MainActivity collects
 * [requests] to show the system prompt.
 */
object LegacyStoragePermission {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * True when a gallery save can go ahead. Otherwise asks for the permission; the caller drops
     * this save and the user taps save again once it's allowed.
     */
    fun ensure(context: Context): Boolean {
        if (isGranted(context)) return true
        _requests.tryEmit(Unit)
        return false
    }

    /** Pre-Q saves write the file directly, so tell the media store it exists for the gallery to show it. */
    fun scan(context: Context, file: File, mimeType: String) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
    }
}
