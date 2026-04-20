package com.chrisalvis.rotato.data

enum class SourceType(
    val displayName: String,
    val needsApiKey: Boolean,
    val needsApiUser: Boolean,
    val apiKeyLabel: String = "API Key",
    val apiUserLabel: String = "API User",
    val safeContent: Boolean = true
) {
    DANBOORU(
        displayName = "Danbooru",
        needsApiKey = true,
        needsApiUser = true,
        apiKeyLabel = "API Key",
        apiUserLabel = "Username",
        safeContent = false
    ),
    GELBOORU(
        displayName = "Gelbooru",
        needsApiKey = true,
        needsApiUser = true,
        apiKeyLabel = "API Key",
        apiUserLabel = "User ID",
        safeContent = false
    ),
    SAFEBOORU(
        displayName = "Safebooru",
        needsApiKey = false,
        needsApiUser = false,
        safeContent = true
    ),
    WALLHAVEN(
        displayName = "Wallhaven",
        needsApiKey = true,
        needsApiUser = false,
        apiKeyLabel = "API Key",
        safeContent = true
    ),
    KONACHAN(
        displayName = "Konachan",
        needsApiKey = false,
        needsApiUser = false,
        safeContent = true
    ),
    YANDERE(
        displayName = "Yande.re",
        needsApiKey = false,
        needsApiUser = false,
        safeContent = false
    )
}

data class LocalSource(
    val type: SourceType,
    val enabled: Boolean = false,
    val apiKey: String = "",
    val apiUser: String = "",
    val tags: String = ""
)
