package com.chrisalvis.rotato.data

data class LocalSource(
    val pluginId: String,
    /** For multi-instance sources (Reddit): the subreddit name. Empty for all other sources. */
    val instanceId: String = "",
    val enabled: Boolean = false,
    val apiKey: String = "",
    val apiUser: String = "",
    val tags: String = "",
    /** Wallhaven only — purity bitmask string */
    val wallhavenPurity: String = "110",
    val nsfwEnabled: Boolean? = null,
    /** Optional base URL override for user-configured instances. Blank = use manifest default. */
    val baseUrl: String = "",
    val extraConfig: Map<String, String> = emptyMap(),
)

