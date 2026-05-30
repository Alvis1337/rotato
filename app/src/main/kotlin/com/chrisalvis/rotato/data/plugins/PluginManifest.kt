package com.chrisalvis.rotato.data.plugins

import org.json.JSONObject

enum class Protocol {
    GELBOORU, DANBOORU, MOEBOORU, WALLHAVEN, REDDIT, ZEROCHAN;
    companion object {
        fun fromString(s: String) = entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
    }
}

sealed class PluginAuth {
    object None : PluginAuth()
    data class ApiKey(val label: String = "API Key", val required: Boolean = false) : PluginAuth()
    data class ApiKeyUserId(
        val keyLabel: String = "API Key",
        val userLabel: String = "User ID",
        val required: Boolean = false,
    ) : PluginAuth()
}

/**
 * Declarative description of a wallpaper source. Parsed from JSON (bundled assets or remote URL).
 * The [PluginExecutor] maps [protocol] to the correct [PluginEngine] at fetch time.
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String = "1.0",
    val author: String = "Rotato",
    val description: String = "",
    val protocol: Protocol,
    val defaultBaseUrl: String,
    val instanceUrlConfigurable: Boolean = false,
    val safeContent: Boolean = true,
    val isPremium: Boolean = false,
    val auth: PluginAuth = PluginAuth.None,
    /** URL this manifest was loaded from; null for bundled manifests. */
    val sourceUrl: String? = null,
    /**
     * Protocol-specific extra configuration. Engines read these to handle API variants.
     * e.g. GelbooruEngine reads "response" ("object"|"array"), "count" ("json"|"xml"|"random"),
     *      "imageUrl" ("file_url"|"safebooru"), "ratingTag" ("general"|"safe")
     */
    val extras: Map<String, String> = emptyMap(),
) {
    val needsApiKey: Boolean get() = auth is PluginAuth.ApiKey || auth is PluginAuth.ApiKeyUserId
    val needsApiUser: Boolean get() = auth is PluginAuth.ApiKeyUserId
    val apiKeyLabel: String get() = when (auth) {
        is PluginAuth.ApiKey -> auth.label
        is PluginAuth.ApiKeyUserId -> auth.keyLabel
        else -> "API Key"
    }
    val apiUserLabel: String get() = when (auth) {
        is PluginAuth.ApiKeyUserId -> auth.userLabel
        else -> "User ID"
    }
    val requiresCredentials: Boolean get() = when (auth) {
        is PluginAuth.ApiKey -> auth.required
        is PluginAuth.ApiKeyUserId -> auth.required
        else -> false
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("version", version)
        put("author", author)
        put("description", description)
        put("protocol", protocol.name)
        put("defaultBaseUrl", defaultBaseUrl)
        put("instanceUrlConfigurable", instanceUrlConfigurable)
        put("safeContent", safeContent)
        put("isPremium", isPremium)
        val authObj = JSONObject()
        when (val a = auth) {
            is PluginAuth.None -> authObj.put("type", "none")
            is PluginAuth.ApiKey -> {
                authObj.put("type", "api_key")
                authObj.put("keyLabel", a.label)
                authObj.put("required", a.required)
            }
            is PluginAuth.ApiKeyUserId -> {
                authObj.put("type", "api_key_user_id")
                authObj.put("keyLabel", a.keyLabel)
                authObj.put("userLabel", a.userLabel)
                authObj.put("required", a.required)
            }
        }
        put("auth", authObj)
        if (sourceUrl != null) put("sourceUrl", sourceUrl)
        if (extras.isNotEmpty()) {
            val extrasObj = JSONObject()
            extras.forEach { (k, v) -> extrasObj.put(k, v) }
            put("extras", extrasObj)
        }
    }

    companion object {
        fun fromJson(json: JSONObject, sourceUrl: String? = null): PluginManifest? {
            val id = json.optString("id").ifBlank { return null }
            val name = json.optString("name").ifBlank { return null }
            val protocol = Protocol.fromString(json.optString("protocol")) ?: return null
            val defaultBaseUrl = json.optString("defaultBaseUrl").ifBlank { return null }
            val authObj = json.optJSONObject("auth")
            val auth: PluginAuth = when (authObj?.optString("type")) {
                "api_key" -> PluginAuth.ApiKey(
                    label = authObj.optString("keyLabel", "API Key"),
                    required = authObj.optBoolean("required", false),
                )
                "api_key_user_id" -> PluginAuth.ApiKeyUserId(
                    keyLabel = authObj.optString("keyLabel", "API Key"),
                    userLabel = authObj.optString("userLabel", "User ID"),
                    required = authObj.optBoolean("required", false),
                )
                else -> PluginAuth.None
            }
            return PluginManifest(
                id = id,
                name = name,
                version = json.optString("version", "1.0"),
                author = json.optString("author", "Unknown"),
                description = json.optString("description", ""),
                protocol = protocol,
                defaultBaseUrl = defaultBaseUrl,
                instanceUrlConfigurable = json.optBoolean("instanceUrlConfigurable", false),
                safeContent = json.optBoolean("safeContent", true),
                isPremium = json.optBoolean("isPremium", false),
                auth = auth,
                sourceUrl = sourceUrl ?: json.optString("sourceUrl").takeUnless { it.isBlank() },
                extras = json.optJSONObject("extras")?.let { ext ->
                    buildMap { ext.keys().forEach { k -> put(k, ext.optString(k)) } }
                } ?: emptyMap(),
            )
        }
    }
}
