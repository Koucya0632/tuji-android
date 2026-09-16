package app.tuji.android.gloss

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.GlossSpan
import app.tuji.android.core.model.SentenceAnnotation
import app.tuji.android.core.model.TargetLanguage

/**
 * An example sentence whose 實詞 and 片語 can be tapped.
 *
 * The sentence is one [Text] with a link per tappable 詞塊 rather than a row of
 * per-token views, which buys correct line breaking, Japanese 禁則, font scaling
 * and a screen reader that can reach each word — and keeps it a `Text`, which
 * matters at the call site because a custom layout in a `Row` beside a weighted
 * sibling negotiates for half the width it should have.
 *
 * **No host means no links.** A live word with nowhere to deliver its tap reads
 * as a broken feature rather than an absent one, so without a [GlossCardHost]
 * above it this is the plain sentence it has always been. The same is true when
 * the annotation does not re-spell the sentence: [SentenceAnnotation] discards
 * a partial set whole rather than underlining three words out of nine.
 *
 * The dotted rule under a tappable word is drawn here rather than set as a text
 * decoration, because Compose has one underline and it is solid. A dotted rule
 * is the dictionary's own gesture for "there is more here", and unlike a colour
 * change it survives a reader who cannot tell the two inks apart.
 */
@Composable
fun InteractiveSentenceText(
    sentence: String,
    spans: List<GlossSpan>?,
    language: TargetLanguage,
    modifier: Modifier = Modifier,
    style: TextStyle = TujiType.body,
    color: Color = TujiColor.Ink,
) {
    val selection = LocalGlossSelection.current
    val usable = remember(spans, sentence) { SentenceAnnotation.spans(spans, sentence) }
    if (selection == null || usable == null) {
        Text(sentence, style = style, color = color, modifier = modifier)
        return
    }

    val hostOrigin = LocalGlossHostOrigin.current
    val selected = selection.selectedIndex(sentence)
    val ranges = remember(usable) { spanRanges(usable) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    val annotated = remember(usable, selected, color) {
        annotate(usable, ranges, selected, color) { index ->
            selection.select(usable[index], index, sentence, language)
        }
    }

    // Reported from an effect rather than from the tap, so a sentence that
    // reflows — rotation, a font-scale change, a card opening above it — moves
    // the caret with the word instead of leaving it aimed at where the word
    // used to be.
    LaunchedEffect(layout, origin, hostOrigin, selected) {
        val result = layout ?: return@LaunchedEffect
        val index = selected ?: return@LaunchedEffect
        val range = ranges.getOrNull(index) ?: return@LaunchedEffect
        val box = union(result, range) ?: return@LaunchedEffect
        selection.report(
            box.translate(origin - hostOrigin),
            GlossSelection.Target(sentence, index),
        )
    }

    Box(modifier) {
        Text(
            annotated,
            style = style,
            color = color,
            onTextLayout = { layout = it },
            modifier = Modifier
                .onGloballyPositioned { origin = it.positionInWindow() }
                .drawWithContent {
                    drawContent()
                    val result = layout ?: return@drawWithContent
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(DOT.toPx(), GAP.toPx()),
                        0f,
                    )
                    // Above the content, so the rule stays visible under the
                    // 螢光筆 the selected word wears.
                    usable.forEachIndexed { index, span ->
                        if (!span.isTappable) return@forEachIndexed
                        val range = ranges[index]
                        lineBoxes(result, range).forEach { (rect, baseline) ->
                            val y = baseline + UNDERLINE_DROP.toPx()
                            drawLine(
                                color = TujiColor.Ink3,
                                start = Offset(rect.left, y),
                                end = Offset(rect.right, y),
                                strokeWidth = STROKE.toPx(),
                                pathEffect = dash,
                            )
                        }
                    }
                },
        )
    }
}

/** Where each span starts and ends in the sentence, as one pass. */
private fun spanRanges(spans: List<GlossSpan>): List<IntRange> {
    var at = 0
    return spans.map { span ->
        val start = at
        at += span.text.length
        start until at
    }
}

private fun annotate(
    spans: List<GlossSpan>,
    ranges: List<IntRange>,
    selected: Int?,
    color: Color,
    onTap: (Int) -> Unit,
): AnnotatedString = buildAnnotatedString {
    spans.forEach { append(it.text) }
    spans.forEachIndexed { index, span ->
        val range = ranges[index]
        if (span.isTappable) {
            addLink(
                LinkAnnotation.Clickable(
                    tag = index.toString(),
                    // Compose tints a link like a hyperlink unless told
                    // otherwise, and 紙與墨 has no accent-coloured text.
                    styles = TextLinkStyles(style = SpanStyle(color = color)),
                    linkInteractionListener = { onTap(index) },
                ),
                range.first,
                range.last + 1,
            )
        }
        if (index == selected) {
            // 螢光筆, not a fill: the ink underneath has to stay readable
            // through it, and it sits under the card's own scrim as well.
            addStyle(
                SpanStyle(background = TujiColor.BrandPrimary.copy(alpha = 0.45f)),
                range.first,
                range.last + 1,
            )
        }
    }
}

/** One box per line the span crosses, with that line's baseline. */
private fun lineBoxes(layout: TextLayoutResult, range: IntRange): List<Pair<Rect, Float>> {
    if (range.isEmpty()) return emptyList()
    val end = (range.last + 1).coerceAtMost(layout.layoutInput.text.length)
    val first = layout.getLineForOffset(range.first)
    val last = layout.getLineForOffset(end - 1)
    return (first..last).mapNotNull { line ->
        val from = maxOf(range.first, layout.getLineStart(line))
        val to = minOf(end, layout.getLineEnd(line, visibleEnd = true))
        if (to <= from) return@mapNotNull null
        val left = layout.getHorizontalPosition(from, usePrimaryDirection = true)
        val right = layout.getHorizontalPosition(to, usePrimaryDirection = true)
        Rect(
            left = minOf(left, right),
            top = layout.getLineTop(line),
            right = maxOf(left, right),
            bottom = layout.getLineBottom(line),
        ) to layout.getLineBaseline(line)
    }
}

/**
 * The rect the caret should aim at.
 *
 * A 詞塊 broken across a line end is two boxes; the union is what the caret
 * points at, because aiming at the first half would put it under a word the
 * reader did not tap.
 */
private fun union(layout: TextLayoutResult, range: IntRange): Rect? =
    lineBoxes(layout, range)
        .map { it.first }
        .reduceOrNull { a, b -> a.expandToInclude(b) }

private fun Rect.expandToInclude(other: Rect) = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)

private val DOT: Dp = 1.5.dp
private val GAP: Dp = 2.5.dp
private val STROKE: Dp = 1.dp
private val UNDERLINE_DROP: Dp = 3.dp
