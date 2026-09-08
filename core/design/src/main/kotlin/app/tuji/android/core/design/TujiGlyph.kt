package app.tuji.android.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two glyphs 聽句 needs, drawn rather than imported.
 *
 * `material-icons-extended` is ~30 MB of vector functions to pull in for two
 * shapes, and its icons are Material's line weight rather than this app's. Both
 * of these are a handful of path commands, they scale with Dynamic Type because
 * the caller passes a size, and they take the ink colour like any other mark on
 * the paper.
 */
object TujiGlyph {

    /** A speaker with two waves — 再聽一次. */
    @Composable
    fun Speaker(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.09f

            // The body and cone as one filled path.
            val body = Path().apply {
                moveTo(w * 0.06f, h * 0.36f)
                lineTo(w * 0.24f, h * 0.36f)
                lineTo(w * 0.46f, h * 0.16f)
                lineTo(w * 0.46f, h * 0.84f)
                lineTo(w * 0.24f, h * 0.64f)
                lineTo(w * 0.06f, h * 0.64f)
                close()
            }
            drawPath(body, tint)

            // Two arcs, the outer one wider: a single arc reads as a bracket.
            listOf(0.62f to 0.30f, 0.80f to 0.46f).forEach { (right, spread) ->
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * (right - spread), h * (0.5f - spread)),
                    size = Size(w * spread * 2f, h * spread * 2f),
                    style = Stroke(width = stroke),
                )
            }
        }
    }

    /** An eye — 顯示例句. */
    @Composable
    fun Eye(size: Dp = 20.dp, tint: Color = TujiColor.Ink2, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.09f

            // Two mirrored quadratic curves make the lens. Drawn as an outline
            // so the pupil inside it stays legible at 20dp.
            val lens = Path().apply {
                moveTo(w * 0.06f, h * 0.5f)
                quadraticTo(w * 0.5f, h * 0.10f, w * 0.94f, h * 0.5f)
                quadraticTo(w * 0.5f, h * 0.90f, w * 0.06f, h * 0.5f)
                close()
            }
            drawPath(lens, tint, style = Stroke(width = stroke))
            drawCircle(tint, radius = w * 0.15f, center = Rect(Offset.Zero, this.size).center)
        }
    }
}
