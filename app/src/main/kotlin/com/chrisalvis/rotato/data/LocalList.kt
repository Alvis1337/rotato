package com.chrisalvis.rotato.data

import java.util.UUID

data class LocalList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class LocalWallpaperEntry(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val sourceId: String,
    val source: String,
    val thumbUrl: String,
    val fullUrl: String,
    val resolution: String,
    val pageUrl: String,
    val tags: List<String>,
    val addedAt: Long = System.currentTimeMillis()
)
