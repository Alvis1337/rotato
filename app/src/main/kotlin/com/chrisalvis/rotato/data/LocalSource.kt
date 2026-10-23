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

/**
 * Cleans up pasted credentials. Rule34 and Gelbooru show them on the account page as one string,
 * "&api_key=…&user_id=…", which pasted into either field used to be sent as the key itself and
 * fail. Either field may hold that string; the pieces it names win.
 * Returns the API key and user ID.
 */
fun normalizeCredentials(apiKey: String, apiUser: String): Pair<String, String> {
    fun param(name: String): String? = listOf(apiKey, apiUser).firstNotNullOfOrNull { field ->
        Regex("(?:^|[&?\\s])$name=([^&\\s]+)").find(field.trim())?.groupValues?.get(1)
    }
    val key = param("api_key") ?: apiKey.trim()
    val user = param("user_id") ?: apiUser.trim()
    return key to user
}
