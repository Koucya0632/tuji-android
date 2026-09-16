package app.tuji.android.core.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the 詞塊 card goes.
 *
 * This is the half that can be wrong in ways a screenshot of one device at one
 * text size will not show: a card running off the top of a short screen, a
 * caret leaving the card when the word sits at the start of a line, the
 * bottom-anchored fallback quietly becoming unreachable.
 */
class GlossCalloutPlacementTest {

    // 16 / 8 / 24 / 8 / 16 — the dp values at density 1, so the numbers below
    // read as the design distances they are.
    private val m = GlossCalloutPlacement.Metrics(
        caretWidth = 16f,
        caretHeight = 8f,
        sideMargin = 24f,
        anchorGap = 8f,
        edgeMargin = 16f,
    )

    private val container = Size(360f, 640f)
    private val card = Size(312f, 120f)

    private fun word(top: Float, left: Float = 100f, width: Float = 40f) =
        Rect(left, top, left + width, top + 20f)

    @Test fun `above is the preference, so the card sits between the eye and the word`() {
        val result = GlossCalloutPlacement.place(word(top = 400f), card, container, m)!!
        assertTrue("caret on the bottom edge", result.pointsDown)
        // 400 - 8 gap - 120 card
        assertEquals(272f, result.top, 0.001f)
    }

    @Test fun `a word near the top pushes the card below it`() {
        // 100 - 8 - 120 is negative, so above does not fit.
        val result = GlossCalloutPlacement.place(word(top = 100f), card, container, m)!!
        assertTrue("caret on the top edge", !result.pointsDown)
        // bottom of the word (120) + 8 gap
        assertEquals(128f, result.top, 0.001f)
    }

    @Test fun `neither side fits, so there is no placement and no caret`() {
        // A tall card on a short screen: the fallback is the live path here,
        // not dead code, and the caller parks the card at the bottom.
        val tall = Size(312f, 560f)
        assertNull(GlossCalloutPlacement.place(word(top = 300f), tall, container, m))
    }

    @Test fun `an unmeasured card has no placement`() {
        // One frame exists between raising the card and knowing its size.
        assertNull(GlossCalloutPlacement.place(word(top = 400f), Size(312f, 0f), container, m))
    }

    @Test fun `the caret follows the word`() {
        // Word centre 120, less the 24 side margin the card starts at.
        val result = GlossCalloutPlacement.place(word(top = 400f, left = 100f), card, container, m)!!
        assertEquals(96f, result.caretX, 0.001f)
    }

    @Test fun `a word at the start of a line keeps the caret inside the card`() {
        // Centre 20 - 24 margin = -4, which would put the caret off the card's
        // own left edge. A word at the start or end of a line is the common
        // case, not an edge one.
        val caret = GlossCalloutPlacement.caretX(word(top = 400f, left = 0f, width = 40f), card.width, m)
        assertEquals(16f, caret, 0.001f) // half the caret + the gap
        val far = GlossCalloutPlacement.caretX(word(top = 400f, left = 340f, width = 20f), card.width, m)
        assertEquals(card.width - 16f, far, 0.001f)
    }

    @Test fun `a card narrower than its own caret centres it rather than inverting the bounds`() {
        val caret = GlossCalloutPlacement.caretX(word(top = 400f), cardWidth = 20f, m = m)
        assertEquals(10f, caret, 0.001f)
    }

    @Test fun `the card keeps the page margin on both sides`() {
        assertEquals(312f, GlossCalloutPlacement.cardWidth(container.width, m), 0.001f)
        // Never negative: a container narrower than two margins is a
        // measurement mid-layout, not a card to draw.
        assertEquals(0f, GlossCalloutPlacement.cardWidth(10f, m), 0.001f)
    }
}
