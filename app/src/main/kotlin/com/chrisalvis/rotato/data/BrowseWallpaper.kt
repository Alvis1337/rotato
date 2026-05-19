package com.chrisalvis.rotato.data

data class BrowseWallpaper(
    val sourceId: String,
    val entryId: String = "",
    val fullUrl: String,
    val sampleUrl: String = "",
    val thumbUrl: String,
    val animeTitle: String,
    /** "device" for locally-uploaded images, otherwise the source plugin name. */
    val source: String = "",
    val tags: List<String> = emptyList(),
    val resolution: String = ""
)
