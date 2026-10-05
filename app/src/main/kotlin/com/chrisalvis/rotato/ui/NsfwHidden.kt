package com.chrisalvis.rotato.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True while the content filter in Settings hides every NSFW feature. Screens check it to leave
 * out NSFW toggles, lock controls, and NSFW images; off, the app behaves exactly as before.
 */
val LocalNsfwHidden = staticCompositionLocalOf { false }
