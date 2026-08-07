package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.util.concurrent.atomic.AtomicInteger

/**
 * Some booru CDNs (Gelbooru) reject hotlinked requests without a Referer header and redirect
 * to a "hotlink blocked" page instead of the actual file. Coil already works around this for
 * static images via an OkHttp interceptor in RotatoApp — ExoPlayer uses its own network stack
 * and never sees that interceptor, so video playback needs the same header applied here.
 */
private fun refererFor(url: String): String? = when {
    url.contains("gelbooru.com", ignoreCase = true) -> "https://gelbooru.com/"
    else -> null
}

/**
 * Bounds how many grid-preview videos can autoplay at once. Scrolling grids can mount several
 * video tiles simultaneously (visible + lazy-layout buffer); without a cap that means that many
 * concurrent ExoPlayer instances each streaming their own video, which is a real data/battery cost.
 * Tiles that lose the race fall back to a static poster + play badge instead of live playback.
 */
object VideoPreviewLimiter {
    private const val MAX_CONCURRENT = 3
    private val active = AtomicInteger(0)

    fun tryAcquire(): Boolean {
        while (true) {
            val current = active.get()
            if (current >= MAX_CONCURRENT) return false
            if (active.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        active.updateAndGet { (it - 1).coerceAtLeast(0) }
    }
}

/**
 * Acquires a grid-preview playback slot for as long as this composable is in the composition
 * and [enabled] stays true. Pass `enabled = false` (e.g. autoplay previews turned off in
 * Settings) to skip acquiring a slot entirely — never returns true in that case.
 */
@Composable
fun rememberVideoPreviewSlot(enabled: Boolean): Boolean {
    var acquired by remember { mutableStateOf(false) }
    DisposableEffect(enabled) {
        acquired = enabled && VideoPreviewLimiter.tryAcquire()
        onDispose { if (acquired) VideoPreviewLimiter.release() }
    }
    return acquired
}

/**
 * Plays a video URL in a loop, muted by default (most booru/reddit video posts have no
 * associated audio track anyway). Player is created/released alongside this composable's lifecycle.
 *
 * Never uses ExoPlayer's built-in [PlayerView] controller — its native bottom control bar
 * (timeline/settings) has no awareness of the app's own Compose overlays and ends up fighting
 * them for the same screen space. Instead, when [allowTapToToggle] is set, a single tap
 * play/pauses via a small Compose-drawn icon that we fully control.
 */
@Composable
fun VideoPlayerView(
    url: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    allowTapToToggle: Boolean = false,
) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            refererFor(url)?.let { referer -> setDefaultRequestProperties(mapOf("Referer" to referer)) }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = ExoPlayer.REPEAT_MODE_ALL
                volume = if (muted) 0f else 1f
                prepare()
                playWhenReady = true
            }
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .let {
                    if (!allowTapToToggle) it else it.pointerInput(exoPlayer) {
                        detectTapGestures(onTap = {
                            exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                            isPlaying = exoPlayer.playWhenReady
                        })
                    }
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
        )
        if (allowTapToToggle && !isPlaying) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(12.dp)
            )
        }
    }
}
