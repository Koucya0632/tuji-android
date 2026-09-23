package app.tuji.android.study

import android.graphics.BlurMaskFilter
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A sentence drawn out of focus — 聽句's question before the ear has earned it.
 *
 * The blur leaves the shape of the words (how many lines, where they break)
 * without leaving a letter legible, which is what makes 顯示例句 mean something.
 * It is 12dp rather than something gentler for that reason.
 *
 * **Why this is not `Modifier.blur`.** That needs `RenderEffect`, which is API
 * 31, and `minSdk` is 29 — and on 29 and 30 it does not throw or warn, it
 * silently draws the text sharp. A listening question that prints its own
 * answer is not a degraded question, it is a broken one, and it looks perfectly
 * normal to anyone testing on a current device. The previous answer here
 * replaced every glyph with `█` on those two levels, which held the invariant
 * but is a different *effect* from iOS's: a redaction, not a sentence you
 * cannot quite read.
 *
 * So the blur is drawn rather than composited: `BlurMaskFilter` has existed
 * since API 1 and works on every device this installs on.
 *
 * **Compose still does the layout.** The line breaking comes from a real
 * [TextMeasurer] run with the same style and width the legible [androidx.compose.material3.Text]
 * gets, and only the *painting* of each line is handed to a native paint — so
 * the blurred shape is the true shape of the sentence, and the Latin/CJK
 * cascade [app.tuji.android.core.design.TujiTypefaces] builds is the one that
 * draws it. Re-laying the text out with a bare `StaticLayout` would have been a
 * second layout engine disagreeing with the first about where a line ends.
 *
 * The bitmap is software-backed on purpose: a mask filter on a hardware canvas
 * is supported only from API 28 and only for some primitives, and "supported on
 * most devices" is not a thing to hang an answer-hiding invariant on.
 */
@Composable
fun BlurredSentence(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    radius: Dp = BLUR_RADIUS,
) {
    val measurer = rememberTextMeasurer()
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val typeface = remember(style.fontFamily, style.fontWeight, style.fontStyle) {
        @Suppress("UNCHECKED_CAST")
        (
            resolver.resolve(
                fontFamily = style.fontFamily ?: FontFamily.Default,
                fontWeight = style.fontWeight ?: FontWeight.Normal,
                fontStyle = style.fontStyle ?: FontStyle.Normal,
                fontSynthesis = FontSynthesis.All,
            ).value as? Typeface
            ) ?: Typeface.DEFAULT
    }

    Canvas(modifier) {
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = style.copy(textAlign = TextAlign.Center),
            constraints = Constraints(maxWidth = size.width.roundToInt()),
        )
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { style.fontSize.toPx() }
            color = style.color.toArgb()
            // Compose spells tracking in sp; a paint spells it in ems.
            style.letterSpacing.takeIf { it.type == TextUnitType.Sp && it.value != 0f }?.let {
                letterSpacing = with(density) { it.toPx() } / textSize
            }
            maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        drawBlurred(text, layout, paint, radius.toPx())
    }
}

/**
 * Paint the laid-out lines into a software bitmap, then stamp it down.
 *
 * The bitmap is grown by the blur radius on every side: a mask filter spreads
 * past the glyphs, and a bitmap sized to the text alone clips the halo into a
 * hard edge — which is a rectangle around the sentence, the one shape the
 * effect must not have.
 */
private fun DrawScope.drawBlurred(
    text: String,
    layout: TextLayoutResult,
    paint: TextPaint,
    radiusPx: Float,
) {
    val pad = (radiusPx * 2).roundToInt()
    val width = layout.size.width + pad * 2
    val height = layout.size.height + pad * 2
    if (width <= 0 || height <= 0) return

    val bitmap = ImageBitmap(width, height).asAndroidBitmap()
    val canvas = android.graphics.Canvas(bitmap)
    for (line in 0 until layout.lineCount) {
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        if (end <= start) continue
        canvas.drawText(
            text,
            start,
            end,
            layout.getLineLeft(line) + pad,
            layout.getLineBaseline(line) + pad,
            paint,
        )
    }
    drawImage(
        bitmap.asImageBitmap(),
        topLeft = Offset(
            x = (size.width - layout.size.width) / 2f - pad,
            y = (size.height - layout.size.height) / 2f - pad,
        ),
    )
}

/**
 * iOS: `.blur(radius: 12)`. Enough to say "there is a sentence here" without
 * leaving one letter legible.
 */
val BLUR_RADIUS = 12.dp
