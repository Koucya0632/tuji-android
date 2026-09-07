package app.tuji.android.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * The control group for the CJK cascade.
 *
 * The spike it belongs to can only be judged by looking, so what it shows has
 * to make a wrong answer *visible* rather than merely absent. This project has
 * already paid once for a spike without a control — the Cloudflare Workers
 * experiment, where a self-inflicted fixture error nearly read as "the platform
 * cannot do this".
 *
 * Three rows in the same size, differing only in which faces are in play:
 *
 *  - **token** — what the app actually uses. Plus Jakarta for Latin,
 *    GenSenRounded for CJK, via [TujiTypefaces.family].
 *  - **latin only** — Plus Jakarta with no fallback declared, so its CJK is
 *    whatever the *platform* substitutes (Noto Sans CJK). This is what the app
 *    would look like if the cascade silently did nothing.
 *  - **cjk only** — GenSenRounded as the primary face, so its Latin is
 *    GenSenRounded's own: a Source Sans derivative that is neither rounded nor
 *    Plus Jakarta. This is the outcome ADR-0003 exists to prevent, and the one
 *    easiest to ship by accident.
 *
 * Read it this way: the Latin in **token** must match **latin only** and must
 * *not* match **cjk only**. The CJK in **token** must match **cjk only** and
 * must *not* match **latin only**. Anything else means the cascade is not doing
 * what its name says.
 */
@Composable
fun CjkCascadeProbe(
    modifier: Modifier = Modifier,
    sample: String = "Tuji 圖鑑 abc 日本語 v1.1.2",
) {
    val type = TujiType
    val face = LocalTujiFace.current

    val latinOnly = remember { FontFamily(Font(R.font.plusjakartasans_bold)) }
    val cjkOnly = remember(face) {
        val res = if (face == TujiFace.JP) R.font.gensenrounded2jp_b else R.font.gensenrounded2tw_b
        FontFamily(Font(res))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        ProbeRow("token", sample, type.h2)
        ProbeRow("latin only", sample, type.h2.copy(fontFamily = latinOnly))
        ProbeRow("cjk only", sample, type.h2.copy(fontFamily = cjkOnly))
        Text(
            "cascade: Typeface.CustomFallbackBuilder",
            style = type.monoLabel,
            color = TujiColor.Ink3,
        )
    }
}

@Composable
private fun ProbeRow(label: String, text: String, style: TextStyle) {
    Text(label, style = TujiType.monoLabel.copy(fontSize = 11.sp), color = TujiColor.Ink3)
    Text(text, style = style, color = TujiColor.Ink)
}
