package com.chrisalvis.rotato.data

import kotlin.math.abs

enum class MinResolution(val label: String, val width: Int, val height: Int) {
    ANY("Any", 0, 0),
    HD("HD  (1280×720)", 1280, 720),
    FHD("FHD (1920×1080)", 1920, 1080),
    QHD("QHD (2560×1440)", 2560, 1440),
    UHD("4K  (3840×2160)", 3840, 2160),
    // Dynamic — actual screen pixels stored in BrainrotFilters.phoneScreenWidth/phoneScreenHeight
    MY_PHONE("My Phone", -1, -1),
}

enum class AspectRatio(
    val label: String,
    /** Value passed to Wallhaven's `ratios` param */
    val wallhavenKey: String,
    val widthParts: Int,
    val heightParts: Int,
) {
    ANY("Any", "", 0, 0),
    ULTRAWIDE("Ultrawide (21:9)", "21x9", 21, 9),
    WIDE("Wide (16:9)", "16x9", 16, 9),
    WIDE_10("Wide (16:10)", "16x10", 16, 10),
    STANDARD("Standard (4:3)", "4x3", 4, 3),
    PORTRAIT("Portrait (9:16)", "9x16", 9, 16),
    // Dynamic — ratio stored in BrainrotFilters.phoneWidthParts/phoneHeightParts
    MY_PHONE("My Phone", "", -1, -1),
}

data class BrainrotFilters(
    val minResolution: MinResolution = MinResolution.ANY,
    val aspectRatio: AspectRatio = AspectRatio.ANY,
    val phoneWidthParts: Int = 0,
    val phoneHeightParts: Int = 0,
    val phoneScreenWidth: Int = 0,
    val phoneScreenHeight: Int = 0,
    val useMalFilter: Boolean = true,
)

/** Returns true if the image dimensions satisfy the resolution and ratio filters. */
fun BrainrotFilters.matches(width: Int, height: Int): Boolean {
    if (width <= 0 || height <= 0) return true // unknown dimensions — let it through
    when (minResolution) {
        MinResolution.ANY -> Unit
        MinResolution.MY_PHONE -> {
            if (phoneScreenWidth > 0 && phoneScreenHeight > 0) {
                if (width < phoneScreenWidth || height < phoneScreenHeight) return false
            }
        }
        else -> if (width < minResolution.width || height < minResolution.height) return false
    }
    when (aspectRatio) {
        AspectRatio.ANY -> Unit
        AspectRatio.MY_PHONE -> {
            if (phoneWidthParts > 0 && phoneHeightParts > 0) {
                val expected = phoneWidthParts.toDouble() / phoneHeightParts
                val actual = width.toDouble() / height
                if (abs(actual - expected) / expected > 0.05) return false
            }
        }
        else -> {
            val expected = aspectRatio.widthParts.toDouble() / aspectRatio.heightParts
            val actual = width.toDouble() / height
            if (abs(actual - expected) / expected > 0.05) return false
        }
    }
    return true
}
