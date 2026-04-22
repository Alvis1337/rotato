package com.chrisalvis.rotato.data

import java.util.UUID

data class LocalList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** When true, all wallpapers in this collection auto-download to the Library rotation pool. */
    val useAsRotation: Boolean = false,
    /** When true, the collection is hidden unless unlocked with biometrics this session. */
    val isLocked: Boolean = false
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
