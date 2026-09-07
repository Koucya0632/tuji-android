package app.tuji.android.core.design

import androidx.compose.ui.graphics.Color

/**
 * Color tokens — 紙與墨 (Paper & Ink). Ported 1:1 from
 * `tuji-ios/Tuji/Core/Theme/TujiColor.swift`; the hex values are the same
 * numbers, and this is the only file in the app allowed to spell one.
 *
 * The system has exactly six meanings, and every colour belongs to one of them.
 * A colour that cannot be written into one of these sentences does not enter
 * the system:
 *
 *   紙 (paper) — the ground. Three steps, expressing region hierarchy.
 *   墨 (ink)   — text, and "this one is selected / this one is primary".
 *   品牌 (brand) — Tuji's identity. Yellow is primary; cocoa is the supporting ground.
 *   現在 (current) — Where the next step is, where you are, what is running.
 *   積累 (accumulation) — Mastery, completion, learned, published, streak.
 *   alert      — errors and destructive actions.
 *
 * Depth is expressed by changing the ground, never by shadow — there is no
 * shadow token. Focus and selection use [TujiBorder] widths instead, which on
 * Android also means never reaching for Material's `elevation`.
 */
object TujiColor {
    // 紙 — carries every "region" signal

    /** App background. The default ground for all content. */
    val Paper = Color(0xFFFBF7EF)

    /** Secondary region: pressed state, input field, skeleton, unselected chip, image container. */
    val Paper2 = Color(0xFFF2ECE0)

    /** Tertiary: disabled ground, lowest heatmap step, progress track. */
    val Paper3 = Color(0xFFE5DCCB)

    // 墨 — text and the only depth layer

    /** Primary text **and** dark-block ground. One role, not two. */
    val Ink = Color(0xFF191512)

    /** Secondary text; tappable text that is not selected. */
    val Ink2 = Color(0xFF4A4239)

    /** Tertiary text, captions, placeholders, disabled text. */
    val Ink3 = Color(0xFF8A8073)

    /** The only line. 1dp, for list separators and the scrolled nav bar underline. */
    val Rule = Color(0xFFD9D0C0)

    // 品牌 — identity, independent from UI state

    /**
     * Tuji's primary brand colour, taken from the mascot's eyes. Brand surfaces
     * and the app's primary action share this value today, but use different
     * semantic tokens so either can evolve without recolouring the other.
     */
    val BrandPrimary = Color(0xFFF5C84B)
    val BrandPrimaryPressed = Color(0xFFC79A1E)

    /** Warm supporting brand ground, shared with the app icon background. */
    val BrandSecondary = Color(0xFF59483D)

    // 現在 — current focus and action

    /** Selected tab indicator, active progress, check mark, focus ring and search hit. */
    val Current = BrandPrimary

    /** Pressed state for current-coloured elements; text under 16sp on a current ground. */
    val CurrentDeep = BrandPrimaryPressed

    // 積累 — learned value, not brand identity

    /**
     * Means only 你的積累 — never a generic button, nav, link or heading. A
     * screen with no accumulation colour means the user has accumulated nothing
     * here yet, which is the correct thing to show.
     */
    val Accumulation = Color(0xFF5A718A)

    /** High mastery ground; text on the soft accumulation ground. */
    val AccumulationDeep = Color(0xFF40566D)

    /** Low mastery ground, and the accumulation signal on ink surfaces. */
    val AccumulationSoft = Color(0xFFDDE5EC)

    // 警示

    /** Errors, deletion, destructive actions, review rejection. */
    val Alert = Color(0xFFD8452B)

    // 遮罩

    /** Behind sheets and dialogs. */
    val Scrim = Color(0xFF191512).copy(alpha = 0.4f)
}
