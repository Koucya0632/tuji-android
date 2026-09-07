package app.tuji.android.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner radius — 2 steps, and one of them is zero. Ported from
 * `Tuji/Core/Theme/Radius.swift`.
 *
 * On iOS, zeroing the radius is what stops the app reading as a stock iOS app.
 * The same argument holds here with the platform swapped: Material 3's default
 * shapes are 8/12/16dp corners, and taking them would make Tuji read as a stock
 * Material app. [R0] is therefore wired into the Material theme's whole shape
 * set in [TujiTheme], not merely available for call sites to remember.
 *
 * [Pill] is reserved for the three things that "speak": avatars (a person),
 * status dots (a live signal), and the mascot's speech bubble. Roundness is
 * therefore unique on screen, and shares its geometry with the cat.
 */
object TujiRadius {
    /**
     * Everything structural: lists, buttons, inputs, sheets, tab bar, image
     * containers, badges, chips, segmented controls, dialogs.
     */
    val R0 = 0.dp

    /** Avatars, status dots, the mascot's speech bubble. Nothing else. */
    val Pill = 999.dp

    val Shape0 = RoundedCornerShape(R0)
    val ShapePill = RoundedCornerShape(Pill)
}
