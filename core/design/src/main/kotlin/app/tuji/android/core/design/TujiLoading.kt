package app.tuji.android.core.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading — what replaces a spinner.
 *
 * A spinner says one thing: wait. A skeleton says two — it is loading, **and
 * this is the shape of what is coming** — which is what keeps the layout from
 * jumping when the real thing lands. Nothing new is invented to draw it: the
 * skeleton is [TujiColor.Paper2], a ground the system already has.
 *
 * Split by whether the wait has a known shape:
 * - content arriving → a skeleton in the shape of the content
 * - work completing → [TujiProgressBar], a 3dp rule
 *
 * **No shimmer sweep.** A sweep is a gradient, and gradients are the door to
 * the skeuomorphic register this design rules out. What these do instead is
 * breathe: one opacity, at D3, the only speed a user is meant to watch.
 */
@Composable
fun TujiSkeleton(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .alpha(breathing(from = 1f, to = 0.55f))
            .background(TujiColor.Paper2)
            .clearAndSetSemantics {},
    )
}

/**
 * Skeleton rows for a list that is still loading.
 *
 * [label] is what a screen reader says instead of the blocks — the copy the
 * screen used to print as a line of text, which is still the honest answer to
 * "what is happening"; it just no longer has to be the *visible* one.
 */
@Composable
fun TujiSkeletonRows(
    modifier: Modifier = Modifier,
    count: Int = 3,
    height: Dp = 56.dp,
    label: String? = null,
) {
    Column(modifier.fillMaxWidth().loadingSemantics(label)) {
        repeat(count) { index ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TujiSpace.S4)
                        .height(TujiBorder.Bw1)
                        .background(TujiColor.Rule),
                )
            }
            TujiSkeleton(
                height = height - TujiSpace.S4,
                modifier = Modifier
                    .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            )
        }
    }
}

/**
 * Stands in for an image that has not arrived, filling whatever container it is
 * given — so a grid keeps its geometry while the pictures load. That stability
 * is the whole reason a skeleton beats a spinner here: without it the tiles
 * reflow the moment the first picture lands, under a thumb already moving.
 */
@Composable
fun TujiImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper2)
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(breathing(from = 0f, to = 0.45f))
                .background(TujiColor.Paper3),
        )
    }
}

/**
 * Page-level loading for a screen whose content shape is not known ahead of
 * time. Still blocks, never a spinner: a heading, a subtitle, a line.
 */
@Composable
fun TujiPageLoading(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .loadingSemantics(label)
            .padding(horizontal = TujiSpace.S4)
            .padding(top = TujiSpace.S5),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        TujiSkeleton(height = 28.dp, width = 180.dp)
        TujiSkeleton(height = 16.dp, width = 260.dp)
        TujiSkeleton(height = 16.dp)
    }
}

/**
 * The shared breath. One function, so a placeholder and a skeleton beside it
 * cannot drift apart in rhythm — which is exactly what happens when two
 * components each hold their own `infiniteRepeatable`.
 *
 * 移除動畫 gets the resting value rather than a faster breath: this is D3
 * motion, the kind meant to be watched, and the rule for that kind is suppress
 * rather than shorten.
 */
@Composable
private fun breathing(from: Float, to: Float): Float {
    if (rememberReduceMotion()) return from
    val transition = rememberInfiniteTransition(label = "breathing")
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(TujiMotion.D3, easing = TujiMotion.EaseOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    return value
}

/**
 * One announcement for the whole block, and nothing from the blocks themselves.
 * A screen reader walking six identical rectangles learns nothing from any of
 * them.
 */
private fun Modifier.loadingSemantics(label: String?): Modifier =
    if (label == null) this else this.clearAndSetSemantics { contentDescription = label }
