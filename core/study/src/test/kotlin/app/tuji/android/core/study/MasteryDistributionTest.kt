package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryDistributionTest {

    @Test fun `an empty map is empty, not four zeroes with a bar`() {
        val spread = MasteryDistribution.of(emptyMap())
        assertTrue(spread.isEmpty)
        assertEquals(0, spread.total)
    }

    @Test fun `each tier boundary lands where MasteryLevel puts it`() {
        val spread = MasteryDistribution.of(
            mapOf(
                "a" to 1, "b" to 34,
                "c" to 35, "d" to 59,
                "e" to 60, "f" to 79,
                "g" to 80, "h" to 100,
            ),
        )
        assertEquals(2, spread.know)
        assertEquals(2, spread.familiar)
        assertEquals(2, spread.proficient)
        assertEquals(2, spread.expert)
        assertEquals(8, spread.total)
    }

    /**
     * A row can sit at 0 — decay reaches it. It must not read as 知道, and there
     * is no tier here for it to fall into either.
     */
    @Test fun `a score of zero is 未學, and 未學 is not counted`() {
        val spread = MasteryDistribution.of(mapOf("a" to 0, "b" to 0, "c" to 42))
        assertEquals(0, spread.know)
        assertEquals(1, spread.familiar)
        assertEquals(1, spread.total)
    }

    /**
     * Three key shapes share one map: bare ids, `atlas:` for 自製圖鑑 and
     * `saved:` for 物見. All three are words this account studies.
     */
    @Test fun `自製圖鑑 and 物見 words count too`() {
        val spread = MasteryDistribution.of(
            mapOf("tomato" to 85, "atlas:6f1c" to 90, "saved:someones-cat" to 95),
        )
        assertEquals(3, spread.expert)
        assertEquals(3, spread.total)
    }

    /** The bar and its legend read the same list, so the order must be fixed. */
    @Test fun `segments run in ladder order`() {
        val spread = MasteryDistribution.of(mapOf("a" to 10, "b" to 40, "c" to 70, "d" to 90))
        assertEquals(
            listOf(
                MasteryLevel.Know,
                MasteryLevel.Familiar,
                MasteryLevel.Proficient,
                MasteryLevel.Expert,
            ),
            spread.segments.map { it.level },
        )
        assertEquals(listOf(1, 1, 1, 1), spread.segments.map { it.words })
    }

    /** 完成度 owns the denominator question. This readout has none at all. */
    @Test fun `total is studied words, never the dictionary size`() {
        assertEquals(2, MasteryDistribution.of(mapOf("a" to 5, "b" to 95)).total)
    }
}
