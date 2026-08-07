package com.chrisalvis.rotato.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tracks whether a single NSFW-blurred tile has been revealed this session (resets on recomposition/restart). */
@Composable
fun rememberNsfwRevealed(key: Any): androidx.compose.runtime.MutableState<Boolean> =
    remember(key) { mutableStateOf(false) }

/**
 * Draws an NSFW blur+scrim overlay when [isNsfw] and [blurEnabled] are true and it hasn't been
 * revealed yet; a no-op otherwise. Pair with [nsfwContentBlur] on the underlying content, and
 * route the caller's tap handler through the same isBlurred check so a blurred tap reveals
 * instead of firing the normal click.
 */
@Composable
fun NsfwBlurLayer(
    isNsfw: Boolean,
    blurEnabled: Boolean,
    revealed: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isBlurred = isNsfw && blurEnabled && !revealed
    if (!isBlurred) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = "NSFW — tap to reveal",
                tint = Color.White,
                modifier = Modifier.padding(bottom = if (compact) 2.dp else 4.dp)
            )
            if (!compact) {
                Text(
                    "Tap to reveal",
                    color = Color.White,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Blur modifier applied to the underlying image/video content itself, alongside [NsfwBlurLayer]'s scrim. */
fun Modifier.nsfwContentBlur(isNsfw: Boolean, blurEnabled: Boolean, revealed: Boolean): Modifier =
    if (isNsfw && blurEnabled && !revealed) this.blur(22.dp) else this
