package app.tuji.android.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which part of a readout is allowed to move.
 *
 * The strings these numbers live in are sentences — 「完成 3 / 12 字」 — and the
 * whole point is that only the count rolls. Getting the split wrong does not
 * crash anything; it sends 完成 sliding up the screen, which is the kind of
 * thing that looks like a rendering bug rather than a feature.
 */
class NumericRunsTest {

    private fun split(text: String) = numericRuns(text).map { it.text to it.isDigits }

    @Test fun `a sentence keeps its words still and moves only the numbers`() {
        assertEquals(
            listOf("完成 " to false, "3" to true, " / " to false, "12" to true, " 字" to false),
            split("完成 3 / 12 字"),
        )
    }

    @Test fun `a multi-digit number is one wheel, not one per digit`() {
        // 365 rolling as three independent digits reads as three counters that
        // happen to be adjacent.
        assertEquals(listOf("365" to true), split("365"))
    }

    @Test fun `a unit stays attached to nothing`() {
        assertEquals(listOf("62" to true, "%" to false), split("62%"))
    }

    @Test fun `two numbers with a mark between them stay two runs`() {
        assertEquals(listOf("3" to true, "→" to false, "7" to true), split("3→7"))
    }

    @Test fun `text with no digits is one run that never animates`() {
        assertEquals(listOf("尚無紀錄" to false), split("尚無紀錄"))
    }

    @Test fun `an empty string has no runs at all`() {
        assertEquals(emptyList<Pair<String, Boolean>>(), split(""))
    }
}
