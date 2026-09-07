package app.tuji.android.core.design

import androidx.compose.ui.unit.dp

/**
 * Spacing scale — 6 steps. Ported from `Tuji/Core/Theme/Space.swift`.
 *
 * The old scale had 13 steps, which is the same as having none: any value can
 * be found somewhere in it, so a layout never has to make a decision and the
 * page ends up with no rhythm. Six steps force every gap to be a choice.
 *
 * Two hard rules, and they carry over to Compose unchanged:
 *  - Page margin is always [S4] (24). There is no second horizontal boundary in
 *    the app — content aligns straight to it, and that line is the skeleton the
 *    whole layout hangs on.
 *  - At most 4 distinct spacing values per screen. More than that means the
 *    hierarchy has not been worked out.
 *
 * `dp` rather than `sp`: these are distances, not text. Only [TujiTypography]
 * uses `sp`, so a user's font-size setting grows the words without also growing
 * every gap between them.
 */
object TujiSpace {
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 16.dp

    /** Page margin. The one horizontal boundary in the app. */
    val S4 = 24.dp

    /** Between sections. */
    val S5 = 40.dp
    val S6 = 64.dp
}
