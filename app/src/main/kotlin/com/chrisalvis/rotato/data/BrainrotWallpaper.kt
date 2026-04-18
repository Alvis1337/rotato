package com.chrisalvis.rotato.data

data class BrainrotWallpaper(
    val id: String,
    val source: String,
    val thumbUrl: String,
    val fullUrl: String,
    val resolution: String,
    val pageUrl: String,
    val tags: List<String>
)
