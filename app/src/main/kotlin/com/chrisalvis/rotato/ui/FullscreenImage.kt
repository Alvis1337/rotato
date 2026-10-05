package com.chrisalvis.rotato.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * Full-screen image with honest loading feedback. While the full image downloads, the low-res
 * preview already in memory ([placeholderKey]) is shown blurred under a progress ring with a
 * percentage, so it never passes for the final image; a failure shows a message and Retry
 * instead of a blank black screen.
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
                .then(if (loading && Build.VERSION.SDK_INT >= 31) Modifier.blur(12.dp) else Modifier)
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
            loading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress }, color = Color.White, modifier = Modifier.size(40.dp))
                } else {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
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
