package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy, not the plumbing.
 *
 * Every one of these is a bug that has actually shipped on one platform or the
 * other: a pull that re-read progress but not the mastery bar beside it, and a
 * guest pull that reached for account-scoped stores that answer 401 to nobody.
 */
class LearningRefreshTest {

    @Test fun `我 re-reads the bar it draws`() {
        val targets = LearningRefreshCause.PulledMe(isGuest = false).targets
        assertTrue("熟練度 is a whole section of that page", RefreshTarget.Mastery in targets)
        assertTrue(RefreshTarget.Progress in targets)
    }

    @Test fun `我 does not re-read numbers it never shows`() {
        // 待複習 and 今天學了幾個 live on 今日. Asking for them here is a request
        // nobody reads the answer to.
        assertEquals(
            setOf(RefreshTarget.Progress, RefreshTarget.Mastery),
            LearningRefreshCause.PulledMe(isGuest = false).targets,
        )
    }

    @Test fun `a guest pulling 我 refreshes nothing at all`() {
        // The page has nothing on it that came from an account.
        assertEquals(emptySet<RefreshTarget>(), LearningRefreshCause.PulledMe(isGuest = true).targets)
    }

    @Test fun `a guest pulling 今日 still re-reads the public half`() {
        // The word list and the themes are public; the progress numbers are not.
        assertEquals(
            setOf(RefreshTarget.Catalogue),
            LearningRefreshCause.PulledToday(isGuest = true).targets,
        )
    }

    @Test fun `今日 signed in re-reads everything it prints`() {
        assertEquals(
            setOf(
                RefreshTarget.Progress,
                RefreshTarget.Stats,
                RefreshTarget.Mastery,
                RefreshTarget.Catalogue,
            ),
            LearningRefreshCause.PulledToday(isGuest = false).targets,
        )
    }

    @Test fun `no cause reaches an account-scoped store for a guest`() {
        val accountScoped = setOf(RefreshTarget.Progress, RefreshTarget.Stats, RefreshTarget.Mastery)
        val guestCauses = listOf(
            LearningRefreshCause.PulledToday(isGuest = true),
            LearningRefreshCause.PulledMe(isGuest = true),
        )
        guestCauses.forEach { cause ->
            assertTrue(
                "$cause reached an account store",
                cause.targets.intersect(accountScoped).isEmpty(),
            )
        }
    }
}
