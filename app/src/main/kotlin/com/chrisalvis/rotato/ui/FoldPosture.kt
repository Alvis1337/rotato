package com.chrisalvis.rotato.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.constrainWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Screen y-coordinate (px) of the top of the hinge while a foldable sits half-open in tabletop
 * posture (fold running horizontally across the screen), or null in any other posture.
 */
@Composable
fun rememberTabletopFoldTop(): Int? {
    val activity = LocalContext.current.findActivity() ?: return null
    val layoutInfo: WindowLayoutInfo? by remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }.collectAsStateWithLifecycle(initialValue = null)
    val fold = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull {
            it.state == FoldingFeature.State.HALF_OPENED &&
                it.orientation == FoldingFeature.Orientation.HORIZONTAL
        }
        ?: return null
    // Feature bounds are in the activity window's coordinates; shift them onto the screen so
    // content in a dialog window (whose origin differs) can compare against them too.
    val windowOrigin = IntArray(2)
    activity.window?.decorView?.getLocationOnScreen(windowOrigin)
    return fold.bounds.top + windowOrigin[1]
}

/**
 * Tabletop mode: when the device is half-folded with the hinge across the screen, keeps this
 * element (an image or video) above the crease so it isn't bent in half, leaving the bottom
 * half for the controls laid out around it. A no-op in every other posture.
 */
@Composable
fun Modifier.aboveTabletopFold(): Modifier {
    val foldTop = rememberTabletopFoldTop() ?: return this
    val view = LocalView.current
    var topOnScreen by remember { mutableIntStateOf(0) }
    return this
        .onGloballyPositioned { coords ->
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            topOnScreen = origin[1] + coords.positionInWindow().y.roundToInt()
        }
        .layout { measurable, constraints ->
            val available = foldTop - topOnScreen
            if (!constraints.hasBoundedHeight || available <= 0 || available >= constraints.maxHeight) {
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = minOf(constraints.minHeight, available),
                        maxHeight = available,
                    )
                )
                // Keep the full slot so surrounding layout doesn't shift; content sits on top.
                layout(constraints.constrainWidth(placeable.width), constraints.maxHeight) {
                    placeable.place(0, 0)
                }
            }
        }
}
