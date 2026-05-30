package com.chrisalvis.rotato.data.plugins

object PluginEntitlement {

    fun isUnlocked(manifest: PluginManifest): Boolean {
        if (!manifest.isPremium) return true
        return true
    }

    fun productIdFor(manifest: PluginManifest): String = "source_${manifest.id.lowercase()}"

    const val BUNDLE_PRODUCT_ID = "source_all"
}
