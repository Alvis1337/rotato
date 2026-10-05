package com.chrisalvis.rotato.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.chrisalvis.rotato.data.ImageLoadProgress
import kotlinx.coroutines.delay

/**
 * Full-screen image with honest loading feedback. While the full image downloads, the low-res
 * preview already in memory ([placeholderKey]) is shown softly blurred with a small progress pill
 * at the top, so it never passes for the final image; a failure shows a message and Retry
 * instead of a blank black screen.
 *
 * Feedback only appears once a load has taken longer than [INDICATOR_DELAY_MS]: images that are
 * cached or arrive quickly (the usual case while swiping, since neighbours are preloaded) just
 * appear, without a blur or spinner flashing in and out. Blur fades in and out rather than snapping.
 *
 * [imageModifier] goes on the image itself (zoom/pan transforms, gestures); the overlays stay put.
 */
@Composable
fun FullscreenImage(
    url: String,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    placeholderKey: String? = null,
    contentDescription: String? = null,
    blurPlaceholder: Boolean = true,
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    var state by remember(url, attempt) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val progressMap by ImageLoadProgress.progress.collectAsStateWithLifecycle()
    val progress = progressMap[url]
    val loading = state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty
    var slow by remember(url, attempt) { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) { delay(INDICATOR_DELAY_MS); slow = true } else slow = false
    }
    val blurRadius by animateDpAsState(
        targetValue = if (loading && slow && blurPlaceholder) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 260),
        label = "placeholderBlur"
    )

    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = remember(url, placeholderKey, attempt) {
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .apply { if (placeholderKey != null && placeholderKey != url) placeholderMemoryCacheKey(placeholderKey) }
                    .setParameter("attempt", attempt, memoryCacheKey = null)
                    .crossfade(200)
                    .build()
            },
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onState = { state = it },
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier)
                .then(imageModifier)
        )

        when {
            state is AsyncImagePainter.State.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                Text("Couldn't load this image", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> Unit
        }

        AnimatedVisibility(
            visible = loading && slow && state !is AsyncImagePainter.State.Error,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress }, color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
                Text(
                    if (progress != null) "Loading full quality · ${(progress * 100).toInt()}%" else "Loading full quality…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private const val INDICATOR_DELAY_MS = 350L

/**
 * In-place zoom for the full-screen viewers: pinch anywhere, drag while zoomed, double tap to zoom
 * toward the tapped point (or back out). One instance per viewer, applied to the current page and
 * reset when the page changes.
 */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)
    val zoomed: Boolean get() = scale > 1f

    fun reset() { scale = 1f; offset = Offset.Zero }

    /** Scale by [factor] around [focus] (a point in the page), keeping that point still. */
    fun zoomAround(focus: Offset, factor: Float, pan: Offset = Offset.Zero) {
        val newScale = (scale * factor).coerceIn(1f, MAX_ZOOM)
        val applied = newScale / scale
        val rel = focus - Offset(size.width / 2f, size.height / 2f)
        val raw = rel - (rel - offset) * applied + pan
        // Keep the image covering the page while panning.
        val maxX = size.width * (newScale - 1f) / 2f
        val maxY = size.height * (newScale - 1f) / 2f
        offset = Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
        scale = newScale
        if (scale <= 1.01f) reset()
    }

    suspend fun toggle(tap: Offset) {
        if (zoomed) {
            val startScale = scale
            val startOffset = offset
            animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                scale = startScale + (1f - startScale) * t
                offset = startOffset * (1f - t)
            }
            reset()
        } else {
            var last = 1f
            animate(1f, DOUBLE_TAP_ZOOM, animationSpec = tween(220)) { v, _ ->
                zoomAround(tap, v / last)
                last = v
            }
        }
    }

    private companion object {
        const val MAX_ZOOM = 8f
        const val DOUBLE_TAP_ZOOM = 2.5f
    }
}

/** Applies [state]'s zoom on top of any other layer transforms. */
fun Modifier.zoomTransform(state: ZoomState, active: Boolean, baseScale: () -> Float = { 1f }): Modifier =
    graphicsLayer {
        val z = if (active) state.scale else 1f
        scaleX = baseScale() * z
        scaleY = baseScale() * z
        if (active) {
            translationX = state.offset.x
            translationY = state.offset.y
        }
    }

/**
 * Tap toggles the surrounding UI, double tap zooms, pinch zooms, drag pans while zoomed.
 * Unzoomed one-finger drags pass through so the pager can swipe and the viewer can swipe-dismiss.
 */
fun Modifier.zoomGestures(
    state: ZoomState,
    active: Boolean,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
): Modifier = this
    .pointerInput(state, active) {
        if (!active) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2 || state.zoomed) {
                    state.zoomAround(event.calculateCentroid(), event.calculateZoom(), event.calculatePan())
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(state, active) {
        coroutineScope {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { tap -> if (active) launch { state.toggle(tap) } },
                onLongPress = onLongPress?.let { cb -> { _: Offset -> cb() } },
            )
        }
    }
