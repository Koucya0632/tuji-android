package app.tuji.android.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Font tokens — 8 steps. Ported from `Tuji/Core/Theme/TujiFont.swift`; the
 * point sizes are the same numbers, spelled in `sp`.
 *
 * `sp` rather than `dp` is how Dynamic Type comes across: iOS applies
 * `UIFontMetrics.scaledFont` by hand inside `TujiTypeface.font`, and Android
 * does the same scaling for free as long as text sizes are `sp` and nothing
 * else is. [TujiSpace] is `dp` for exactly that reason.
 *
 * 12sp was removed from the scale on purpose: CJK strokes merge at that size,
 * and the 12→14 step was too small to survive Dynamic Type — the hierarchy
 * collapsed when the user enlarged text. The smallest size here is 13.
 */
@Immutable
class TujiTypography internal constructor(
    private val regular: FontFamily,
    private val semiBold: FontFamily,
    private val bold: FontFamily,
    private val extraBold: FontFamily,
    private val mono: FontFamily,
) {
    /** The word being learned; the number on a completion screen. */
    val display = TextStyle(fontFamily = extraBold, fontSize = 56.sp)

    /** Screen title — in the content flow, not in the top app bar. */
    val h1 = TextStyle(fontFamily = bold, fontSize = 34.sp)

    /** Section heading, sheet title. */
    val h2 = TextStyle(fontFamily = bold, fontSize = 24.sp)

    /** List row primary text, button text, segmented control text. */
    val h3 = TextStyle(fontFamily = bold, fontSize = 18.sp)

    /** Body copy, example sentences, descriptions. */
    val body = TextStyle(fontFamily = regular, fontSize = 16.sp)

    /**
     * Body copy that has to carry weight: a button's label, a row's title, a
     * status word. This axis is why 84 iOS call sites were bypassing the scale
     * and rendering in the system face instead.
     */
    val bodyStrong = TextStyle(fontFamily = semiBold, fontSize = 16.sp)

    /** Secondary explanation, row subtitles. */
    val bodySm = TextStyle(fontFamily = regular, fontSize = 14.sp)
    val bodySmStrong = TextStyle(fontFamily = semiBold, fontSize = 14.sp)

    /**
     * Badges, status labels, section overlines, tab labels.
     * The scale assumes +4% letter spacing, so it is baked in here rather than
     * left for each call site to remember.
     */
    val label = TextStyle(fontFamily = bold, fontSize = 13.sp, letterSpacing = 0.5.sp)

    /** IPA, UID, system codes. Latin-only by definition, so no CJK cascade. */
    val monoLabel = TextStyle(fontFamily = mono, fontSize = 13.sp)

    /**
     * The word being learned, at the one size every screen sets it.
     *
     * Sized by its caller rather than fixed here, because ruby is derived from
     * this size and the two have to stay in proportion — see [TujiHeadwordSize],
     * which owns the number. Bold rather than [display]'s ExtraBold: this is a
     * 26sp word, and a heavy CJK face closes up at that size.
     */
    fun headword(size: TextUnit): TextStyle = TextStyle(fontFamily = bold, fontSize = size)

    /**
     * The kana over a headword. Regular, because at half the headword's size
     * even Bold starts filling in its own counters.
     */
    fun headwordRuby(size: TextUnit): TextStyle = TextStyle(fontFamily = regular, fontSize = size)

    /** Body at a chosen emphasis, mirroring iOS's `Font.tujiBody(_:)`. */
    fun body(emphasis: TujiEmphasis): TextStyle =
        if (emphasis == TujiEmphasis.Strong) bodyStrong else body

    fun bodySm(emphasis: TujiEmphasis): TextStyle =
        if (emphasis == TujiEmphasis.Strong) bodySmStrong else bodySm
}

/**
 * One size for every headword on every screen.
 *
 * A size only short words got to keep is not a hierarchy: at 56sp, 洗剤 would be
 * set twice as large as シャワーカーテンポール purely because it is shorter. 26 is
 * where that stops.
 *
 * It is also the floor. Ruby is half the headword and the CJK face stops
 * resolving below 13sp, so 26 is the smallest headword that can carry legible
 * kana.
 */
object TujiHeadwordSize {
    val Base = 26.sp
    const val RUBY_RATIO = 0.5f

    /** Smallest kana the CJK face still resolves; see [TujiTypography]. */
    val MinimumRubyPoint = 13.sp
}
