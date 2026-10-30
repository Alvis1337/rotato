package com.chrisalvis.rotato.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.chrisalvis.rotato.data.findFocusPoint
import com.chrisalvis.rotato.data.focusedCrop
import com.chrisalvis.rotato.data.imageSourceFor
import com.chrisalvis.rotato.data.isFoldable
import com.chrisalvis.rotato.data.knownDisplaySizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private class PreviewImage(val bitmap: Bitmap, val focus: PointF)

/**
 * "Try it on": the image full screen, framed the way smart crop would set it, under a lock screen
 * clock or a home screen's icons. On a foldable either screen can be previewed. Nothing is set
 * until the user taps Set.
 */
@Composable
internal fun ScreenPreviewDialog(
    imageUrl: String,
    onSet: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val screens = remember { knownDisplaySizes(context).sortedBy { it.width.toFloat() / it.height } }
    val foldable = remember { isFoldable(context) && screens.size > 1 }
    val image by produceState<PreviewImage?>(null, imageUrl) {
        value = withContext(Dispatchers.IO) {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(imageSourceFor(context, imageUrl))
                    .size(1600)
                    .allowHardware(false)
                    .build()
            )
            val bmp = ((result as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: return@withContext null
            PreviewImage(bmp, runCatching { findFocusPoint(bmp) }.getOrDefault(PointF(0.5f, 0.5f)))
        }
    }
    val now = remember { Date() }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT).format(now) }
    val date = remember { java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(now) }
    // Start on the screen the phone is showing right now.
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val currentAspect = config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)
    var screenIndex by remember {
        mutableIntStateOf(screens.indices.minByOrNull { i ->
            kotlin.math.abs(screens[i].width.toFloat() / screens[i].height - minOf(currentAspect, 1f / currentAspect))
        } ?: 0)
    }
    var homeMode by remember { mutableStateOf(false) }
    // Tap the picture to hide the buttons and see it clean.
    var chrome by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { chrome = !chrome },
            contentAlignment = Alignment.Center,
        ) {
            val img = image
            val screen = screens.getOrNull(screenIndex)
            if (img == null || screen == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                val aspect = screen.width.toFloat() / screen.height
                TryOnScreen(img, aspect, time, date, homeMode)
            }

            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.statusBarsPadding().padding(top = 8.dp),
                ) {
                    Segmented(listOf("Lock screen", "Home screen"), if (homeMode) 1 else 0) { homeMode = it == 1 }
                    if (screens.size > 1) {
                        Segmented(
                            screens.indices.map { i ->
                                when {
                                    foldable && i == 0 -> "Folded"
                                    foldable && i == screens.lastIndex -> "Unfolded"
                                    else -> "${screens[i].width} × ${screens[i].height}"
                                }
                            },
                            screenIndex,
                        ) { screenIndex = it }
                    }
                }
            }
            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    PillButton("Done", filled = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                    if (onSet != null) {
                        PillButton("Set wallpaper", filled = true, enabled = image != null, modifier = Modifier.weight(2f)) {
                            onSet(); onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) Color(0xFF0B0B0F) else Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            color = if (filled) Color(0xFF0B0B0F) else Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The picture framed the way smart crop would set it on a screen of [aspect], with a lock screen
 * clock or a home screen's icons drawn over it. Fills the window when the shapes match, and is
 * letterboxed as a phone-shaped frame when previewing the other screen of a foldable.
 */
@Composable
private fun TryOnScreen(img: PreviewImage, aspect: Float, time: String, date: String, home: Boolean) {
    val crop = remember(img, aspect) { focusedCrop(img.bitmap.width, img.bitmap.height, aspect, img.focus) }
    val bitmap = remember(img) { img.bitmap.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val windowAspect = maxWidth / maxHeight
        val fills = kotlin.math.abs(windowAspect - aspect) < 0.03f
        Box(
            Modifier
                .then(if (fills) Modifier.fillMaxSize() else Modifier.padding(horizontal = 16.dp, vertical = 120.dp).aspectRatio(aspect))
                .then(if (fills) Modifier else Modifier.clip(RoundedCornerShape(20.dp)).border(3.dp, Color(0xFF2A2A2A), RoundedCornerShape(20.dp)))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    bitmap,
                    srcOffset = IntOffset(crop.left, crop.top),
                    srcSize = IntSize(crop.width(), crop.height()),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
            if (home) HomeOverlay(time) else LockOverlay(time, date, big = fills)
        }
    }
}

@Composable
private fun LockOverlay(time: String, date: String, big: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = if (big) 150.dp else 36.dp),
    ) {
        Text(date, color = Color.White, fontSize = if (big) 16.sp else 12.sp, fontWeight = FontWeight.Medium)
        Text(time, color = Color.White, fontSize = if (big) 84.sp else 44.sp, fontWeight = FontWeight.Light, lineHeight = if (big) 90.sp else 48.sp)
    }
}

/** A stand-in home screen: a clock, rows of app icons and a dock, as soft translucent shapes. */
@Composable
private fun HomeOverlay(time: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(time, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                repeat(4) { AppIcon() }
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            repeat(4) { AppIcon() }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.35f))
        )
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
private fun AppIcon() {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.35f))
    )
}
