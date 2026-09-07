package app.tuji.android.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaScaleLadderTest {

    @Test
    fun `at the app's headword size the ladder collapses to one rung`() {
        // 26 * 0.5 == 13, which is exactly the floor. There is no room left to
        // shrink into, so one rung at full size is the whole answer — and every
        // headword on every screen therefore renders identically.
        assertEquals(listOf(1f), FuriganaScaleLadder.steps(baseSize = 26f))
    }

    @Test
    fun `a larger headword gets five rungs down to the ruby floor`() {
        val steps = FuriganaScaleLadder.steps(baseSize = 56f)
        assertEquals(5, steps.size)
        assertEquals(1f, steps.first(), 0.0001f)
        // The floor is whatever keeps the ruby at 13: 13 / (56 * 0.5).
        assertEquals(13f / 28f, steps.last(), 0.0001f)
    }

    @Test
    fun `rungs descend`() {
        val steps = FuriganaScaleLadder.steps(baseSize = 56f)
        assertEquals(steps.sortedDescending(), steps)
    }

    @Test
    fun `shrinking never puts ruby under the CJK floor`() {
        // Only meaningful where there is room to shrink — 26 is the smallest
        // base whose ruby starts at or above the floor, so it is the smallest
        // base for which "do not shrink past legible" is a constraint at all.
        for (base in listOf(26f, 34f, 40f, 56f)) {
            val smallest = FuriganaScaleLadder.steps(base).last()
            val ruby = base * TujiHeadwordSize.RUBY_RATIO * smallest
            assertTrue(
                "base=$base produced ruby=$ruby",
                ruby >= FuriganaScaleLadder.MINIMUM_RUBY_POINT - 0.0001f,
            )
        }
    }

    @Test
    fun `a base under the floor draws once and does not pretend otherwise`() {
        // At 20 the ruby is already 10 before anything shrinks. There is no rung
        // that fixes that, so the ladder returns one and the caller draws a
        // ruby it knows is too small — which is why 26 is the app's headword
        // size and this branch is not reachable from a Tuji screen.
        val steps = FuriganaScaleLadder.steps(baseSize = 20f)
        assertEquals(listOf(1f), steps)
        assertTrue(20f * TujiHeadwordSize.RUBY_RATIO < FuriganaScaleLadder.MINIMUM_RUBY_POINT)
    }

    @Test
    fun `a base too small to ever be legible still draws at full size`() {
        // Below the floor there is nothing to shrink into. One rung is the
        // honest answer rather than an empty list a caller has to guard.
        assertEquals(listOf(1f), FuriganaScaleLadder.steps(baseSize = 18f))
        assertEquals(listOf(1f), FuriganaScaleLadder.steps(baseSize = 0f))
    }
}
