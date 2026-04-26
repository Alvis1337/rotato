package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

fun sanitizeFilename(s: String): String = s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)

fun loadScaledBitmap(context: Context, path: String): Bitmap? {
    val metrics = context.resources.displayMetrics
    val targetWidth = metrics.widthPixels
    val targetHeight = metrics.heightPixels
    val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, boundsOnly)
    var sampleSize = 1
    var w = boundsOnly.outWidth
    var h = boundsOnly.outHeight
    while (w / 2 >= targetWidth && h / 2 >= targetHeight) {
        w /= 2; h /= 2; sampleSize *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}
