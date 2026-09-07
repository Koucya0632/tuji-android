package app.tuji.android.core.design

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/**
 * Pairs a Latin face with the CJK face for the current interface language, the
 * way `TujiTypeface` does on iOS (ADR-0003).
 *
 * The rule being preserved: **the CJK face must not replace the Latin one.**
 * GenSenRounded 2 ships its own Latin — a Source Sans derivative that is
 * neither rounded nor Plus Jakarta — so setting a CJK font as *the* typeface
 * recolours every English word in the app as a side effect of fixing Chinese.
 * iOS avoids that with `UIFontDescriptor.cascadeList`: Plus Jakarta draws what
 * it can, and only the glyphs it lacks fall through.
 *
 * **This file is why `minSdk` is 29.** Android's equivalent of that cascade is
 * [Typeface.CustomFallbackBuilder], which is API 29, while the architecture
 * plan had chosen 26. On 26–28 there was no third option — only a wrong CJK
 * face (platform Noto instead of GenSenRounded) or a wrong Latin face
 * everywhere. The floor was raised rather than shipping either. See
 * `docs/SPIKE-FURIGANA.md` for the measurement that settled it.
 */
enum class TujiFace {
    TW,
    JP;

    companion object {
        /**
         * ja gets the JP face; every other interface language gets TW.
         *
         * zh-Hans knowingly gets Taiwan glyph forms — GenSenRounded has no SC
         * face, and coverage is complete so there is no tofu, only regionally
         * different stroke conventions. `en` has no CJK to place.
         */
        fun forUiLanguage(uiLang: String): TujiFace =
            if (uiLang.startsWith("ja")) JP else TW
    }
}

/** The Latin weights that are bundled. */
enum class LatinFace(@FontRes val res: Int) {
    Regular(R.font.plusjakartasans_regular),
    SemiBold(R.font.plusjakartasans_semibold),
    Bold(R.font.plusjakartasans_bold),
    ExtraBold(R.font.plusjakartasans_extrabold);

    /**
     * Two CJK weights are bundled, not the three ADR-0003 lists: no token asks
     * for Medium, and an unused CJK weight is 31 MB of nothing. Display is set
     * in ExtraBold and takes Bold here — GenSenRounded's Heavy would cost
     * another 30 MB for one token.
     */
    val wantsBoldCjk: Boolean
        get() = this == Bold || this == ExtraBold
}

/**
 * Whether a body step is carrying weight.
 *
 * Two cases, not a weight axis, because two is what is bundled: the CJK faces
 * ship in R and B only, so a third Latin weight would pair with the same
 * GenSenRounded Bold and buy nothing but a Latin-only distinction the Chinese
 * half cannot honour.
 */
enum class TujiEmphasis {
    Normal,
    Strong;

    val latin: LatinFace
        get() = when (this) {
            Normal -> LatinFace.Regular
            Strong -> LatinFace.SemiBold
        }
}

object TujiTypefaces {
    private val cache = HashMap<Key, FontFamily>()

    private data class Key(val latin: LatinFace, val face: TujiFace)

    /**
     * A [FontFamily] that draws Latin in [latin] and CJK in GenSenRounded.
     *
     * Cached because building a cascaded typeface parses two font files, and
     * these are asked for on every composition of every text style.
     */
    fun family(context: Context, latin: LatinFace, face: TujiFace): FontFamily =
        synchronized(cache) {
            cache.getOrPut(Key(latin, face)) { build(context, latin, face) }
        }

    /** The Latin-only face, for text that is Latin by definition (IPA, UID, codes). */
    val mono: FontFamily = FontFamily(Font(R.font.jetbrainsmono_regular))

    @FontRes
    private fun cjkRes(latin: LatinFace, face: TujiFace): Int =
        when (face) {
            TujiFace.TW -> if (latin.wantsBoldCjk) R.font.gensenrounded2tw_b else R.font.gensenrounded2tw_r
            TujiFace.JP -> if (latin.wantsBoldCjk) R.font.gensenrounded2jp_b else R.font.gensenrounded2jp_r
        }

    /**
     * Falls back to the bare Latin family if the cascade cannot be built.
     *
     * Not a version guard — `minSdk` 29 makes the API always present. It guards
     * a font file that fails to parse, and it degrades toward *Latin* on
     * purpose: losing the rounded CJK is a wrong face, losing Plus Jakarta is a
     * wrong app.
     */
    private fun build(context: Context, latin: LatinFace, face: TujiFace): FontFamily =
        runCatching { FontFamily(cascade(context, latin.res, cjkRes(latin, face))) }
            .getOrElse { FontFamily(Font(latin.res)) }

    private fun cascade(context: Context, @FontRes latinRes: Int, @FontRes cjkRes: Int): Typeface {
        val res = context.resources
        val latinFamily = android.graphics.fonts.FontFamily.Builder(
            android.graphics.fonts.Font.Builder(res, latinRes).build()
        ).build()
        val cjkFamily = android.graphics.fonts.FontFamily.Builder(
            android.graphics.fonts.Font.Builder(res, cjkRes).build()
        ).build()
        return Typeface.CustomFallbackBuilder(latinFamily)
            .addCustomFallback(cjkFamily)
            // Without this the builder appends no system chain at all, and
            // anything neither font covers — emoji, other scripts — draws as
            // tofu. Ours is added first, so Noto never wins a CJK glyph.
            .setSystemFallback("sans-serif")
            .build()
    }
}
