package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** The shared-transition layout around the app's pages, when there is one. */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedTransitions = compositionLocalOf<SharedTransitionScope?> { null }

/** The page this content belongs to, as it slides in or out. */
internal val LocalPageVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Makes this element fly to (or from) the element with the same [key] on the next page,
 * like the route thumbnail growing into a drive's map. Does nothing outside the page layout.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedElementBounds(key: String): Modifier {
    val shared = LocalSharedTransitions.current ?: return this
    val page = LocalPageVisibility.current ?: return this
    return with(shared) {
        this@sharedElementBounds.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = page,
            boundsTransform = { _: Rect, _: Rect -> spring(dampingRatio = 0.85f, stiffness = 380f) },
        )
    }
}

internal fun driveMapKey(tripId: Long) = "drive-map-$tripId"
