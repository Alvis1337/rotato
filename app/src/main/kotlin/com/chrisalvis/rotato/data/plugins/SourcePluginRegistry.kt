package com.chrisalvis.rotato.data.plugins

/**
 * Canonical registry of all available source plugins.
 *
 * Free plugins are available to all users. Premium plugins are listed here and shown
 * in the UI, but require an entitlement check via [PluginEntitlement] before fetching.
 *
 * To add a new source: implement [SourcePlugin], add it to [all], done.
 */
object SourcePluginRegistry {

    val all: List<SourcePlugin> = listOf(
        SafebooruPlugin,   // free — works without an account, SFW only
        WallhavenPlugin,   // premium
        DanbooruPlugin,    // premium
        GelbooruPlugin,    // premium
        KonachanPlugin,    // premium
        YanderePlugin,     // premium
    )

    fun findById(id: String): SourcePlugin? = all.firstOrNull { it.id == id }

    /** Convenience: look up by the legacy SourceType enum name (same as plugin id). */
    fun forType(type: com.chrisalvis.rotato.data.SourceType): SourcePlugin? = findById(type.name)
}
