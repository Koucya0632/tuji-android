package app.tuji.android.core.design

import androidx.compose.ui.unit.dp

/**
 * Border widths — 3 steps. Ported from `Tuji/Core/Theme/Border.swift`.
 *
 * With no corner radius and no shadow, focus and selection need a signal that
 * does not depend on shape, so it comes from a stroke instead.
 *
 * Default state has no border. A border appears only for focus, selection, or
 * warning — if a resting element has one, something is wrong.
 */
object TujiBorder {
    /** The [TujiColor.Rule] separator. The only "line" in the app. */
    val Bw1 = 1.dp

    /** Focus ring ([TujiColor.Current]), check-mark stroke. */
    val Bw2 = 2.dp

    /**
     * Selection indicator: tab top edge, multi-select row leading edge, sheet
     * top edge, status label leading edge, progress bars.
     */
    val Bw3 = 3.dp
}
