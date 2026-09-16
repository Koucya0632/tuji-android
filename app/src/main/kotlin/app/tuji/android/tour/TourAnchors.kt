package app.tuji.android.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where each highlighted thing is, collected from wherever it is drawn.
 *
 * iOS carries these up the tree with an `anchorPreference`; Compose has no
 * preference system, so the equivalent is a map the shell owns and the marked
 * composables write into. Bounds are **in window space**, because the reader of
 * them is a full-screen overlay that is not in the same coordinate space as any
 * of the writers.
 */
class TourAnchors {
    private val bounds = mutableStateMapOf<TourTarget, Rect>()

    operator fun get(target: TourTarget): Rect? = bounds[target]

    /** The target's rect, or its step's second choice, or nothing. */
    fun resolve(step: TourStep): Rect? =
        step.target?.let { bounds[it] } ?: step.fallback?.let { bounds[it] }

    internal fun put(target: TourTarget, rect: Rect) {
        // A composable that leaves the tree stops updating rather than
        // clearing: the tab it was on may simply be off-screen mid-transition,
        // and dropping the rect then makes the hole vanish for a frame.
        bounds[target] = rect
    }
}

val LocalTourAnchors = compositionLocalOf<TourAnchors?> { null }

@Composable
fun ProvideTourAnchors(anchors: TourAnchors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTourAnchors provides anchors, content = content)
}

/**
 * Mark this composable as a tour target.
 *
 * A no-op when nothing is collecting, which is every launch after the first —
 * the modifier stays in the tree so the marks live next to what they mark,
 * rather than in a list somewhere that goes stale the day something moves.
 */
@Composable
fun Modifier.tourAnchor(target: TourTarget): Modifier {
    val anchors = LocalTourAnchors.current ?: return this
    return this.onGloballyPositioned { anchors.put(target, it.boundsInWindow()) }
}

/** Remembers one collector for the whole shell. */
@Composable
fun rememberTourAnchors(): TourAnchors = remember { TourAnchors() }
