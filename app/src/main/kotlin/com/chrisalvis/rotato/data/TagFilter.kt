package com.chrisalvis.rotato.data

import android.content.Context
import kotlinx.coroutines.flow.first

/**
 * One spelling for tag comparisons. Boorus write "hatsune_miku" while Zerochan and Wallhaven
 * write "Hatsune Miku"; without this a tag blocked from one source slipped through from another.
 */
fun normalizeTag(tag: String): String = tag.trim().lowercase().replace(' ', '_')

fun BrainrotWallpaper.hasAnyTag(normalizedTags: Set<String>): Boolean =
    normalizedTags.isNotEmpty() && tags.any { normalizeTag(it) in normalizedTags }

/** Tags and URLs that must never be added: the global blacklist, "Never" tiers and blocked images. */
class ContentBlocklist(val tags: Set<String>, val urls: Set<String>) {
    fun blocks(wp: BrainrotWallpaper): Boolean =
        wp.hasAnyTag(tags) || (urls.isNotEmpty() && (wp.fullUrl in urls || wp.thumbUrl in urls))

    companion object {
        suspend fun load(context: Context, nsfw: Boolean): ContentBlocklist {
            val prefs = RotatoPreferences(context)
            val taste = TastePreferences(context)
            val tiers = taste.sfwTagTiers.first() + if (nsfw) taste.nsfwTagTiers.first() else emptyMap()
            val never = tiers.filterValues { it == TagTier.NEVER }.keys
            val tags = (prefs.globalBlacklist.first() + never).mapTo(HashSet(), ::normalizeTag)
            return ContentBlocklist(tags, prefs.blockedUrls.first())
        }
    }
}
