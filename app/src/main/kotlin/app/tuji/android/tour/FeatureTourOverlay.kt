package app.tuji.android.tour

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberReduceMotion
import app.tuji.android.core.design.tujiClickable

/**
 * The spotlight.
 *
 * Dims the window, cuts a hole over the step's target, and puts a card with the
 * cat in whichever half the hole is not in. **The dim swallows every touch**,
 * so the highlight is visual only and the tour moves through 下一步 and 跳過
 * alone — a tour that can be half-dismissed by tapping the thing it is pointing
 * at leaves the reader in a state nobody designed.
 */
@Composable
fun FeatureTourOverlay(
    step: TourStep,
    copy: TourCopy,
    anchors: TourAnchors,
    /** True while the shell slides to another tab: keep the dim, drop the hole. */
    transitioning: Boolean,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val shown = remember(step.id) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(step.id, reduceMotion) {
        if (!reduceMotion) shown.animateTo(1f, TujiMotion.ease(TujiMotion.D2))
    }

    val density = LocalDensity.current
    val padPx = with(density) { CUTOUT_PADDING.toPx() }
    val ringPx = with(density) { 2.dp.toPx() }
    val hole = (if (transitioning) null else anchors.resolve(step))?.let {
        Rect(it.left - padPx, it.top - padPx, it.right + padPx, it.bottom + padPx)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Everything, including whatever is under the hole.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        val middle = with(density) { maxHeight.toPx() } / 2f

        Canvas(Modifier.fillMaxSize()) {
            // One layer, so the hole is punched out of the scrim. `Clear`
            // straight onto the canvas would take the app out with it.
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawRect(TujiColor.Scrim)
            hole?.let {
                val radius = holeRadius(step, it)
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(it.left, it.top),
                    size = Size(it.width, it.height),
                    cornerRadius = CornerRadius(radius, radius),
                    blendMode = BlendMode.Clear,
                )
            }
            drawContext.canvas.restore()

            hole?.let {
                val radius = holeRadius(step, it)
                drawRoundRect(
                    color = TujiColor.Current,
                    topLeft = Offset(it.left, it.top),
                    size = Size(it.width, it.height),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = ringPx),
                )
            }
        }

        if (!transitioning) {
            TourCard(
                copy = copy,
                isLast = isLast,
                onSkip = onSkip,
                onNext = onNext,
                modifier = Modifier
                    // Never over the hole it is explaining: the card goes to
                    // whichever half the hole is not in. The closing step has
                    // no hole and sits in the middle.
                    .align(
                        when {
                            hole == null -> Alignment.Center
                            hole.center.y < middle -> Alignment.BottomCenter
                            else -> Alignment.TopCenter
                        },
                    )
                    // The dim covers the system bars on purpose — half a dim
                    // is worse than none — but the *card* is reading matter,
                    // and the top-aligned one had the clock sitting on its
                    // title.
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(TujiSpace.S4)
                    .graphicsLayer {
                        alpha = shown.value
                        val scale = 0.96f + 0.04f * shown.value
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

private fun holeRadius(step: TourStep, rect: Rect): Float = when (step.shape) {
    TourCutoutShape.Pill -> rect.height / 2f
    TourCutoutShape.Square -> 0f
}

@Composable
private fun TourCard(
    copy: TourCopy,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 360.dp).fillMaxWidth().background(TujiColor.Paper)) {
        Row(
            Modifier.padding(TujiSpace.S4),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MascotFigure(pose = copy.pose, size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(stringResource(copy.title), style = TujiType.h3, color = TujiColor.Ink)
                Text(stringResource(copy.text), style = TujiType.bodySm, color = TujiColor.Ink2)
            }
        }
        Row(
            Modifier.padding(start = TujiSpace.S4, end = TujiSpace.S4, bottom = TujiSpace.S4),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 跳過 goes on the last step: by then there is nothing left to skip,
            // and two ways out of one card is one too many.
            if (!isLast) {
                Text(
                    stringResource(R.string.tour_skip),
                    style = TujiType.bodySmStrong,
                    color = TujiColor.Ink3,
                    modifier = Modifier
                        .tujiClickable(onClick = onSkip)
                        .padding(vertical = TujiSpace.S2, horizontal = TujiSpace.S1),
                )
            }
            Spacer(Modifier.weight(1f))
            TujiButton(
                text = stringResource(if (isLast) R.string.tour_start else R.string.tour_next),
                onClick = onNext,
            )
        }
    }
}

/** iOS pads its cutout by 8 so the ring does not sit on what it frames. */
private val CUTOUT_PADDING = 8.dp
