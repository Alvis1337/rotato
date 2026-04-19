package com.chrisalvis.rotato.data

data class ServerConfig(
    val sorting: String = "relevance",
    val minResolution: String = "1920x1080",
    val aspectRatio: String = "",
    val nsfwMode: Boolean = false,
    val searchSuffix: String = "",
    val feedApiKey: String = "",
    val malClientId: String = "",
    val malClientSecret: String = "",
    val redirectUri: String = ""
)

data class SourceRow(
    val name: String,
    val enabled: Boolean,
    val apiKey: String,
    val apiUser: String
)

data class ServerFeed(
    val id: Int,
    val slug: String,
    val name: String
)
