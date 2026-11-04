package app.tuji.android.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.tuji.android.core.design.TujiColor

/**
 * 情境故事 — the place behind the stage, iOS's `StoryBackdrop`. One flat drawing
 * per scene id from the offer (`supermarket`, `office`, `restaurant`,
 * `station`, `home`), made of the app's own colour tokens rather than painted
 * art. An id the app does not know yet (the server owns the list) gets the
 * plain room. Placeholder art: swap a case for an image when the illustrations
 * exist.
 *
 * Everything is laid out against [FLOOR], the fraction of the height where the
 * wall meets the ground; the stage stands Moira on it. Fixed sizes (line
 * widths, the lamp shades) are in dp, as iOS's are in points.
 */
@Composable
internal fun StoryBackdrop(sceneId: String?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val painter = Painter(this)
        when (sceneId) {
            "supermarket" -> painter.supermarket()
            "office" -> painter.office()
            "restaurant" -> painter.restaurant()
            "station" -> painter.station()
            else -> painter.home()
        }
    }
}

/** Where the wall meets the floor, as a fraction of the height. */
internal const val FLOOR = 0.62f

private class Painter(private val scope: DrawScope) {
    private val wall = Rect(0f, 0f, scope.size.width, scope.size.height * FLOOR)
    private val ground = Rect(0f, wall.bottom, scope.size.width, scope.size.height)
    private val w get() = wall.width
    private val h get() = wall.height

    /** dp to px. */
    private fun p(value: Float) = value * scope.density

    /** A rectangle in wall fractions. */
    private fun box(x: Float, y: Float, width: Float, height: Float) = Rect(Offset(w * x, h * y), Size(w * width, h * height))

    private fun rect(x: Float, y: Float, width: Float, height: Float) = Rect(Offset(x, y), Size(width, height))

    private fun fill(rect: Rect, color: Color) = scope.drawRect(color, rect.topLeft, rect.size)

    private fun oval(rect: Rect, color: Color) = scope.drawOval(color, rect.topLeft, rect.size)

    private fun frame(rect: Rect, width: Float) = scope.drawRect(TujiColor.Ink, rect.topLeft, rect.size, style = Stroke(p(width)))

    fun supermarket() {
        fill(wall, TujiColor.Paper2)
        fill(ground, TujiColor.Paper3)
        tiles(every = 48f)
        // Two shelving units, three shelves each, stocked with boxes.
        val goods = listOf(TujiColor.BrandPrimary, TujiColor.Accumulation, TujiColor.Alert.copy(alpha = 0.7f), TujiColor.AccumulationSoft)
        listOf(0.04f, 0.56f).forEachIndexed { index, x ->
            val unit = box(x, 0.18f, 0.40f, 0.82f)
            fill(unit, TujiColor.BrandSecondary.copy(alpha = 0.85f))
            for (row in 0 until 3) {
                val shelfY = unit.top + unit.height * (0.30f + row * 0.32f)
                fill(rect(unit.left, shelfY, unit.width, p(4f)), TujiColor.Ink)
                val boxW = unit.width / 6
                for (slot in 0 until 5) {
                    val boxH = unit.height * (0.12f + 0.04f * ((slot + row + index) % 3))
                    val boxX = unit.left + boxW * 0.4f + slot * boxW * 1.08f
                    fill(rect(boxX, shelfY - boxH, boxW * 0.82f, boxH), goods[(slot + row * 2 + index) % goods.size])
                }
            }
        }
    }

    fun office() {
        fill(wall, TujiColor.AccumulationSoft)
        fill(ground, TujiColor.Paper3)
        // A row of windows with towers behind.
        for (col in 0 until 3) {
            val pane = box(0.06f + col * 0.30f, 0.10f, 0.26f, 0.50f)
            fill(pane, TujiColor.Paper)
            for (tower in 0 until 2) {
                val towerH = pane.height * (0.45f + 0.2f * ((col + tower) % 2))
                val towerX = pane.left + pane.width * (0.12f + 0.46f * tower)
                fill(rect(towerX, pane.bottom - towerH, pane.width * 0.28f, towerH), TujiColor.Accumulation.copy(alpha = 0.35f))
            }
            frame(pane, 3f)
            fill(rect(pane.center.x - p(1.5f), pane.top, p(3f), pane.height), TujiColor.Ink)
        }
        // Desk with a monitor.
        val desk = box(0.52f, 0.92f, 0.44f, 0.08f)
        fill(desk, TujiColor.BrandSecondary)
        val screen = rect(desk.left + desk.width * 0.30f, desk.top - h * 0.20f, desk.width * 0.42f, h * 0.17f)
        fill(screen, TujiColor.Ink)
        fill(screen.deflate(p(4f)), TujiColor.Accumulation)
        fill(rect(screen.center.x - p(3f), screen.bottom, p(6f), desk.top - screen.bottom), TujiColor.Ink)
        plant(Offset(w * 0.10f, wall.bottom), h * 0.26f)
    }

    fun restaurant() {
        fill(wall, TujiColor.BrandPrimary.copy(alpha = 0.28f))
        fill(box(0f, 0.62f, 1f, 0.38f), TujiColor.BrandSecondary.copy(alpha = 0.75f))
        fill(ground, TujiColor.BrandSecondary.copy(alpha = 0.45f))
        tiles(every = 64f)
        // Pendant lamps.
        for (x in listOf(0.2f, 0.5f, 0.8f)) {
            val cx = w * x
            fill(rect(cx - p(1f), 0f, p(2f), h * 0.22f), TujiColor.Ink)
            val shade = Path().apply {
                moveTo(cx - p(22f), h * 0.30f)
                lineTo(cx - p(8f), h * 0.22f)
                lineTo(cx + p(8f), h * 0.22f)
                lineTo(cx + p(22f), h * 0.30f)
                close()
            }
            scope.drawPath(shade, TujiColor.Ink)
            oval(rect(cx - p(30f), h * 0.30f, p(60f), p(18f)), TujiColor.BrandPrimary.copy(alpha = 0.5f))
        }
        // Menu board.
        val board = box(0.62f, 0.36f, 0.30f, 0.20f)
        fill(board, TujiColor.Ink)
        for (line in 0 until 3) {
            val y = board.top + p(12f) + line * board.height * 0.26f
            fill(rect(board.left + p(10f), y, board.width * (0.7f - 0.12f * line), p(3f)), TujiColor.Paper.copy(alpha = 0.8f))
        }
        // A table in front.
        val top = rect(ground.width * 0.58f, ground.top + ground.height * 0.10f, ground.width * 0.38f, p(8f))
        fill(top, TujiColor.Ink)
        fill(rect(top.center.x - p(3f), top.bottom, p(6f), ground.height * 0.30f), TujiColor.Ink)
    }

    fun station() {
        fill(wall, TujiColor.AccumulationSoft)
        // Far platform and the track bed.
        fill(box(0f, 0.66f, 1f, 0.34f), TujiColor.Ink3.copy(alpha = 0.6f))
        for (y in listOf(0.76f, 0.90f)) fill(rect(0f, h * y, w, p(3f)), TujiColor.Ink2)
        // Canopy roof and pillars.
        fill(box(0f, 0f, 1f, 0.10f), TujiColor.BrandSecondary)
        for (x in listOf(0.08f, 0.90f)) fill(rect(w * x, h * 0.10f, p(8f), h * 0.56f), TujiColor.BrandSecondary)
        // Station sign.
        val sign = box(0.28f, 0.20f, 0.44f, 0.14f)
        fill(sign, TujiColor.Paper)
        frame(sign, 2f)
        fill(rect(sign.left, sign.bottom - p(6f), sign.width, p(6f)), TujiColor.Accumulation)
        // Near platform: concrete with the yellow tactile line.
        fill(ground, TujiColor.Paper3)
        fill(rect(0f, ground.top + p(6f), ground.width, p(10f)), TujiColor.BrandPrimary)
        fill(rect(0f, ground.top, ground.width, p(3f)), TujiColor.Ink)
    }

    fun home() {
        fill(wall, TujiColor.Paper)
        fill(ground, TujiColor.BrandSecondary.copy(alpha = 0.35f))
        // Planks.
        val plank = ground.width / 6
        var x = 0f
        while (x < ground.width) {
            fill(rect(x, ground.top, p(1f), ground.height), TujiColor.BrandSecondary.copy(alpha = 0.4f))
            x += plank
        }
        fill(rect(0f, wall.bottom - p(8f), w, p(8f)), TujiColor.BrandSecondary.copy(alpha = 0.6f))
        // Window with curtains.
        val window = box(0.56f, 0.12f, 0.34f, 0.42f)
        fill(window, TujiColor.AccumulationSoft)
        frame(window, 3f)
        fill(rect(window.center.x - p(1.5f), window.top, p(3f), window.height), TujiColor.Ink)
        for (cx in listOf(window.left - p(10f), window.right - p(12f))) {
            fill(rect(cx, window.top - p(6f), p(22f), window.height + p(16f)), TujiColor.BrandPrimary.copy(alpha = 0.7f))
        }
        // Sofa.
        val seat = box(0.04f, 0.84f, 0.44f, 0.16f)
        fill(rect(seat.left, seat.top - h * 0.12f, seat.width, h * 0.14f), TujiColor.Accumulation)
        fill(seat, TujiColor.AccumulationDeep)
        plant(Offset(w * 0.94f, wall.bottom), h * 0.30f)
    }

    // Props

    private fun tiles(every: Float) {
        val step = p(every)
        var x = ground.left
        while (x <= ground.right) {
            fill(rect(x, ground.top, p(1f), ground.height), TujiColor.Rule)
            x += step
        }
        var y = ground.top
        while (y <= ground.bottom) {
            fill(rect(ground.left, y, ground.width, p(1f)), TujiColor.Rule)
            y += step / 2
        }
    }

    private fun plant(base: Offset, height: Float) {
        val pot = rect(base.x - height * 0.18f, base.y - height * 0.32f, height * 0.36f, height * 0.32f)
        for ((dx, leaf) in listOf(-0.16f to 0.95f, 0f to 1.1f, 0.16f to 0.9f)) {
            oval(
                rect(base.x + height * dx - height * 0.10f, pot.top - height * leaf * 0.70f, height * 0.20f, height * leaf * 0.75f),
                TujiColor.AccumulationDeep,
            )
        }
        fill(pot, TujiColor.BrandSecondary)
    }
}
