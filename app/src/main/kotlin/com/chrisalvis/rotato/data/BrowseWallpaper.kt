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
    val resolution: String = "",
    val isVideo: Boolean = false,
    val isNsfw: Boolean = false,
    /** The post's page on its source site, when known. */
    val pageUrl: String = "",
) {
    /** What to copy or share: the post's page (artist, tags, related) when known, else the image. */
    val shareLink: String
        get() = pageUrl.takeIf { it.startsWith("http") } ?: fullUrl.takeIf { it.startsWith("http") }.orEmpty()
}
