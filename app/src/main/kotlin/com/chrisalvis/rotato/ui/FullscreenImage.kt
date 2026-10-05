package com.chrisalvis.rotato.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
        targetValue = if (loading && slow) 14.dp else 0.dp,
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
