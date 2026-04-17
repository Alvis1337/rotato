package com.chrisalvis.rotato.data

data class FeedConfig(
    val id: String,
    val url: String,
    val apiKey: String,
    val name: String,
    val lastSyncMs: Long = 0L
)
