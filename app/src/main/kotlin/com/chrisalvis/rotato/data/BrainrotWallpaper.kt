package com.chrisalvis.rotato.data

data class BrainrotWallpaper(
    val id: String,
    val source: String,
    val thumbUrl: String,
    val sampleUrl: String,  // medium quality (~850px) for grid display
    val fullUrl: String,    // original resolution for full-screen / wallpaper
    val resolution: String,
    val pageUrl: String,
    val tags: List<String>,
    val isVideo: Boolean = false,
    val isNsfw: Boolean = false
) {
    /**
     * Image the Discover masonry grid shows. Sources without a mid-size sample (Wallhaven)
     * report the original as sampleUrl; tiles then downloaded multi-megabyte 4K originals and
     * often timed out, so those use the thumbnail instead.
     */
    val gridUrl: String
        get() = if (sampleUrl == fullUrl && thumbUrl.isNotBlank() && !MediaType.isVideoUrl(thumbUrl)) thumbUrl
        else sampleUrl.ifBlank { fullUrl }

    /** Image for a Discover tile in data saver mode: the small preview when there is one. */
    val dataSaverUrl: String
        get() = thumbUrl.takeIf { it.isNotBlank() && !MediaType.isVideoUrl(it) } ?: gridUrl

    /** Small static preview, when it differs from [gridUrl], to show while that loads. */
    val lowResPreviewUrl: String?
        get() = thumbUrl.takeIf { it.isNotBlank() && it != gridUrl && !MediaType.isVideoUrl(it) }
}
