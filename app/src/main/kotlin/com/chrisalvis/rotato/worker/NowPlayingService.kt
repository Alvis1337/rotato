package com.chrisalvis.rotato.worker

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.chrisalvis.rotato.data.NowPlaying
import com.chrisalvis.rotato.data.PlayingTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches media sessions for now playing mode. Notification access is only how Android lets an
 * app see what's playing; no notification is read. While the mode is off it unbinds itself.
 */
class NowPlayingService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    private val watched = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var pending: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!NowPlaying.enabled(this)) {
            // Settle anything left on screen from before, then get out of the way.
            scope.launch { runCatching { NowPlaying.stopped(applicationContext, force = true) } }
            runCatching { requestUnbind() }
            return
        }
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        val me = ComponentName(this, NowPlayingService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, me, main)
            watch(manager.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        release()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        super.onDestroy()
    }

    private fun release() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        sessions = null
        watched.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        watched.clear()
    }

    /** Follows exactly the sessions in [controllers], then looks at what's playing now. */
    private fun watch(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watched.keys.filter { it !in tokens }.forEach { token ->
            watched.remove(token)?.let { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        }
        controllers.filter { it.sessionToken !in watched }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = evaluate()
                override fun onMetadataChanged(metadata: MediaMetadata?) = evaluate()
                override fun onSessionDestroyed() = evaluate()
            }
            runCatching { controller.registerCallback(callback, main) }
            watched[controller.sessionToken] = controller to callback
        }
        evaluate()
    }

    /**
     * Waits a moment before acting: players send bursts of updates on a track change, and a
     * short pause or a gap between tracks shouldn't flip the wallpaper back and forth.
     */
    private fun evaluate() {
        if (!NowPlaying.enabled(this)) return
        val playing = watched.values.map { it.first }.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val metadata = playing?.metadata
        pending?.cancel()
        pending = if (playing != null && metadata != null) {
            scope.launch {
                delay(PLAY_SETTLE_MS)
                val track = trackOf(metadata) ?: return@launch
                NowPlaying.playing(applicationContext, track)
            }
        } else {
            scope.launch {
                delay(STOP_GRACE_MS)
                NowPlaying.stopped(applicationContext)
            }
        }
    }

    private suspend fun trackOf(metadata: MediaMetadata): PlayingTrack? {
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (artist.isBlank() && album.isBlank() && title.isBlank()) return null
        return PlayingTrack(artist.trim(), album.trim(), title.trim(), artOf(metadata))
    }

    /** A private copy of the cover: the session's own bitmap can be recycled under us. */
    private suspend fun artOf(metadata: MediaMetadata): Bitmap? {
        val embedded = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        ).firstNotNullOfOrNull { key -> runCatching { metadata.getBitmap(key) }.getOrNull()?.takeIf { !it.isRecycled } }
        if (embedded != null) return runCatching { embedded.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        val uri = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        ).firstNotNullOfOrNull { key -> metadata.getString(key)?.takeIf { it.isNotBlank() } } ?: return null
        val request = ImageRequest.Builder(this)
            .data(Uri.parse(uri))
            .allowHardware(false)
            .size(1200)
            .build()
        val bitmap = ((imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return null
        // Coil may keep this one in its memory cache, so hand over a copy.
        return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
    }

    private companion object {
        const val PLAY_SETTLE_MS = 1_500L
        const val STOP_GRACE_MS = 20_000L
    }
}
