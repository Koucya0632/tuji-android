package app.tuji.android.core.study

import app.tuji.android.core.model.CategoryProgress
import app.tuji.android.core.model.StudyMode
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
        stats: StudyStats? = stats(),
        dailyGoal: Int = 10,
    ) = TodayDecisions(TodayInputs(stats = stats, dailyGoal = dailyGoal))

    private fun selection(picked: List<String>, seen: Int, total: Int, settingsLoaded: Boolean = true) =
        CompletionReadout(
            CompletionReadout.Inputs(
                settingsLoaded = settingsLoaded,
                studyCategories = picked,
                seenInSelection = seen,
                totalInSelection = total,
                dictionaryCount = 857,
                dictionaryCountInSelection = total,
            ),
        )

    private fun studying(
        completion: CompletionReadout,
        stats: StudyStats? = stats(),
        loaded: Boolean = true,
        freeThemes: FreeThemesProgress? = null,
    ) = TodayDecisions(TodayInputs(stats = stats, completion = completion, progressLoaded = loaded, freeThemes = freeThemes))

    // Before the numbers exist

    @Test fun `nothing is claimed before stats land`() {
        val t = today(stats = null)
        assertEquals(TodayCall.Unknown, t.call)
        assertNull("no way out is offered for a call that names nothing", t.footer)
        assertEquals(TodayNewBlock.None, t.newBlock)
    }

    // The heading

    @Test fun `the heading names the first theme and the count`() {
        val t = studying(selection(listOf("kitchen", "bathroom", "office"), seen = 3, total = 90))
        assertEquals(TodayHeading.Themes(firstId = "kitchen", count = 3), t.heading)
    }

    @Test fun `no themes is said as no themes`() {
        assertEquals(TodayHeading.NoThemes, studying(selection(emptyList(), seen = 0, total = 0)).heading)
    }

    @Test fun `settings not loaded yet is not no themes`() {
        val t = studying(selection(emptyList(), seen = 0, total = 0, settingsLoaded = false), loaded = false)
        assertEquals(TodayHeading.Unknown, t.heading)
        assertTrue(t.call != TodayCall.PickThemes)
        assertTrue(t.newBlock != TodayNewBlock.NoThemes)
    }

    // The one button

    @Test fun `anything due is reviewed first, with its count`() {
        assertEquals(TodayCall.Study(StudyMode.Review, 7), today(stats = stats(due = 7)).call)
    }

    @Test fun `nothing due learns new words, capped by the goal`() {
        assertEquals(TodayCall.Study(StudyMode.New, 10), today(stats = stats(due = 0, new = 40)).call)
    }

    @Test fun `the new count is capped by what is left`() {
        val t = studying(selection(listOf("kitchen"), seen = 61, total = 64))
        assertEquals(TodayCall.Study(StudyMode.New, 3), t.call)
    }

    @Test fun `no themes sends the button to pick them`() {
        val t = studying(selection(emptyList(), seen = 0, total = 0))
        assertEquals(TodayCall.PickThemes, t.call)
        assertEquals(TodayNewBlock.NoThemes, t.newBlock)
        assertNull(t.footer)
    }

    @Test fun `picked themes with no words block the button`() {
        val t = studying(selection(listOf("kitchen"), seen = 0, total = 0))
        assertEquals(TodayCall.Blocked(TodayBlock.NoCards), t.call)
        assertEquals(TodayFooter.ChangeThemes, t.footer)
    }

    @Test fun `what is left counts the picked themes, not the whole catalogue`() {
        // The server still has 537 new words, but the picked themes are done.
        val t = studying(selection(listOf("kitchen"), seen = 64, total = 64))
        assertEquals(0, t.newAvailable)
        assertEquals(TodayCall.Blocked(TodayBlock.ThemesExhausted), t.call)
        assertEquals(TodayFooter.ChangeThemes, t.footer)
    }

    @Test fun `before the themes' progress lands the server's count stands in`() {
        val t = studying(selection(listOf("kitchen"), seen = 0, total = 0), loaded = false)
        assertEquals(537, t.newAvailable)
        assertEquals(TodayNewBlock.None, t.newBlock)
    }

    // The footer

    @Test fun `reviewing with new words left offers to switch`() {
        assertEquals(TodayFooter.SwitchToNew, today(stats = stats(due = 5, new = 40)).footer)
    }

    @Test fun `reviewing with no new words offers nothing`() {
        assertNull(today(stats = stats(due = 5, new = 0)).footer)
    }

    @Test fun `a backlog that eats the quota offers no switch`() {
        val due = (1..500).first { StudyQuotas.computeNewLimit(goal = 10, due = it) == 0 }
        val t = today(stats = stats(due = due, new = 200))
        assertEquals(TodayNewBlock.ReviewBacklog, t.newBlock)
        assertNull(t.footer)
    }

    @Test fun `learning new words needs no way out`() {
        assertNull(today(stats = stats(due = 0, new = 40)).footer)
    }

    // Free themes, for a non-member

    @Test fun `every free theme learned unlocks rather than switches`() {
        val t = studying(
            selection(listOf("kitchen"), seen = 64, total = 64),
            freeThemes = FreeThemesProgress(total = 150, unseen = 0),
        )
        assertEquals(TodayCall.Blocked(TodayBlock.FreeThemesLearned), t.call)
        assertEquals(TodayFooter.UnlockAllThemes, t.footer)
    }

    @Test fun `free themes still unlearned keep 換幾個主題`() {
        val t = studying(
            selection(listOf("kitchen"), seen = 64, total = 64),
            freeThemes = FreeThemesProgress(total = 150, unseen = 30),
        )
        assertEquals(TodayFooter.ChangeThemes, t.footer)
    }

    @Test fun `free themes are not all learned before progress lands`() {
        val t = studying(
            selection(listOf("kitchen"), seen = 0, total = 0),
            loaded = false,
            freeThemes = FreeThemesProgress(total = 0, unseen = 0),
        )
        assertFalse(t.freeThemesLearned)
    }

    @Test fun `free-theme progress counts unlearned words theme by theme`() {
        // A withdrawn word leaves kitchen's seen above its total; summed, that
        // surplus would hide fruit's two unlearned words.
        val p = FreeThemesProgress.of(
            studyable = listOf("kitchen", "fruit"),
            progress = listOf(
                CategoryProgress("kitchen", total = 10, seen = 12),
                CategoryProgress("fruit", total = 5, seen = 3),
                CategoryProgress("office", total = 60, seen = 0),
            ),
        )!!
        assertEquals(15, p.total)
        assertEquals(2, p.unseen)
        assertFalse(p.allLearned)
    }

    @Test fun `no gate is no free-theme progress`() {
        assertNull(FreeThemesProgress.of(null, emptyList()))
        assertNull(FreeThemesProgress.of(emptyList(), emptyList()))
    }

    // What is offered

    @Test fun `an empty catalogue blocks 學新字 with a reason`() {
        val t = today(stats = stats(new = 0))
        assertEquals(TodayNewBlock.AllLearned, t.newBlock)
        assertTrue(t.newDisabled)
    }

    @Test fun `a zero goal cannot divide the day by nothing`() {
        assertEquals(TodayCall.Study(StudyMode.New, 1), today(stats = stats(new = 40), dailyGoal = 0).call)
    }
}
