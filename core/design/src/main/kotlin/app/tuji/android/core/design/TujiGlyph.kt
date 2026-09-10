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
import androidx.compose.ui.graphics.StrokeCap
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

    /** A cross — leave, dismiss, close. */
    @Composable
    fun Close(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.11f
            val inset = w * 0.18f
            drawLine(tint, Offset(inset, inset), Offset(w - inset, h - inset), stroke)
            drawLine(tint, Offset(w - inset, inset), Offset(inset, h - inset), stroke)
        }
    }

    /** Three dots — 更多. */
    @Composable
    fun More(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val r = w * 0.075f
            listOf(0.22f, 0.5f, 0.78f).forEach { x ->
                drawCircle(tint, radius = r, center = Offset(w * x, this.size.height / 2f))
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

    /**
     * 設定.
     *
     * Eight teeth on a ring, drawn rather than shipped as an asset — the same
     * 2dp round-cap stroke every other mark here uses, so it does not arrive
     * looking like it came from another app's icon set.
     */
    @Composable
    fun Gear(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val stroke = w * 0.09f
            val centre = Rect(Offset.Zero, this.size).center
            val ring = w * 0.30f
            drawCircle(tint, radius = ring, center = centre, style = Stroke(width = stroke))
            // Teeth as spokes from the ring outward. A ring plus spokes reads
            // as a gear at 20dp where a toothed silhouette turns to mush.
            repeat(TEETH) { i ->
                val angle = (2.0 * Math.PI / TEETH) * i
                val dx = kotlin.math.cos(angle).toFloat()
                val dy = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(centre.x + dx * ring, centre.y + dy * ring),
                    end = Offset(centre.x + dx * w * 0.44f, centre.y + dy * w * 0.44f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    private const val TEETH = 8
}
