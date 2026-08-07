package com.chrisalvis.rotato.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
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
 * Session-scoped (not persisted across app restarts) mute preference for full-screen video
 * playback — unmuting one video keeps subsequent videos unmuted for the rest of the session.
 * Grid previews never read this; they're always muted regardless.
 */
object VideoMuteState {
    var muted by mutableStateOf(true)
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Plays a video URL in a loop. Player is created/released alongside this composable's lifecycle.
 *
 * Never uses ExoPlayer's built-in [PlayerView] controller — its native bottom control bar
 * (timeline/settings) has no awareness of the app's own Compose overlays and ends up fighting
 * them for the same screen space. Instead, when [allowTapToToggle] is set, a single tap
 * play/pauses and an optional [showSeekBar]/double-tap-to-seek are drawn in Compose that we
 * fully control.
 */
@Composable
fun VideoPlayerView(
    url: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    allowTapToToggle: Boolean = false,
    showMuteButton: Boolean = false,
    showSeekBar: Boolean = false,
    allowDoubleTapSeek: Boolean = false,
    // Callers that draw their own bottom-aligned overlay (caption/tags/actions) over the video
    // must pass that overlay's measured height here, or its touch targets will sit on top of —
    // and steal taps from — the seek bar, since both are independently BottomCenter-aligned over
    // the same full-screen area.
    seekBarBottomInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    var isMuted by remember(url) { mutableStateOf(if (showMuteButton) VideoMuteState.muted else muted) }
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
                volume = if (isMuted) 0f else 1f
                prepare()
                playWhenReady = true
            }
    }
    var isPlaying by remember(exoPlayer) { mutableStateOf(true) }
    var isBuffering by remember(exoPlayer) { mutableStateOf(true) }
    var hasError by remember(exoPlayer) { mutableStateOf(false) }
    var positionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var durationMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var seekFeedback by remember(exoPlayer) { mutableStateOf<Int?>(null) } // seek delta in seconds, for the transient overlay
    var userSeeking by remember(exoPlayer) { mutableStateOf(false) }
    var draggedFraction by remember(exoPlayer) { mutableFloatStateOf(0f) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    if (showSeekBar) {
        LaunchedEffect(exoPlayer) {
            while (true) {
                if (!userSeeking) positionMs = exoPlayer.currentPosition
                delay(250)
            }
        }
    }

    if (seekFeedback != null) {
        LaunchedEffect(seekFeedback) {
            delay(600)
            seekFeedback = null
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0f else 1f
        if (showMuteButton) VideoMuteState.muted = isMuted
    }

    fun seekBy(deltaSeconds: Int) {
        val target = (exoPlayer.currentPosition + deltaSeconds * 1000L)
            .coerceIn(0L, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exoPlayer.seekTo(target)
        positionMs = target
        seekFeedback = deltaSeconds
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
        )

        // A plain Compose node (not the AndroidView above) so the seek bar drawn later in this
        // Box correctly wins touch priority in its region. AndroidView-embedded native views
        // participate in Android's own touch dispatch ahead of Compose z-order, so attaching this
        // gesture directly to the PlayerView would let it steal taps/drags meant for the Slider.
        if (allowTapToToggle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(exoPlayer, allowDoubleTapSeek) {
                        detectTapGestures(
                            onTap = {
                                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                isPlaying = exoPlayer.playWhenReady
                            },
                            onDoubleTap = { offset ->
                                if (allowDoubleTapSeek) {
                                    if (offset.x < size.width / 2f) seekBy(-10) else seekBy(10)
                                }
                            }
                        )
                    }
            )
        }

        if (isBuffering && !hasError) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(if (allowTapToToggle) 40.dp else 20.dp)
            )
        }

        if (hasError) {
            if (allowTapToToggle) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        "Couldn't load video",
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Video unavailable",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
        }

        if (allowTapToToggle && !isPlaying && !isBuffering && !hasError) {
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

        AnimatedVisibility(
            visible = seekFeedback != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val delta = seekFeedback ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    if (delta < 0) Icons.Default.Replay10 else Icons.Default.Forward10,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    "${if (delta < 0) "-" else "+"}${kotlin.math.abs(delta)}s",
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        if (showMuteButton) {
            Icon(
                if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable { toggleMute() }
                    .padding(8.dp)
            )
        }

        if (showSeekBar && durationMs > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = seekBarBottomInset)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val shownPosition = if (userSeeking) (draggedFraction * durationMs).toLong() else positionMs
                Text(
                    formatDuration(shownPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = if (userSeeking) draggedFraction else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = {
                        userSeeking = true
                        draggedFraction = it
                    },
                    onValueChangeFinished = {
                        val target = (draggedFraction * durationMs).toLong()
                        exoPlayer.seekTo(target)
                        positionMs = target
                        userSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    formatDuration(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
