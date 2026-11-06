package app.tuji.android.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.form.TujiSheetHeader
import kotlin.math.exp

/**
 * 記憶曲線 — why 今日 asks for 複習 before 學新字. iOS's `ReviewCurveSheet`.
 *
 * Opened from the ⓘ at the ink block's bottom-right, next to the button that
 * says 「複習 26」: the question comes up there, so the answer sits there. The
 * thresholds quoted are `StudyQuotas.computeNewLimit`'s — if those move, this
 * copy moves with them.
 */
@Composable
fun ReviewCurveSheet(onDismiss: () -> Unit) {
    TujiWindow(onDismiss = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TujiColor.Scrim)
                .tujiClickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .background(TujiColor.Paper)
                    // Swallows taps so one on the sheet does not reach the scrim.
                    .tujiClickable {}
                    .navigationBarsPadding(),
            ) {
                TujiSheetHeader(title = stringResource(R.string.today_review_curve), closeEnabled = true, onClose = onDismiss)
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3, bottom = TujiSpace.S5),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
                ) {
                    ForgettingCurveChart()
                    Point(R.string.review_curve_fade_title, R.string.review_curve_fade_body)
                    Point(R.string.review_curve_spaced_title, R.string.review_curve_spaced_body)
                    Point(R.string.review_curve_first_title, R.string.review_curve_first_body)
                }
            }
        }
    }
}

@Composable
private fun Point(title: Int, detail: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Text(stringResource(title), style = TujiType.bodyStrong, color = TujiColor.Ink)
        Text(stringResource(detail), style = TujiType.bodySm, color = TujiColor.Ink.copy(alpha = 0.7f))
    }
}

/** When each review happens, as a fraction of the x-axis. */
private val REVIEWS = listOf(0.0, 0.14, 0.34, 0.64)

/** How slowly each segment decays — longer after every review. */
private val STABILITY = listOf(0.07, 0.17, 0.4, 1.2)

/**
 * Two curves on one axis: retention with no review, and the sawtooth spaced
 * review draws. Illustrative, not data: the shape is the point.
 */
@Composable
private fun ForgettingCurveChart() {
    val faded = TujiColor.Ink.copy(alpha = 0.35f)
    Column(
        Modifier.border(TujiBorder.Bw1, TujiColor.Rule).padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics {}) {
            // Inset so the review dots on the top edge are not clipped.
            val inset = 5.dp.toPx()
            val w = size.width - inset * 2
            val h = size.height - inset * 2
            fun point(t: Double, retention: Double) =
                Offset(inset + (t * w).toFloat(), inset + ((1 - retention) * h).toFloat())

            drawLine(TujiColor.Rule, Offset(inset, inset + h), Offset(inset + w, inset + h), 1.dp.toPx())

            val forgetting = Path().apply {
                moveTo(point(0.0, 1.0).x, point(0.0, 1.0).y)
                for (i in 0..100) {
                    val t = i / 100.0
                    val p = point(t, exp(-t / STABILITY[0]))
                    lineTo(p.x, p.y)
                }
            }
            drawPath(
                forgetting,
                faded,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )

            val spaced = Path()
            REVIEWS.forEachIndexed { index, start ->
                val end = REVIEWS.getOrElse(index + 1) { 1.0 }
                val top = point(start, 1.0)
                if (index == 0) spaced.moveTo(top.x, top.y) else spaced.lineTo(top.x, top.y)
                var t = start
                while (t <= end + 1e-9) {
                    val p = point(t, exp(-(t - start) / STABILITY[index]))
                    spaced.lineTo(p.x, p.y)
                    t += 0.01
                }
            }
            drawPath(spaced, TujiColor.Accumulation, style = Stroke(width = 2.5.dp.toPx()))

            for (start in REVIEWS.drop(1)) {
                val c = point(start, 1.0)
                drawCircle(TujiColor.Current, radius = 4.dp.toPx(), center = c)
                drawCircle(TujiColor.Ink, radius = 4.dp.toPx(), center = c, style = Stroke(width = 1.dp.toPx()))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S4), verticalAlignment = Alignment.CenterVertically) {
            Legend(stringResource(R.string.review_curve_on_time), TujiColor.Accumulation, dashed = false)
            Legend(stringResource(R.string.review_curve_no_review), faded, dashed = true)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.review_curve_time), style = TujiType.label, color = TujiColor.Ink.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Legend(label: String, color: Color, dashed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(label, style = TujiType.label, color = TujiColor.Ink.copy(alpha = 0.7f))
    }
}
