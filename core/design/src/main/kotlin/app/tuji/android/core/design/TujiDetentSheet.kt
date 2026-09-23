package app.tuji.android.core.design

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A bottom sheet with two heights: what it needs, and everything it can get.
 *
 * iOS spells this as `presentationDetents([rest, .large])`, where the rest
 * detent is *measured* from the content rather than set to half the screen —
 * which is also why this is hand-rolled rather than Material's
 * `ModalBottomSheet`. That component offers a partial state fixed at half the
 * window, plus a rounded top and a grabber capsule; a summary and two buttons
 * do not need half a phone, and the other two are Material's signature in a
 * system whose every surface is a square on paper.
 *
 * **It follows the finger.** The expansion is a fraction, not a state that
 * flips on release: a sheet that does nothing until you let go reads as a
 * gesture that was not received. The fraction settles to whichever end is
 * nearer when the drag stops, and a fling decides it outright.
 *
 * The 3dp ink edge along the top is the same mark the tab bar and every
 * selected state already use.
 *
 * **The summary rides up with the sheet; only the actions are pinned.** That
 * is iOS's shape in both places this exists — `WordPeekSheet.expandableBody`
 * and `ReviewRevealSheet` are each a `ScrollView { summary; detail }` with the
 * buttons in a `.safeAreaInset(edge: .bottom)`. The first cut here had it
 * upside down: the entry opened *above* a summary nailed to the bottom, so
 * pulling up pushed the word away from the reader instead of carrying it along.
 *
 * @param summary the word, or whatever the sheet is about. Scrolls.
 * @param actions the buttons. Pinned to the bottom edge at both heights,
 *   because they are what the sheet is asking for and the answer must not be a
 *   scroll away.
 * @param expandedContent what appears in the space the drag opens up. Composed
 *   only once the sheet has begun to open, so the work behind it — a word's
 *   full entry, say — is not paid for by a reader who never drags.
 * @param collapsedHint what stands in its place until then, in the whitespace
 *   the resting height leaves. A two-height sheet with no sentence saying so
 *   has only the drag indicator to advertise its second height.
 * @param onCollapsedDragDown a downward drag with nothing left to collapse.
 *   null means the sheet cannot be dismissed that way.
 */
@Composable
fun TujiDetentSheet(
    modifier: Modifier = Modifier,
    onCollapsedDragDown: (() -> Unit)? = null,
    expandedContent: (@Composable () -> Unit)? = null,
    collapsedHint: (@Composable () -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
    summary: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reading = rememberScrollState()
    var fraction by remember { mutableFloatStateOf(0f) }
    var summaryPx by remember { mutableIntStateOf(0) }
    var hintPx by remember { mutableIntStateOf(0) }
    var actionsPx by remember { mutableIntStateOf(0) }
    var containerPx by remember { mutableIntStateOf(0) }
    // Measured rather than declared — iOS's `ReviewRevealLayout.restHeight`,
    // which replaced a fixed fraction the rating row had quietly outgrown.
    val restPx = if (summaryPx > 0 && actionsPx > 0) {
        indicatorPx(density) + summaryPx + hintPx + actionsPx
    } else {
        0
    }
    val extraPx = (containerPx - restPx).coerceAtLeast(0)

    fun drag(delta: Float): Float {
        if (extraPx <= 0) return 0f
        val before = fraction
        fraction = (fraction - delta / extraPx).coerceIn(0f, 1f)
        return (before - fraction) * extraPx
    }

    fun settle(velocity: Float) {
        val target = when {
            velocity < -FLING -> 1f
            velocity > FLING -> 0f
            else -> if (fraction > 0.4f) 1f else 0f
        }
        scope.launch {
            animate(fraction, target, animationSpec = tween(TujiMotion.D2, easing = TujiMotion.EaseOut)) { v, _ ->
                fraction = v
            }
        }
    }

    /**
     * The half of the gesture a scrolling child would otherwise swallow.
     *
     * Dragging **up** opens the sheet before the content inside it scrolls, and
     * dragging **down** closes it only once that content has nothing left to
     * give back — which is the rule every sheet with a list inside it follows,
     * and the reason a hand-rolled one cannot stop at [draggable]: without this
     * the pulled-up half is a trap, openable by the handle and closable by
     * nothing the thumb is already touching.
     */
    val nested = remember(extraPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0 && fraction < 1f) Offset(0f, -drag(available.y)) else Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (available.y > 0) Offset(0f, -drag(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (fraction > 0f && fraction < 1f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            // The expanded sheet stops below the status bar, as iOS's `.large`
            // detent does. Without it the drag indicator ends up level with the
            // clock.
            .statusBarsPadding()
            .onSizeChanged { containerPx = it.height },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val heightPx = restPx + (extraPx * fraction)
        Column(
            Modifier
                .fillMaxWidth()
                // Explicit only once the rest height is known and the sheet has
                // begun to open: at rest it wraps its content, which is what
                // makes the rest detent *measured* rather than declared.
                .then(
                    if (restPx > 0 && fraction > 0f) {
                        Modifier.height(with(density) { heightPx.toDp() })
                    } else {
                        Modifier
                    },
                )
                .background(TujiColor.Paper)
                // The top edge is a selection indicator, which is the one thing
                // this weight means in this system.
                .padding(top = TujiBorder.Bw3)
                .nestedScroll(nested)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { drag(it) },
                    onDragStopped = { velocity ->
                        if (fraction == 0f && velocity > 0f) onCollapsedDragDown?.invoke() else settle(velocity)
                    },
                ),
        ) {
            DragIndicator()
            // The reading half. `weight` only once open: at rest it wraps, and
            // wrapping is what makes the rest height *measured*. A computed
            // height instead would put a rest measurement a few pixels out into
            // the rating row rather than into the reading.
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (fraction > 0f) Modifier.weight(1f) else Modifier)
                    .clipToBounds()
                    .verticalScroll(reading),
            ) {
                Column(Modifier.onSizeChanged { summaryPx = it.height }) { summary() }
                if (expandedContent != null) {
                    if (fraction > 0f) {
                        expandedContent()
                    } else {
                        // Measured while it is the thing standing there, so the
                        // rest height includes it — an affordance below the fold
                        // is not an affordance.
                        Box(Modifier.onSizeChanged { hintPx = it.height }) { collapsedHint?.invoke() }
                    }
                }
            }
            if (expandedContent != null && fraction > 0f) {
                // The line between reading and acting. Without it the last
                // visible line of the entry sits a pixel off the actions'
                // first, and a half-cut sentence touching a label reads as two
                // things drawn on top of each other.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TujiBorder.Bw1)
                        .background(TujiColor.Rule),
                )
            }
            Column(Modifier.onSizeChanged { actionsPx = it.height }) { actions() }
        }
    }
}

/**
 * The one thing kept from the system sheet: a mark that says this can be
 * pulled. A rule rather than a capsule — a capsule is the platform's own
 * grabber, and the whole point of the sheet is that it is not one.
 */
@Composable
private fun DragIndicator() {
    Box(Modifier.fillMaxWidth().height(INDICATOR_BAND), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = TujiBorder.Bw3)
                .background(TujiColor.Paper3)
                .clearAndSetSemantics {},
        )
    }
}

private fun indicatorPx(density: androidx.compose.ui.unit.Density): Int =
    with(density) { INDICATOR_BAND.roundToPx() }

private val INDICATOR_BAND = 20.dp

/** Above this, the flick decides rather than where the finger stopped. */
private const val FLING = 400f
