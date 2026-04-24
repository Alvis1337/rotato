package com.chrisalvis.rotato.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chrisalvis.rotato.data.BrainrotWallpaper

private val INTERVAL_OPTIONS = listOf(3, 5, 10, 30)

@Composable
fun HandsFreeOverlay(
    items: List<BrainrotWallpaper>,
    intervalSecs: Int,
    onIntervalChange: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return

    BackHandler(onBack = onDismiss)

    val pagerState = rememberPagerState { items.size }
    var isPaused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    // Auto-advance timer — restarts whenever page fully settles, pause state, or interval changes
    LaunchedEffect(pagerState.settledPage, isPaused, intervalSecs) {
        if (isPaused) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = intervalSecs * 1000, easing = LinearEasing)
        )
        // Animation ran to completion (not cancelled) — advance page
        val next = pagerState.settledPage + 1
        if (next < items.size) pagerState.animateScrollToPage(next)
    }

    // Trigger load more when approaching the end
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage >= items.size - 3) onLoadMore()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            AsyncImage(
                model = item.fullUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isPaused = !isPaused }
            )
        }

        // Bottom controls bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interval cycle button
                TextButton(onClick = {
                    val next = INTERVAL_OPTIONS[(INTERVAL_OPTIONS.indexOf(intervalSecs) + 1) % INTERVAL_OPTIONS.size]
                    onIntervalChange(next)
                }) {
                    Text("${intervalSecs}s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                // Pause / resume
                IconButton(onClick = { isPaused = !isPaused }) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Exit
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Exit hands-free",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
