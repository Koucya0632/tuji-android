package app.tuji.android.core.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * 向上拉看完整詳情 — the affordance in a collapsed [TujiDetentSheet]'s whitespace.
 *
 * Ported from iOS's `PullUpHint`. A two-height sheet whose second height is
 * reachable only by dragging has no visible edge saying so: the drag indicator
 * is a mark, not a sentence, and a reader who does not try the gesture never
 * learns the word's whole entry is one pull away.
 *
 * The chevron bounces, as iOS's does (`.symbolEffect(.bounce, .repeating)`) —
 * it is the part that says *drag*, and a still arrow over a line of text reads
 * as decoration. Under 移除動畫 it holds still: the sentence is the affordance
 * and the motion is the emphasis, so losing the motion loses nothing that
 * cannot be read.
 *
 * @param text the localised sentence. The string lives with the app's
 *   resources, not here — this module has no `R.string`.
 */
@Composable
fun TujiPullUpHint(text: String, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val lift by if (reduceMotion) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "pullUp").animateFloat(
            initialValue = 0f,
            targetValue = -BOUNCE_DP,
            animationSpec = infiniteRepeatable(
                tween(BOUNCE_MS, easing = TujiMotion.EaseInOut),
                RepeatMode.Reverse,
            ),
            label = "pullUpLift",
        )
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
    ) {
        TujiGlyph.ChevronUp(
            tint = TujiColor.Ink3,
            // Merged away from the reader: the sentence under it already says
            // this, and a screen reader announcing an unlabelled chevron says
            // nothing twice.
            modifier = Modifier
                .graphicsLayer { translationY = lift * density }
                .clearAndSetSemantics {},
        )
        Text(text, style = TujiType.label, color = TujiColor.Ink3)
    }
}

/** Far enough to read as a nudge, not as something coming loose. */
private const val BOUNCE_DP = 3f
private const val BOUNCE_MS = 620
