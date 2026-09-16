package app.tuji.android.core.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.roundToInt

/**
 * How far along something is, as a rule of ink.
 *
 * A rectangle at the selection weight, not a rounded Material bar: 紙與墨 has
 * one way to draw "this far", and it is the same 3dp the study header uses.
 *
 * The colours are arguments because the same bar sits on two grounds. On paper
 * the track is [TujiColor.Paper3]; on the ink hero it is paper at 20%, and the
 * fill switches to the **pale** accumulation step — the deep teal only reaches
 * 3.04:1 against ink while the pale one reaches 13.58:1 and carries the same
 * meaning.
 *
 * **The fill moves.** A progress value that teleports is not progress; it is a
 * redraw, and this is D3 — the one speed a user is meant to follow. Under
 * 移除動畫 it jumps instead, because that rule says suppress rather than
 * shorten.
 */
@Composable
fun TujiProgressBar(
    progress: Double,
    modifier: Modifier = Modifier,
    track: Color = TujiColor.Paper3,
    fill: Color = TujiColor.Current,
) {
    val reduceMotion = rememberReduceMotion()
    val target = progress.coerceIn(0.0, 1.0).toFloat()
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else TujiMotion.D3,
            easing = TujiMotion.EaseOut,
        ),
        label = "progress",
    )
    Box(modifier.fillMaxWidth().height(TujiBorder.Bw3).background(track)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(fill))
    }
}

/**
 * The same rule, for work whose end is not known: AI recognition, an upload.
 *
 * A short fill sweeping back and forth rather than a percentage, because the
 * honest answer is "still going" and a bar that fills to 90% and waits there is
 * a promise nobody made. Under 移除動畫 it holds still at the left — the work is
 * announced in words by whatever raised it.
 */
@Composable
fun TujiIndeterminateBar(
    modifier: Modifier = Modifier,
    track: Color = TujiColor.Paper3,
    fill: Color = TujiColor.Current,
    label: String? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val offset = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "sweep")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = IndeterminateSweep.TRAVEL,
            animationSpec = infiniteRepeatable(
                animation = tween(TujiMotion.D3 * 2, easing = TujiMotion.EaseOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sweepOffset",
        )
        value
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(TujiBorder.Bw3)
            .background(track)
            .then(
                if (label == null) Modifier
                else Modifier.clearAndSetSemantics { contentDescription = label },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth(IndeterminateSweep.FILL)
                .fillMaxHeight()
                // Offset as a fraction of the *track*, which the fill does not
                // know: a percentage of its own width would sweep a third as
                // far as it should.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative((constraints.maxWidth * offset).roundToInt(), 0)
                    }
                }
                .background(fill),
        )
    }
}

/**
 * The two numbers the sweep is made of, together because they are one decision:
 * **they must add to 1**, or the fill walks off the end of its own track. That
 * is the whole reason they are named rather than written into the two call
 * sites five lines apart, where changing the width and forgetting the travel is
 * the obvious mistake and the only symptom is a bar that briefly disappears.
 */
internal object IndeterminateSweep {
    /** How much of the track the fill covers. */
    const val FILL = 0.35f

    /** How far it travels, as a fraction of the track. */
    const val TRAVEL = 0.65f
}
