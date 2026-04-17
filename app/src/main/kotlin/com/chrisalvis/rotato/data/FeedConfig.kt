package com.chrisalvis.rotato.data

data class FeedConfig(
    val id: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val name: String,
    val lastSyncMs: Long = 0L
)
