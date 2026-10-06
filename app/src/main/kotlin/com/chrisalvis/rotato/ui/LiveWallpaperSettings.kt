package com.chrisalvis.rotato.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chrisalvis.rotato.live.LiveWallpaper

/**
 * Turns on Rotato's live wallpaper and its extras. Everything here is stored on the device by
 * [LiveWallpaper]; the toggles take effect on the home screen straight away.
 */
@Composable
internal fun LiveWallpaperSection() {
    val context = LocalContext.current
    var active by remember { mutableStateOf(LiveWallpaper.isActive(context)) }
    LifecycleResumeEffect(Unit) {
        active = LiveWallpaper.isActive(context)
        onPauseOrDispose { }
    }
    var crossfade by remember { mutableStateOf(LiveWallpaper.fadeMs(context) > 0) }
    var drift by remember { mutableStateOf(LiveWallpaper.drift(context)) }
    var parallax by remember { mutableStateOf(LiveWallpaper.parallax(context)) }
    var doubleTap by remember { mutableStateOf(LiveWallpaper.doubleTap(context)) }

    SettingsSection(title = "Live Wallpaper") {
        Text(
            if (active) "On. Wallpapers crossfade in, each screen is framed on its own, and videos from Discover can be set as wallpapers."
            else "Rotato can run as a live wallpaper: smooth crossfades, per-screen framing when you fold and unfold on any Android version, and video wallpapers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val open = {
            try {
                context.startActivity(LiveWallpaper.pickerIntent(context))
            } catch (e: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Toast.makeText(context, "Pick Rotato under Wallpaper › Live wallpapers", Toast.LENGTH_LONG).show()
                } catch (_: ActivityNotFoundException) { }
            }
        }
        if (active) {
            OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text("Preview live wallpaper") }
        } else {
            Button(onClick = open, modifier = Modifier.fillMaxWidth()) { Text("Use Rotato live wallpaper") }
        }
        SettingsToggleRow(
            title = "Crossfade",
            subtitle = "Blend into each new wallpaper instead of switching instantly",
            checked = crossfade,
            onCheckedChange = { crossfade = it; LiveWallpaper.setFadeMs(context, if (it) 900L else 0L) },
        )
        SettingsToggleRow(
            title = "Living stills",
            subtitle = "A very slow zoom and drift so still images never look frozen",
            checked = drift,
            onCheckedChange = { drift = it; LiveWallpaper.setDrift(context, it) },
        )
        SettingsToggleRow(
            title = "Tilt parallax",
            subtitle = "The image shifts a little as you tilt the phone, like a window. Uses the motion sensor only while the home screen is showing",
            checked = parallax,
            onCheckedChange = { parallax = it; LiveWallpaper.setParallax(context, it) },
        )
        SettingsToggleRow(
            title = "Double-tap for next",
            subtitle = "Double-tap an empty spot on the home screen to skip to the next wallpaper",
            checked = doubleTap,
            onCheckedChange = { doubleTap = it; LiveWallpaper.setDoubleTap(context, it) },
        )
    }
}
