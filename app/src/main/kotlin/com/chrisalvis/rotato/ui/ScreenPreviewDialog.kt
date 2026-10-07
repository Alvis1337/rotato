package com.chrisalvis.rotato.ui

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * "Preview on my screens": the image framed the way smart crop would frame it on each of this
 * phone's screens (both panels on a foldable), with a clock on top so it reads like a real home
 * screen. Nothing is set until the user taps Set.
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
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("On your screens") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Framed around the subject, the way Rotato's smart crop would set it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val img = image
                if (img == null) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        screens.forEachIndexed { i, s ->
                            val aspect = s.width.toFloat() / s.height
                            val label = when {
                                foldable && i == 0 -> "Outer"
                                foldable && i == screens.lastIndex -> "Inner"
                                screens.size == 1 -> "Your screen"
                                else -> "${s.width} × ${s.height}"
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(aspect)) {
                                PhoneFrame(img, aspect, time)
                                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onSet != null) TextButton(onClick = { onSet(); onDismiss() }, enabled = image != null) { Text("Set wallpaper") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PhoneFrame(img: PreviewImage, aspect: Float, time: String) {
    val crop = remember(img, aspect) { focusedCrop(img.bitmap.width, img.bitmap.height, aspect, img.focus) }
    val bitmap = remember(img) { img.bitmap.asImageBitmap() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(14.dp))
            .border(3.dp, Color(0xFF1C1C1C), RoundedCornerShape(14.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                bitmap,
                srcOffset = IntOffset(crop.left, crop.top),
                srcSize = IntSize(crop.width(), crop.height()),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
        Text(
            time,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
        )
    }
}
