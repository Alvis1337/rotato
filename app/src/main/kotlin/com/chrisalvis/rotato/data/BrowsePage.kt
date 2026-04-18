package com.chrisalvis.rotato.data

data class BrowsePage(
    val wallpapers: List<BrowseWallpaper>,
    val page: Int,
    val pages: Int
)
