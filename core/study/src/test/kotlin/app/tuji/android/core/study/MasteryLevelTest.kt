package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class MasteryLevelTest {

    @Test fun `no record and zero both read as 未學`() {
        assertEquals(MasteryLevel.NotLearned, MasteryLevel.of(null))
        assertEquals(MasteryLevel.NotLearned, MasteryLevel.of(0))
    }

    /** Every boundary, from both sides — the whole content of the rule. */
    @Test fun `the four thresholds`() {
        assertEquals(MasteryLevel.Know, MasteryLevel.of(1))
        assertEquals(MasteryLevel.Know, MasteryLevel.of(34))
        assertEquals(MasteryLevel.Familiar, MasteryLevel.of(35))
        assertEquals(MasteryLevel.Familiar, MasteryLevel.of(59))
        assertEquals(MasteryLevel.Proficient, MasteryLevel.of(60))
        assertEquals(MasteryLevel.Proficient, MasteryLevel.of(79))
        assertEquals(MasteryLevel.Expert, MasteryLevel.of(80))
        assertEquals(MasteryLevel.Expert, MasteryLevel.of(100))
    }

    /**
     * One correct answer lands the EMA around 21–30. If that landed on 熟悉 the
     * top of the ladder would be two answers away and mean nothing.
     */
    @Test fun `one right answer is only 知道`() {
        assertEquals(MasteryLevel.Know, MasteryLevel.of(21))
        assertEquals(MasteryLevel.Know, MasteryLevel.of(30))
    }

    /** A score outside 0–100 is a server bug, not a crash and not a hole. */
    @Test fun `scores outside the range still land on a tier`() {
        assertEquals(MasteryLevel.NotLearned, MasteryLevel.of(-5))
        assertEquals(MasteryLevel.Expert, MasteryLevel.of(140))
    }

    /** The badge is read as an amount, so 精通 may not leave a segment empty. */
    @Test fun `精通 fills the whole bar`() {
        assertEquals(0, MasteryLevel.NotLearned.filledSegments)
        assertEquals(1, MasteryLevel.Know.filledSegments)
        assertEquals(2, MasteryLevel.Familiar.filledSegments)
        assertEquals(3, MasteryLevel.Proficient.filledSegments)
        assertEquals(5, MasteryLevel.Expert.filledSegments)
    }
}
