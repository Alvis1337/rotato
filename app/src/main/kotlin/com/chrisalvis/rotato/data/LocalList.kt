package com.chrisalvis.rotato.data

import java.util.UUID

enum class ScreenRotationTarget(val label: String) {
    BOTH("Home & Lock"),
    HOME_ONLY("Home only"),
    LOCK_ONLY("Lock only"),
}

/**
 * Tag-based rules for smart/dynamic collections.
 *
 * A wallpaper matches when:
 *   - ALL tags in [requireAll] are present (AND), AND
 *   - AT LEAST ONE tag in [requireAny] is present if the list is non-empty (OR), AND
 *   - NONE of the tags in [excludeAny] are present (NOT)
 */
data class SmartRule(
    val requireAll: List<String> = emptyList(),
    val requireAny: List<String> = emptyList(),
    val excludeAny: List<String> = emptyList(),
) {
    fun matches(entry: LocalWallpaperEntry): Boolean {
        // Include source name as a virtual tag so rules can match by source (e.g. "wallhaven", "reddit")
        val entryTags = (entry.tags + listOf(entry.source)).map { it.lowercase() }.filter { it.isNotBlank() }
        if (requireAll.isNotEmpty() && !requireAll.all { req ->
            entryTags.any { it.contains(req.lowercase()) }
        }) return false
        if (requireAny.isNotEmpty() && !requireAny.any { req ->
            entryTags.any { it.contains(req.lowercase()) }
        }) return false
        if (excludeAny.isNotEmpty() && excludeAny.any { exc ->
            entryTags.any { it.contains(exc.lowercase()) }
        }) return false
        return true
    }

    val isEmpty: Boolean get() = requireAll.isEmpty() && requireAny.isEmpty() && excludeAny.isEmpty()
}

data class LocalList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** When true, all wallpapers in this collection auto-download to the Library rotation pool. */
    val useAsRotation: Boolean = false,
    val rotationTarget: ScreenRotationTarget = ScreenRotationTarget.BOTH,
    /** When true, the collection is hidden unless unlocked with biometrics this session. */
    val isLocked: Boolean = false,
    val coverUrl: String = "",
    /** Non-null for smart/dynamic collections — wallpapers auto-populate when they match the rule. */
    val smartRule: SmartRule? = null,
) {
    val isSmartCollection: Boolean get() = smartRule != null && !smartRule.isEmpty
}

data class LocalWallpaperEntry(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val sourceId: String,
    val source: String,
    val thumbUrl: String,
    val fullUrl: String,
    val resolution: String,
    val pageUrl: String,
    val tags: List<String>,
    val addedAt: Long = System.currentTimeMillis()
)
