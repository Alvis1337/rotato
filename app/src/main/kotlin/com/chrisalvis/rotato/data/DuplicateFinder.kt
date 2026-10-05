package com.chrisalvis.rotato.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File

/** A set of near-identical images: [keep] is the best copy, [duplicates] can be removed. */
data class DuplicateGroup(val keep: File, val duplicates: List<File>)

/**
 * Finds near-identical images among [files] with a 64-bit difference hash, which survives
 * resizing, recompression and small colour shifts (the same art saved from two sites). Images
 * whose hashes differ in at most [maxDistance] bits are grouped; each group keeps its
 * highest-resolution copy.
 */
fun findDuplicateImages(files: List<File>, maxDistance: Int = 6): List<DuplicateGroup> {
    val hashed = files.mapNotNull { file -> imageFingerprint(file)?.let { file to it } }
    val parent = IntArray(hashed.size) { it }
    fun find(i: Int): Int {
        var x = i
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }
    for (i in hashed.indices) {
        for (j in i + 1 until hashed.size) {
            if (java.lang.Long.bitCount(hashed[i].second.hash xor hashed[j].second.hash) <= maxDistance) {
                parent[find(j)] = find(i)
            }
        }
    }
    return hashed.indices
        .groupBy { find(it) }
        .values
        .filter { it.size > 1 }
        .map { members ->
            val sorted = members.map { hashed[it] }
                .sortedWith(compareByDescending<Pair<File, Fingerprint>> { it.second.pixels }.thenByDescending { it.first.length() })
            DuplicateGroup(keep = sorted.first().first, duplicates = sorted.drop(1).map { it.first })
        }
}

private class Fingerprint(val hash: Long, val pixels: Long)

private fun imageFingerprint(file: File): Fingerprint? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val small = Bitmap.createScaledBitmap(decoded, 9, 8, true)
    if (small !== decoded) decoded.recycle()
    val px = IntArray(9 * 8)
    small.getPixels(px, 0, 9, 0, 0, 9, 8)
    small.recycle()
    var hash = 0L
    var bit = 0
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            if (luma(px[y * 9 + x]) > luma(px[y * 9 + x + 1])) hash = hash or (1L shl bit)
            bit++
        }
    }
    // Near-flat images (solid colours, minimal gradients) all hash to almost the same value and
    // would be grouped though they differ; leave them out.
    val setBits = java.lang.Long.bitCount(hash)
    if (setBits <= 2 || setBits >= 62) return null
    return Fingerprint(hash, bounds.outWidth.toLong() * bounds.outHeight)
}

private fun luma(c: Int): Int = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
