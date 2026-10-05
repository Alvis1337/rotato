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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Smartphone
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

/** The wallpaper canvas covering every screen, or null on a single-screen phone. */
@Composable
fun rememberFoldCanvas(): com.chrisalvis.rotato.data.WallpaperCanvas? {
    val context = LocalContext.current
    return remember(context) {
        val app = context.applicationContext
        if (com.chrisalvis.rotato.data.isFoldable(app)) com.chrisalvis.rotato.data.wallpaperTargetSize(app) else null
    }
}

/** Small "Fold" pill marking images big enough to fill both screens. */
@Composable
fun FoldBadge(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        modifier = modifier,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Default.Smartphone,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(10.dp),
            )
            androidx.compose.material3.Text(
                "Fold",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
