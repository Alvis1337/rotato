package com.chrisalvis.rotato.wear

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen view of the phone's wallpaper with Previous, Save and Next. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NowStore.load(this)
        setContent { MaterialTheme { NowScreen() } }
    }

    @Composable
    private fun NowScreen() {
        val now by NowStore.state.collectAsState()
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }

        // Catch up on anything sent while the app was closed, and ask the phone for a fresh copy.
        LaunchedEffect(Unit) {
            NowStore.refresh(this@MainActivity)
            if (!PhoneLink.send(this@MainActivity, WatchPaths.SYNC) && now?.hasImage != true) {
                status = "Phone not connected"
            }
        }
        // A new picture arriving ends the "working" state of Next / Previous.
        LaunchedEffect(now?.updatedAt) { busy = false }

        val bitmap = remember(now?.updatedAt, now?.hasImage) {
            now?.image?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
        }

        fun send(path: String, label: String, waitsForImage: Boolean) {
            scope.launch {
                val sent = withContext(Dispatchers.IO) { PhoneLink.send(this@MainActivity, path) }
                status = if (sent) label else "Phone not connected"
                busy = sent && waitsForImage
                delay(2_500)
                status = null
                delay(10_000)
                busy = false
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = now?.caption?.ifBlank { null } ?: "Phone wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
                    )
                )
            } else {
                Text(
                    text = if (now?.updatedAt == 0L || now == null) "Open Rotato on your phone to see its wallpaper here"
                    else "This wallpaper can't be shown on the watch",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.padding(horizontal = 28.dp).padding(bottom = 48.dp),
                )
            }

            if (busy) CircularProgressIndicator(Modifier.size(36.dp), indicatorColor = Color.White)

            Column(
                Modifier.fillMaxSize().padding(bottom = 14.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val caption = status ?: now?.caption.orEmpty()
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.caption2,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Control(Icons.Filled.SkipPrevious, "Previous") { send(WatchPaths.PREVIOUS, "Going back…", true) }
                    Control(Icons.Filled.FavoriteBorder, "Save", large = true) { send(WatchPaths.SAVE, "Saving…", false) }
                    Control(Icons.Filled.SkipNext, "Next") { send(WatchPaths.NEXT, "Next wallpaper…", true) }
                }
            }
        }
    }

    @Composable
    private fun Control(icon: ImageVector, label: String, large: Boolean = false, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.size(if (large) ButtonDefaults.DefaultButtonSize else ButtonDefaults.SmallButtonSize),
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}
