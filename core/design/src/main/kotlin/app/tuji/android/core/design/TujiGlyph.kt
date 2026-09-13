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
import androidx.compose.ui.graphics.StrokeJoin
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

    /**
     * 書籤.
     *
     * A five-pointed star drawn from its own geometry rather than shipped as an
     * asset, so it takes the same 2dp round-cap stroke as every other mark here
     * and the filled state is the *same shape* filled — not a second icon that
     * has to be kept in step with the first.
     */
    @Composable
    fun Star(
        size: Dp = 20.dp,
        filled: Boolean = false,
        tint: Color = TujiColor.Ink,
        modifier: Modifier = Modifier,
    ) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val centre = Rect(Offset.Zero, this.size).center
            val outer = w * 0.44f
            // The classic ratio. Anything nearer 0.5 reads as a pentagon.
            val inner = outer * 0.382f
            val path = Path()
            repeat(POINTS * 2) { i ->
                val radius = if (i % 2 == 0) outer else inner
                // Start at the top: a star resting on a point is upside down.
                val angle = -Math.PI / 2 + (Math.PI / POINTS) * i
                val x = centre.x + (kotlin.math.cos(angle) * radius).toFloat()
                val y = centre.y + (kotlin.math.sin(angle) * radius).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            if (filled) {
                drawPath(path, tint)
            } else {
                drawPath(path, tint, style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
            }
        }
    }

    /**
     * 今天. A filled disc with eight short rays — the tab bar's one daylight
     * mark, so it is solid like the three beside it.
     */
    @Composable
    fun Sun(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val centre = Rect(Offset.Zero, this.size).center
            drawCircle(tint, radius = w * 0.21f, center = centre)
            repeat(RAYS) { i ->
                val angle = (2.0 * Math.PI / RAYS) * i
                val dx = kotlin.math.cos(angle).toFloat()
                val dy = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(centre.x + dx * w * 0.33f, centre.y + dy * w * 0.33f),
                    end = Offset(centre.x + dx * w * 0.46f, centre.y + dy * w * 0.46f),
                    strokeWidth = w * 0.10f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    /**
     * 圖鑑. Three spines standing on one shelf line, the last leaning — the
     * lean is what makes three rectangles read as books rather than a chart.
     */
    @Composable
    fun Books(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            val base = h * 0.90f
            drawRect(tint, topLeft = Offset(w * 0.06f, h * 0.10f), size = Size(w * 0.20f, base - h * 0.10f))
            drawRect(tint, topLeft = Offset(w * 0.30f, h * 0.22f), size = Size(w * 0.16f, base - h * 0.22f))
            // The leaning one: a parallelogram whose foot stays on the shelf.
            val lean = Path().apply {
                moveTo(w * 0.52f, base)
                lineTo(w * 0.70f, base)
                lineTo(w * 0.96f, h * 0.20f)
                lineTo(w * 0.78f, h * 0.14f)
                close()
            }
            drawPath(lean, tint)
        }
    }

    /** 物見. Two barrels joined at the bridge — things seen, not people. */
    @Composable
    fun Binoculars(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            // The eyepieces, then the bridge, then the two lenses over them.
            drawRect(tint, topLeft = Offset(w * 0.20f, h * 0.18f), size = Size(w * 0.18f, h * 0.30f))
            drawRect(tint, topLeft = Offset(w * 0.62f, h * 0.18f), size = Size(w * 0.18f, h * 0.30f))
            drawRect(tint, topLeft = Offset(w * 0.36f, h * 0.40f), size = Size(w * 0.28f, h * 0.16f))
            drawCircle(tint, radius = w * 0.215f, center = Offset(w * 0.27f, h * 0.64f))
            drawCircle(tint, radius = w * 0.215f, center = Offset(w * 0.73f, h * 0.64f))
        }
    }

    /** 我. A head and shoulders, both filled. */
    @Composable
    fun Person(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            drawCircle(tint, radius = w * 0.21f, center = Offset(w * 0.5f, h * 0.29f))
            val shoulders = Path().apply {
                moveTo(w * 0.10f, h * 0.94f)
                cubicTo(w * 0.10f, h * 0.62f, w * 0.30f, h * 0.55f, w * 0.5f, h * 0.55f)
                cubicTo(w * 0.70f, h * 0.55f, w * 0.90f, h * 0.62f, w * 0.90f, h * 0.94f)
                close()
            }
            drawPath(shoulders, tint)
        }
    }

    /**
     * 連勝. A flame — a teardrop leaning its tip a little off centre, filled,
     * so it reads at 12dp where an outlined one turns into a smudge.
     */
    @Composable
    fun Flame(size: Dp = 12.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            val flame = Path().apply {
                moveTo(w * 0.54f, h * 0.04f)
                cubicTo(w * 0.60f, h * 0.24f, w * 0.86f, h * 0.38f, w * 0.86f, h * 0.62f)
                cubicTo(w * 0.86f, h * 0.83f, w * 0.70f, h * 0.96f, w * 0.50f, h * 0.96f)
                cubicTo(w * 0.30f, h * 0.96f, w * 0.14f, h * 0.83f, w * 0.14f, h * 0.63f)
                cubicTo(w * 0.14f, h * 0.44f, w * 0.30f, h * 0.36f, w * 0.38f, h * 0.22f)
                cubicTo(w * 0.42f, h * 0.30f, w * 0.44f, h * 0.36f, w * 0.48f, h * 0.40f)
                cubicTo(w * 0.52f, h * 0.30f, w * 0.54f, h * 0.18f, w * 0.54f, h * 0.04f)
                close()
            }
            drawPath(flame, tint)
        }
    }

    /** 搜尋. A lens and its handle. */
    @Composable
    fun Search(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val stroke = w * 0.11f
            val centre = Offset(w * 0.42f, w * 0.42f)
            val radius = w * 0.29f
            drawCircle(tint, radius = radius, center = centre, style = Stroke(width = stroke))
            val reach = radius * 0.7071f + stroke * 0.3f
            drawLine(
                color = tint,
                start = Offset(centre.x + reach, centre.y + reach),
                end = Offset(w * 0.90f, w * 0.90f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }

    /** 返回. A shaft with an open head — not a chevron, which is iOS's own. */
    @Composable
    fun ArrowLeft(size: Dp = 20.dp, tint: Color = TujiColor.Ink, modifier: Modifier = Modifier) {
        Canvas(modifier.then(Modifier.size(size))) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.11f
            val y = h * 0.5f
            drawLine(tint, Offset(w * 0.12f, y), Offset(w * 0.88f, y), stroke, cap = StrokeCap.Round)
            val head = Path().apply {
                moveTo(w * 0.46f, h * 0.16f)
                lineTo(w * 0.12f, y)
                lineTo(w * 0.46f, h * 0.84f)
            }
            drawPath(
                head,
                tint,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    private const val TEETH = 8

    private const val POINTS = 5

    private const val RAYS = 8
}
