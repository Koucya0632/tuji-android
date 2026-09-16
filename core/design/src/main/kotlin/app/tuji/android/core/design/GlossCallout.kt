package app.tuji.android.core.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 詞塊卡片的幾何 — where the card goes, and what outline it wears when it gets
 * there.
 *
 * Pure arithmetic and one [Path], kept out of the card itself because this is
 * the half that can be wrong in ways a screenshot of one device at one text
 * size will not show: a card running off the top of a short screen, a caret
 * leaving the card when the word sits at the start of a line, the fallback
 * quietly becoming unreachable.
 *
 * Everything here is in **pixels**, because every number it is handed is:
 * a text layout's bounding box, a measured card, a container size. The four
 * design distances are the only dp in the file, and [metrics] is where they
 * stop being dp.
 */
object GlossCalloutPlacement {
    /** 16 × 8, off the spacing scale, so the caret is the same shape everywhere. */
    val caretWidth = TujiSpace.S3
    val caretHeight = TujiSpace.S2

    /**
     * The page margin — the card shares the one horizontal boundary the rest of
     * the app aligns to, rather than inventing a second.
     */
    val sideMargin = TujiSpace.S4

    /** Between the caret's tip and the 詞塊 it points at. */
    val anchorGap = TujiSpace.S2

    /** Between the card and the top or bottom of the host. */
    val edgeMargin = TujiSpace.S3

    /** The same four distances, in the units everything else arrives in. */
    data class Metrics(
        val caretWidth: Float,
        val caretHeight: Float,
        val sideMargin: Float,
        val anchorGap: Float,
        val edgeMargin: Float,
    )

    fun metrics(density: Density): Metrics = with(density) {
        Metrics(
            caretWidth = caretWidth.toPx(),
            caretHeight = caretHeight.toPx(),
            sideMargin = sideMargin.toPx(),
            anchorGap = anchorGap.toPx(),
            edgeMargin = edgeMargin.toPx(),
        )
    }

    /**
     * The card is always this wide, placed or not. A width that depended on the
     * placement would feed back into the height the placement is decided from,
     * and the two would chase each other.
     */
    fun cardWidth(containerWidth: Float, m: Metrics): Float =
        max(0f, containerWidth - 2 * m.sideMargin)

    data class Result(
        /** The card's top edge, in the host's coordinate space. */
        val top: Float,
        /** The caret's centre, in the card's own coordinate space. */
        val caretX: Float,
        /** Caret on the bottom edge — the card sits above the word. */
        val pointsDown: Boolean,
    )

    /**
     * null ⇒ the card fits neither above nor below the word; the caller falls
     * back to the bottom of the screen and draws no caret.
     *
     * [cardSize] already includes the caret's band, which is reserved on one
     * side whichever way the caret ends up pointing — so the height does not
     * depend on the answer this function is computing.
     */
    fun place(anchor: Rect, cardSize: Size, container: Size, m: Metrics): Result? {
        if (cardSize.height <= 0f || container.height <= 0f) return null
        val above = anchor.top - m.anchorGap - cardSize.height
        val below = anchor.bottom + m.anchorGap
        val pointsDown = when {
            // Above is the preference: the card lands between the reader's eye
            // and the word rather than under their own thumb.
            above >= m.edgeMargin -> true
            below + cardSize.height <= container.height - m.edgeMargin -> false
            else -> return null
        }
        return Result(
            top = if (pointsDown) above else below,
            caretX = caretX(anchor, cardSize.width, m),
            pointsDown = pointsDown,
        )
    }

    /**
     * The caret follows the word until it would leave the card — a word at the
     * start or end of a line is the common case, not an edge one.
     */
    fun caretX(anchor: Rect, cardWidth: Float, m: Metrics): Float {
        val half = m.caretWidth / 2
        val lower = half + m.anchorGap
        val upper = cardWidth - half - m.anchorGap
        if (lower > upper) return cardWidth / 2
        return min(max(anchor.center.x - m.sideMargin, lower), upper)
    }
}

/**
 * The card's outline: a square-cornered block with one caret, as a single path.
 *
 * One path rather than a card plus a stuck-on triangle, because the 1dp
 * [TujiColor.Rule] edge has to run round the caret without a seam — and the
 * seam is the only thing a hairline border can get wrong.
 */
data class GlossCalloutShape(
    /**
     * null ⇒ no caret. The card could not be placed beside the 詞塊 and is
     * sitting at the bottom instead; a caret aimed at nothing is worse than no
     * caret.
     */
    val caret: Caret?,
) : Shape {
    data class Caret(val x: Float, val pointsDown: Boolean)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val m = GlossCalloutPlacement.metrics(density)
        val band = m.caretHeight
        val half = m.caretWidth / 2
        // The band is reserved on the bottom when there is no caret, so the
        // card's height is the same either way.
        val pointsDown = caret?.pointsDown ?: true
        val top = if (pointsDown) 0f else band
        val bottom = if (pointsDown) max(0f, size.height - band) else size.height
        val path = Path()
        val c = caret
        if (c == null || size.width <= m.caretWidth) {
            path.addRect(Rect(Offset(0f, top), Offset(size.width, bottom)))
            return Outline.Generic(path)
        }
        val x = min(max(c.x, half), size.width - half)
        if (c.pointsDown) {
            path.moveTo(0f, top)
            path.lineTo(size.width, top)
            path.lineTo(size.width, bottom)
            path.lineTo(x + half, bottom)
            path.lineTo(x, bottom + band)
            path.lineTo(x - half, bottom)
            path.lineTo(0f, bottom)
        } else {
            path.moveTo(0f, bottom)
            path.lineTo(0f, top)
            path.lineTo(x - half, top)
            path.lineTo(x, top - band)
            path.lineTo(x + half, top)
            path.lineTo(size.width, top)
            path.lineTo(size.width, bottom)
        }
        path.close()
        return Outline.Generic(path)
    }
}
