package app.tuji.android.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.tuji.android.core.model.FuriganaSegment

/**
 * A Japanese headword with its kana set over the characters they read.
 *
 * Replaces the line that used to sit *under* the word, where the reader had to
 * work out which kana went with which kanji: はみがきこ under 歯磨き粉 says nothing
 * about 磨 being みが. The split itself is a server-side dictionary fact
 * (ADR-0006); this file is only about drawing it.
 *
 * The word is fitted to its column by **choosing a size**, not by scaling a
 * laid-out row. iOS reaches for `ViewThatFits`, which has no Compose
 * equivalent, so the same decision is made explicitly: measure each rung of
 * [FuriganaScaleLadder] with a [TextMeasurer] and take the first that fits.
 * That is arguably the more honest spelling — it is the same arithmetic
 * `ViewThatFits` runs, and the iOS version's predecessor got this exact thing
 * wrong by computing a scale, placing segments at scaled offsets, and then
 * letting each label draw at full size on top of its neighbour.
 */
@Composable
fun FuriganaHeadword(
    segments: List<FuriganaSegment>,
    modifier: Modifier = Modifier,
    baseSize: TextUnit = TujiHeadwordSize.Base,
    rubyRatio: Float = TujiHeadwordSize.RUBY_RATIO,
    /** Ruby sits closer to its base than normal line spacing would put it. */
    rubySpacing: Dp = 1.dp,
) {
    val type = TujiType
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val steps = remember(baseSize, rubyRatio) {
            FuriganaScaleLadder.steps(baseSize.value, rubyRatio)
        }
        val scale = remember(segments, available, steps) {
            steps.firstOrNull { step ->
                rowWidth(measurer, segments, type, baseSize, rubyRatio, step) <= available
            } ?: steps.last()
        }

        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.clearAndSetSemantics {
                // Segment by segment a screen reader would announce 歯・磨・き・粉
                // as four items and then say the kana twice over. One label,
                // the word as written.
                contentDescription = segments.joinToString("") { it.text }
            },
        ) {
            for (segment in segments) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Every segment reserves the ruby line, including the ones
                    // with no ruby: without it the bare kana of 歯磨"き"粉 would
                    // sit a ruby's height higher than its neighbours.
                    Text(
                        text = segment.ruby ?: " ",
                        style = type.headwordRuby(baseSize * rubyRatio * scale),
                        color = TujiColor.Ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(Modifier.height(rubySpacing * scale))
                    Text(
                        text = segment.text,
                        style = type.headword(baseSize * scale),
                        color = TujiColor.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** Natural width of the whole row at one rung, in pixels. */
private fun rowWidth(
    measurer: TextMeasurer,
    segments: List<FuriganaSegment>,
    type: TujiTypography,
    baseSize: TextUnit,
    rubyRatio: Float,
    scale: Float,
): Int = segments.sumOf { segment ->
    val base = measurer.measure(segment.text, type.headword(baseSize * scale)).size.width
    val ruby = segment.ruby
        ?.let { measurer.measure(it, type.headwordRuby(baseSize * rubyRatio * scale)).size.width }
        ?: 0
    maxOf(base, ruby)
}

/**
 * The sizes a furigana headword is allowed to take, largest first.
 *
 * The bottom of the ladder is not a taste decision: below roughly 13sp CJK
 * strokes merge, which is why 12 was removed from the type scale outright. Ruby
 * is half the headword, so *the headword's* floor is whatever keeps the ruby
 * legible — 0.47 under a 56sp word, but 0.77 under a 34sp one. Deriving it here
 * makes that constraint something the code enforces instead of something a call
 * site is trusted to remember.
 *
 * At the 26sp [TujiHeadwordSize.Base] sets, the floor *is* full size and the
 * ladder collapses to a single rung. That is the correct answer, not a
 * degenerate one: there is no room left to shrink into.
 */
object FuriganaScaleLadder {
    /** Smallest kana the CJK face still resolves. */
    const val MINIMUM_RUBY_POINT = 13f

    private const val RUNGS = 5

    fun steps(
        baseSize: Float,
        rubyRatio: Float = TujiHeadwordSize.RUBY_RATIO,
        minRubyPoint: Float = MINIMUM_RUBY_POINT,
    ): List<Float> {
        val rubyAtFullSize = baseSize * rubyRatio
        if (rubyAtFullSize <= 0f) return listOf(1f)

        // A base so small that even full size breaks the floor still has to
        // draw something; one rung at full size is the honest answer.
        val floor = minOf(1f, minRubyPoint / rubyAtFullSize)
        if (floor >= 1f) return listOf(1f)

        // Evenly spaced rather than tuned: the gap only decides how much
        // smaller than necessary a word ends up, and at this many rungs that is
        // under 5%.
        return (0 until RUNGS).map { index ->
            1f - (1f - floor) * index / (RUNGS - 1)
        }
    }
}
