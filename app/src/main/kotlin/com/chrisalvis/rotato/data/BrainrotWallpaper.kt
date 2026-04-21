package com.chrisalvis.rotato.data

data class BrainrotWallpaper(
    val id: String,
    val source: String,
    val thumbUrl: String,
    val sampleUrl: String,  // medium quality (~850px) for grid display
    val fullUrl: String,    // original resolution for full-screen / wallpaper
    val resolution: String,
    val pageUrl: String,
    val tags: List<String>
)
