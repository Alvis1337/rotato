package com.chrisalvis.rotato.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chrisalvis.rotato.RotatoApp
import com.chrisalvis.rotato.data.LocalList

/**
 * The collections a picker may name. A locked collection only shows while it's unlocked for this
 * session, and never while the content filter ("Hide NSFW features") is on, the same rule the
 * Saved tab and Discover's save menus already follow.
 */
fun List<LocalList>.visibleInPickers(unlocked: Set<String>, nsfwHidden: Boolean): List<LocalList> =
    filter { !it.isLocked || (!nsfwHidden && it.id in unlocked) }

/** [lists] filtered with [visibleInPickers], following the session unlocks and the content filter. */
@Composable
fun rememberPickerLists(lists: List<LocalList>): List<LocalList> {
    val app = LocalContext.current.applicationContext as RotatoApp
    val unlocked by app.unlockedListIds.collectAsStateWithLifecycle()
    val nsfwHidden by app.nsfwHidden.collectAsStateWithLifecycle()
    return remember(lists, unlocked, nsfwHidden) { lists.visibleInPickers(unlocked, nsfwHidden) }
}

/**
 * Label for an already chosen collection id: its name when it's visible, a neutral
 * "Locked collection" when it's a hidden locked one, or [fallback] when nothing matches.
 */
fun pickerLabelFor(
    id: String?,
    all: List<LocalList>,
    visible: List<LocalList>,
    fallback: String,
): String {
    if (id.isNullOrEmpty()) return fallback
    visible.firstOrNull { it.id == id }?.let { return it.name }
    return if (all.any { it.id == id }) "Locked collection" else fallback
}
