package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors `lib/scheduling.ts`; the same three cases iOS pins. */
class StudyQuotasTest {

    @Test
    fun `the full goal stands while the backlog is small`() {
        assertEquals(10, StudyQuotas.computeNewLimit(goal = 10, due = 0))
        assertEquals(10, StudyQuotas.computeNewLimit(goal = 10, due = 20))
    }

    @Test
    fun `it tapers as the backlog grows`() {
        assertEquals(7, StudyQuotas.computeNewLimit(goal = 10, due = 21))
        assertEquals(7, StudyQuotas.computeNewLimit(goal = 10, due = 50))
        assertEquals(5, StudyQuotas.computeNewLimit(goal = 10, due = 51))
        assertEquals(5, StudyQuotas.computeNewLimit(goal = 10, due = 100))
    }

    @Test
    fun `a heavy backlog means no new cards at all`() {
        // Dig out of what is due before piling on more.
        assertEquals(0, StudyQuotas.computeNewLimit(goal = 10, due = 101))
        assertEquals(0, StudyQuotas.computeNewLimit(goal = 50, due = 500))
    }
}
