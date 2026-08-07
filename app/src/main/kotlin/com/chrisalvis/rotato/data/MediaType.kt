package com.chrisalvis.rotato.data

/** Detects whether a post URL points to playable video rather than a static image. */
object MediaType {
    private val videoExts = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".gifv", ".m4v")

    fun isVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val clean = url.substringBefore('?').substringBefore('#')
        if (videoExts.any { clean.endsWith(it, ignoreCase = true) }) return true
        // Reddit-hosted video posts (v.redd.it) often have no file extension in the URL.
        if (clean.contains("v.redd.it", ignoreCase = true)) return true
        return false
    }
}
