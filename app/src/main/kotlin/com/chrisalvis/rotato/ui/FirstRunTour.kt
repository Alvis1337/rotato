package com.chrisalvis.rotato.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisalvis.rotato.data.supportsFoldPairs
import kotlinx.coroutines.delay

/**
 * A few quick tips the first time each main screen opens. Seen tips are remembered on the
 * device; Settings › About & Data can bring them back.
 */
object FirstRunTour {
    private const val PREFS = "first_run_tour"

    fun seen(context: Context, screen: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(screen, false)

    fun markSeen(context: Context, screen: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(screen, true).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    internal fun tipsFor(context: Context, route: String?): List<String> = when (route) {
        "discover" -> buildList {
            add("Tap any image to open it. Save, Set, Share and Skip sit in the dock at the bottom.")
            add("Swipe up on an open image to see its tags, size and where it came from.")
            add("Skipped one by accident? Tap Undo and it comes back where it was.")
            if (supportsFoldPairs(context)) add("On your foldable, Pair sets one image for the cover screen and another for the inside.")
        }
        "home" -> listOf(
            "This is your Library: everything Rotato rotates through.",
            "Tap a colour swatch to show only wallpapers in that colour, or Match a photo to find ones that go with any picture.",
            "Settings › Rotation has the Rotato live wallpaper: crossfades, video wallpapers and double-tap for next.",
        )
        "browse" -> listOf(
            "Collections hold the images you save from Discover.",
            "A collection's menu can share it as a small file. Only links and tags go in it, never your settings or keys.",
            "Got a collection from a friend? Use the import button at the top to add it.",
        )
        "taste" -> listOf(
            "Rotato learns from what you save, set and skip.",
            "Rate tags from Love to Never to steer what Discover shows you.",
        )
        else -> emptyList()
    }
}

@Composable
internal fun FirstRunTourOverlay(route: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val screen = route ?: return
    val tips = remember(screen) { FirstRunTour.tipsFor(context, screen) }
    if (tips.isEmpty()) return
    var visible by remember(screen) { mutableStateOf(false) }
    var index by remember(screen) { mutableIntStateOf(0) }
    LaunchedEffect(screen) {
        if (FirstRunTour.seen(context, screen)) return@LaunchedEffect
        delay(700) // let the screen settle first
        visible = true
    }
    fun finish() {
        visible = false
        FirstRunTour.markSeen(context, screen)
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
            ) {
                Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Quick tip", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(end = 8.dp)) {
                            tips.indices.forEach { i ->
                                Box(
                                    Modifier
                                        .size(if (i == index) 7.dp else 5.dp)
                                        .background(Color.White.copy(alpha = if (i == index) 0.95f else 0.35f), CircleShape)
                                )
                            }
                        }
                    }
                    AnimatedContent(targetState = index, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { i ->
                        Text(
                            tips[i],
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (index < tips.lastIndex) {
                            TextButton(onClick = { finish() }) { Text("Skip", color = Color.White.copy(alpha = 0.7f)) }
                            TextButton(onClick = { index++ }) { Text("Next") }
                        } else {
                            TextButton(onClick = { finish() }) { Text("Got it") }
                        }
                    }
                }
            }
        }
    }
}
