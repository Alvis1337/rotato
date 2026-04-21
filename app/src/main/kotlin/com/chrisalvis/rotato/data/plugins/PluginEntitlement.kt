package com.chrisalvis.rotato.data.plugins

/**
 * Checks whether a given plugin is unlocked for the current user.
 *
 * Today: all plugins are unlocked (stub). Phase 4 will integrate Google Play Billing:
 * each premium plugin maps to a Play product ID (e.g. "source_wallhaven") and a bundle
 * SKU ("source_all") that unlocks everything. Receipt verification happens server-side or
 * via the Play Billing library's purchase token.
 */
object PluginEntitlement {

    // TODO (Phase 4): inject BillingClient and cache purchase state per product ID.

    /**
     * Returns true if the user may use [plugin].
     * Free plugins always return true. Premium plugins return true until billing is wired up.
     */
    fun isUnlocked(plugin: SourcePlugin): Boolean {
        if (!plugin.isPremium) return true
        // Placeholder: grant access to all premium plugins until IAP is integrated.
        return true
    }

    /** Play Store product ID for a single premium source unlock. */
    fun productIdFor(plugin: SourcePlugin): String = "source_${plugin.id.lowercase()}"

    /** Bundle SKU that unlocks all premium sources at once. */
    const val BUNDLE_PRODUCT_ID = "source_all"
}
