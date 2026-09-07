package app.tuji.android.core.design

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/**
 * Pairs a Latin face with the CJK face for the current interface language, the
 * way `TujiTypeface` does on iOS (ADR-0003).
 *
 * The rule being preserved: **the CJK face must not replace the Latin one.**
 * GenSenRounded 2 ships its own Latin — a Source Sans derivative that is
 * neither rounded nor Plus Jakarta — so setting a CJK font as the typeface
 * recolours every English word in the app as a side effect of fixing Chinese.
 * iOS avoids that with `UIFontDescriptor.cascadeList`: Plus Jakarta draws what
 * it can, and only the glyphs it lacks fall through.
 *
 * Android's equivalent is [Typeface.CustomFallbackBuilder], and it is **API 29**
 * while `minSdk` is 26. So there are two paths, and they are not equivalent:
 *
 *  - **API 29+** — a real cascade. Plus Jakarta first, GenSenRounded second.
 *    Same behaviour as iOS.
 *  - **API 26–28** — no cascade API exists. Plus Jakarta is set alone and the
 *    platform falls back to the *system* CJK face (Noto Sans CJK) for kanji and
 *    kana. Latin stays correct; Chinese and Japanese are drawn in the system
 *    face rather than GenSenRounded, so those two OS versions get a rounded-less
 *    but entirely legible CJK.
 *
 * That is a deliberate trade, not an oversight: the alternative on 26–28 is to
 * set GenSenRounded as the primary face, which trades a wrong CJK face for a
 * wrong *Latin* face across the whole app — the exact outcome ADR-0003 exists
 * to prevent. See `docs/SPIKE-FURIGANA.md` for what this actually looks like.
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

/** The Latin weights that are bundled. Two only — see [TujiEmphasis]. */
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
    /** Whether this device can express the Latin→CJK cascade at all. */
    val supportsCascade: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

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

    private fun build(context: Context, latin: LatinFace, face: TujiFace): FontFamily {
        if (!supportsCascade) return FontFamily(Font(latin.res))
        return runCatching { FontFamily(cascade(context, latin.res, cjkRes(latin, face))) }
            .getOrElse { FontFamily(Font(latin.res)) }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
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
            // Without this the builder appends the *system* fallback chain after
            // ours, which is what we want for emoji and scripts neither font
            // covers — but the CJK family has to come first or Noto wins.
            .setSystemFallback("sans-serif")
            .build()
    }
}
