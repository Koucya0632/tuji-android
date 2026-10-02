package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy, not the plumbing.
 *
 * A bug that has actually shipped: a pull that re-read progress but not the
 * mastery bar beside it.
 */
class LearningRefreshTest {

    @Test fun `我 re-reads the bar it draws`() {
        val targets = LearningRefreshCause.PulledMe.targets
        assertTrue("熟練度 is a whole section of that page", RefreshTarget.Mastery in targets)
        assertTrue(RefreshTarget.Progress in targets)
    }

    @Test fun `我 does not re-read numbers it never shows`() {
        // 待複習 and 今天學了幾個 live on 今日. Asking for them here is a request
        // nobody reads the answer to.
        assertEquals(
            setOf(RefreshTarget.Progress, RefreshTarget.Mastery),
            LearningRefreshCause.PulledMe.targets,
        )
    }

    @Test fun `今日 re-reads everything it prints`() {
        assertEquals(
            setOf(
                RefreshTarget.Progress,
                RefreshTarget.Stats,
                RefreshTarget.Mastery,
                RefreshTarget.Catalogue,
            ),
            LearningRefreshCause.PulledToday.targets,
        )
    }
}
