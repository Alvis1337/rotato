package com.chrisalvis.rotato.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Colour names the Library can filter by. [hue] is the centre of the range in degrees; the
 * neutral ones (black, white, grey) are picked by brightness and saturation instead.
 */
enum class ImageColour(val label: String, val swatch: Long, val hue: Float? = null) {
    RED("Red", 0xFFE53935, 0f),
    ORANGE("Orange", 0xFFFB8C00, 30f),
    YELLOW("Yellow", 0xFFFDD835, 55f),
    GREEN("Green", 0xFF43A047, 120f),
    TEAL("Teal", 0xFF00ACC1, 180f),
    BLUE("Blue", 0xFF1E88E5, 220f),
    PURPLE("Purple", 0xFF8E24AA, 275f),
    PINK("Pink", 0xFFEC407A, 330f),
    BLACK("Dark", 0xFF212121),
    WHITE("Light", 0xFFF5F5F5),
}

/** What Rotato knows about an image's look: average brightness (0–255) and its main colours. */
data class ImageLook(val brightness: Int, val colours: Set<ImageColour>)

/**
 * Brightness and colour analysis for local (rotation pool) images, cached by file name and
 * modification time so each image is decoded only once. Used by night-aware rotation and the
 * Library's colour filter.
 */
object ImageAnalysis {
    private const val PREFS = "image_looks"

    /** Images at or below this average brightness count as dark. */
    const val DARK_THRESHOLD = 105

    fun lookFor(context: Context, file: File): ImageLook? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = file.name
        val stamp = file.lastModified()
        sp.getString(key, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                if (o.optLong("m") == stamp) {
                    val colours = o.optString("c").split(',').mapNotNull { n -> ImageColour.entries.find { it.name == n } }.toSet()
                    return ImageLook(o.optInt("b"), colours)
                }
            }
        }
        val look = analyse(file) ?: return null
        sp.edit().putString(
            key,
            JSONObject().put("m", stamp).put("b", look.brightness).put("c", look.colours.joinToString(",") { it.name }).toString()
        ).apply()
        return look
    }

    /** Looks for every file, computing any that aren't cached yet. Call off the main thread. */
    fun looksFor(context: Context, files: List<File>): Map<String, ImageLook> =
        files.mapNotNull { f -> lookFor(context, f)?.let { f.name to it } }.toMap()

    private fun analyse(file: File): ImageLook? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 48) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return lookOf(bmp)
    }

    /** The look of any picture (e.g. a photo picked to match against), not cached. Call off the main thread. */
    fun lookForUri(context: Context, uri: Uri): ImageLook? = try {
        decodeSmall(context, uri)?.let { lookOf(it) }
    } catch (e: Exception) {
        null
    }

    private fun decodeSmall(context: Context, uri: Uri): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 48) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    /**
     * How well a Library image's [look] goes with a [target] look: shared main colours count most,
     * then how close the brightness is. Null when they share no colour at all.
     */
    fun matchScore(target: ImageLook, look: ImageLook): Float? {
        val shared = target.colours.intersect(look.colours).size
        if (shared == 0) return null
        val brightness = 1f - kotlin.math.abs(target.brightness - look.brightness) / 255f
        return shared * 2f + brightness
    }

    /** Analyses (and recycles) a small decoded bitmap. */
    private fun lookOf(bmp: android.graphics.Bitmap): ImageLook {
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val hsv = FloatArray(3)
            var lumaSum = 0.0
            val weights = HashMap<ImageColour, Double>()
            for (c in px) {
                lumaSum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
                Color.colorToHSV(c, hsv)
                val (hue, sat, value) = Triple(hsv[0], hsv[1], hsv[2])
                val colour = when {
                    value < 0.2f -> ImageColour.BLACK
                    sat < 0.18f && value > 0.85f -> ImageColour.WHITE
                    sat < 0.18f -> null // grey: says little about the image's colour
                    else -> nearestHue(hue)
                }
                if (colour != null) {
                    // Vivid pixels say more about how an image reads than muted ones.
                    val weight = if (colour.hue != null) (sat * value).toDouble() + 0.15 else 0.6
                    weights[colour] = (weights[colour] ?: 0.0) + weight
                }
            }
            val total = weights.values.sum().takeIf { it > 0 } ?: 1.0
            // An image "is" a colour when that colour holds a real share of it; up to three.
            val colours = weights.entries
                .filter { it.value / total >= 0.18 }
                .sortedByDescending { it.value }
                .take(3)
                .mapTo(LinkedHashSet()) { it.key }
            return ImageLook((lumaSum / px.size).toInt().coerceIn(0, 255), colours)
        } finally {
            bmp.recycle()
        }
    }

    private fun nearestHue(hue: Float): ImageColour =
        ImageColour.entries.filter { it.hue != null }.minBy { c ->
            val d = kotlin.math.abs(hue - c.hue!!)
            minOf(d, 360f - d)
        }
}
