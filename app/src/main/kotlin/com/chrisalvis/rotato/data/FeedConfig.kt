package com.chrisalvis.rotato.data

data class FeedConfig(
    val id: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val name: String,
    val lastSyncMs: Long = 0L,
    /** null = use /api/brainrot (live search); non-null = use /api/feed/[slug]?random=true */
    val serverSlug: String? = null
)
