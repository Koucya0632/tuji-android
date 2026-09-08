package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnedRatingTest {

    @Test fun `a clean run posts what the user said`() {
        SRSRating.entries.forEach {
            assertEquals(it, LearnedRating.effective(it, mistakes = 0))
        }
    }

    @Test fun `one mistake drops a level`() {
        assertEquals(SRSRating.Good, LearnedRating.effective(SRSRating.Easy, 1))
        assertEquals(SRSRating.Hard, LearnedRating.effective(SRSRating.Good, 1))
        assertEquals(SRSRating.Again, LearnedRating.effective(SRSRating.Hard, 1))
        assertEquals(SRSRating.Again, LearnedRating.effective(SRSRating.Again, 1))
    }

    @Test fun `two or more is 重來, however confident the tap was`() {
        // The self-rating happens before the user is asked to retrieve
        // anything, so 已認識 twice contradicted is not evidence of anything.
        assertEquals(SRSRating.Again, LearnedRating.effective(SRSRating.Easy, 2))
        assertEquals(SRSRating.Again, LearnedRating.effective(SRSRating.Easy, 7))
    }

    @Test fun `a negative count is treated as none rather than crashing`() {
        assertEquals(SRSRating.Easy, LearnedRating.effective(SRSRating.Easy, -1))
    }

    @Test fun `the activity is the one the server already accepts`() {
        assertEquals("new_recognize", LearnedRating.ACTIVITY)
    }
}
