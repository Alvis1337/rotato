package com.chrisalvis.rotato.worker

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chrisalvis.rotato.data.RotatoPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Panic button" QS tile: one tap forces global SFW mode and switches rotation to the
 * user-designated stealth collection until tapped again. See RotatoPreferences.setStealthActive
 * for how the previous NSFW mode is remembered and restored, and WallpaperWorker for how the
 * stealth collection override takes effect during rotation.
 */
class StealthTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        scope.launch {
            val prefs = RotatoPreferences(applicationContext)
            updateTile(prefs.stealthActive.first(), prefs.stealthCollectionId.first().isNotBlank())
        }
    }

    override fun onClick() {
        scope.launch {
            val prefs = RotatoPreferences(applicationContext)
            val collectionId = prefs.stealthCollectionId.first()
            if (collectionId.isBlank()) {
                updateTile(active = false, configured = false)
                return@launch
            }
            val newActive = !prefs.stealthActive.first()
            prefs.setStealthActive(newActive)
            updateTile(newActive, configured = true)

            // Apply immediately rather than waiting for the next scheduled rotation.
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>().build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork("stealth_toggle_apply", ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile(active: Boolean, configured: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !configured -> "Not set up"
                active -> "On"
                else -> "Off"
            }
        }
        tile.updateTile()
    }
}
