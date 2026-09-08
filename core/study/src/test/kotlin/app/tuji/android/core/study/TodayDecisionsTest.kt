package app.tuji.android.core.study

import app.tuji.android.core.model.StudyStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayDecisionsTest {

    private fun stats(total: Int = 557, seen: Int = 20, due: Int = 0, new: Int = 537, todayNew: Int? = 0) =
        StudyStats(total = total, seen = seen, due = due, new = new, todayNew = todayNew)

    private fun today(
        isGuest: Boolean = false,
        stats: StudyStats? = stats(),
        dailyGoal: Int = 10,
    ) = TodayDecisions(TodayInputs(isGuest = isGuest, stats = stats, dailyGoal = dailyGoal))

    // Before the numbers exist

    @Test fun `nothing is claimed before stats land`() {
        val t = today(stats = null)
        assertEquals(TodaySubtitle.Unknown, t.subtitle)
        assertNull("a hint about today's numbers, before them, is a guess", t.heroHint)
        assertEquals(TodayNewBlock.None, t.newBlock)
    }

    @Test fun `an empty account is not told it has learned everything`() {
        // The failure this guards: 「都學過了」 flashing on a brand-new account
        // while the first fetch is still in flight.
        assertEquals(TodaySubtitle.Unknown, today(stats = null).subtitle)
    }

    // The subtitle ladder

    @Test fun `a review backlog is what the line says first`() {
        assertEquals(TodaySubtitle.ReviewDue, today(stats = stats(due = 7)).subtitle)
    }

    @Test fun `the goal never contradicts the badge beside it`() {
        val t = today(stats = stats(todayNew = 10), dailyGoal = 10)
        assertTrue(t.goalReached)
        assertEquals(TodaySubtitle.GoalReached, t.subtitle)
    }

    @Test fun `partway through the day it says what was done`() {
        assertEquals(TodaySubtitle.NewDoneToday, today(stats = stats(todayNew = 3)).subtitle)
    }

    @Test fun `a fresh day with words left offers them`() {
        assertEquals(TodaySubtitle.NewAvailable, today(stats = stats(todayNew = 0, new = 40)).subtitle)
    }

    @Test fun `a finished catalogue says so`() {
        assertEquals(TodaySubtitle.AllLearned, today(stats = stats(new = 0, todayNew = 0)).subtitle)
    }

    @Test fun `a guest is told they are browsing, whatever the numbers say`() {
        assertEquals(
            TodaySubtitle.GuestBrowsing,
            today(isGuest = true, stats = stats(due = 9)).subtitle,
        )
    }

    // What is offered

    @Test fun `複習 is dead when nothing is due`() {
        assertTrue(today(stats = stats(due = 0)).reviewDisabled)
        assertFalse(today(stats = stats(due = 1)).reviewDisabled)
    }

    @Test fun `a guest is offered neither`() {
        val t = today(isGuest = true, stats = stats(due = 5, new = 100))
        assertTrue(t.reviewDisabled)
        assertTrue(t.newDisabled)
    }

    @Test fun `an empty catalogue blocks 學新字 with a reason`() {
        val t = today(stats = stats(new = 0))
        assertEquals(TodayNewBlock.AllLearned, t.newBlock)
        assertTrue(t.newDisabled)
    }

    @Test fun `a big backlog blocks 學新字 rather than letting it bounce`() {
        // computeNewLimit falls to 0 once the backlog is large enough; without
        // this the user enters the flow and is thrown straight back out.
        val due = (1..500).first { StudyQuotas.computeNewLimit(goal = 10, due = it) == 0 }
        val t = today(stats = stats(due = due, new = 200))
        assertEquals(TodayNewBlock.ReviewBacklog, t.newBlock)
    }

    // The hint

    @Test fun `a blocked 學新字 speaks before the quota note`() {
        val t = today(stats = stats(new = 0, due = 0))
        assertEquals(TodayHeroHint.NewBlocked, t.heroHint)
    }

    @Test fun `a tapered quota is reported with its numbers`() {
        val due = (1..500).firstOrNull {
            StudyQuotas.computeNewLimit(goal = 10, due = it) in 1..9
        } ?: error("no backlog tapers the quota — the rule changed")
        val t = today(stats = stats(due = due, new = 200, todayNew = 0))
        val (reportedDue, limit) = t.quotaAdjustment!!
        assertEquals(due, reportedDue)
        assertTrue(limit in 1..9)
        // 複習 is available in this state, so the quota note is what speaks.
        assertEquals(TodayHeroHint.QuotaAdjusted, t.heroHint)
    }

    @Test fun `a met goal is not also a quota complaint`() {
        assertNull(today(stats = stats(todayNew = 10, due = 3), dailyGoal = 10).quotaAdjustment)
    }

    @Test fun `nothing to review is the last thing said`() {
        val t = today(stats = stats(due = 0, new = 200, todayNew = 3))
        assertEquals(TodayHeroHint.NothingToReview, t.heroHint)
    }

    @Test fun `a zero goal cannot divide the day by nothing`() {
        val t = today(stats = stats(todayNew = 0), dailyGoal = 0)
        assertFalse(t.goalReached)
        assertEquals(TodaySubtitle.NewAvailable, t.subtitle)
    }
}
