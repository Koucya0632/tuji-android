package app.tuji.android.core.study

import app.tuji.android.core.model.CategoryProgress
import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins 完成度 — the rule 今日's hero and 我's completion card share. Ported
 * from iOS's `CompletionReadoutTests`, case for case.
 */
class CompletionReadoutTest {

    private fun inputs(
        isGuest: Boolean = false,
        settingsLoaded: Boolean = true,
        studyCategories: List<String> = listOf("kitchen"),
        guestLearnedCount: Int = 0,
        seenInSelection: Int = 40,
        totalInSelection: Int = 120,
        dictionaryCount: Int = 480,
        dictionaryCountInSelection: Int = 60,
    ) = CompletionReadout.Inputs(
        isGuest = isGuest,
        settingsLoaded = settingsLoaded,
        studyCategories = studyCategories,
        guestLearnedCount = guestLearnedCount,
        seenInSelection = seenInSelection,
        totalInSelection = totalInSelection,
        dictionaryCount = dictionaryCount,
        dictionaryCountInSelection = dictionaryCountInSelection,
    )

    @Test fun `a guest's progress is the local learned set, not the empty server rows`() {
        val readout = CompletionReadout(
            inputs(isGuest = true, studyCategories = emptyList(), guestLearnedCount = 37, seenInSelection = 0, totalInSelection = 0),
        )
        assertEquals(37, readout.seen)
        assertEquals(480, readout.total)
    }

    /** 自定義 and 物見 have no published cards, so the server has no rows and the fallback runs. */
    @Test fun `the denominator describes the selection, not the whole dictionary`() {
        val readout = CompletionReadout(
            inputs(
                studyCategories = listOf("custom", "community"),
                seenInSelection = 0,
                totalInSelection = 0,
                dictionaryCountInSelection = 12,
            ),
        )
        assertEquals(12, readout.total)
        assertEquals(CompletionScope.SelectedThemes, readout.scope)
    }

    @Test fun `with no themes picked the fraction reads 0 of 0, not a whole-catalogue number`() {
        val readout = CompletionReadout(inputs(studyCategories = emptyList(), seenInSelection = 300, totalInSelection = 480))
        assertEquals(0, readout.seen)
        assertEquals(0, readout.total)
        assertEquals(CompletionScope.Pending, readout.scope)
        assertEquals(0, readout.percent)
    }

    @Test fun `the server's count wins whenever it has one`() {
        val readout = CompletionReadout(inputs())
        assertEquals(40, readout.seen)
        assertEquals(120, readout.total)
    }

    /** Settings have not arrived: an empty list is "we don't know", not the prompt. */
    @Test fun `the whole dictionary stands in only when nothing is selected`() {
        val readout = CompletionReadout(
            inputs(settingsLoaded = false, studyCategories = emptyList(), seenInSelection = 0, totalInSelection = 0),
        )
        assertEquals(480, readout.total)
        assertEquals(CompletionScope.WholeDictionary, readout.scope)
    }

    @Test fun `a withdrawn word can leave seen above total, and the ratio still stops at 1`() {
        val readout = CompletionReadout(inputs(seenInSelection = 124, totalInSelection = 120))
        assertEquals(1.0, readout.ratio, 0.0)
        assertEquals(100, readout.percent)
    }

    @Test fun `a zero denominator is zero, not a division by zero`() {
        assertEquals(0.0, CompletionReadout.ratio(0, 0), 0.0)
        assertEquals(0.0, CompletionReadout.ratio(5, 0), 0.0)
    }

    @Test fun `the percentage is derived from the ratio, so it cannot disagree with the bar`() {
        val readout = CompletionReadout(inputs(seenInSelection = 40, totalInSelection = 120))
        assertEquals(33, readout.percent)
        assertEquals(CompletionReadout.ratio(40, 120), readout.ratio, 0.0)
    }

    @Test fun `an empty theme list only means pick themes once settings have loaded`() {
        assertFalse(CompletionReadout(inputs(settingsLoaded = false, studyCategories = emptyList())).showsThemePrompt)
        assertTrue(CompletionReadout(inputs(settingsLoaded = true, studyCategories = emptyList())).showsThemePrompt)
    }

    @Test fun `a guest is never prompted to pick themes`() {
        assertFalse(CompletionReadout(inputs(isGuest = true, studyCategories = emptyList())).showsThemePrompt)
    }

    // The mapping — what the app holds → the facts above.

    @Test fun `every scoped number is measured against the same selection`() {
        val inputs = CompletionReadout.Inputs.from(
            isGuest = false,
            settingsLoaded = true,
            studyCategories = listOf("kitchen"),
            progress = listOf(CategoryProgress("kitchen", total = 30, seen = 12), CategoryProgress("office", total = 50, seen = 40)),
            words = listOf(Word("a", "a", category = "kitchen"), Word("b", "b", category = "kitchen"), Word("c", "c", category = "office")),
        )
        assertEquals(12, inputs.seenInSelection)
        assertEquals(30, inputs.totalInSelection)
        assertEquals(2, inputs.dictionaryCountInSelection)
        // The unscoped one stays unscoped: it is the denominator for "nothing picked", and nothing else.
        assertEquals(3, inputs.dictionaryCount)
    }

    /** Before settings arrive, an empty list is not a pick, and the server's whole count stands. */
    @Test fun `an empty selection sums every server row`() {
        val inputs = CompletionReadout.Inputs.from(
            isGuest = false,
            settingsLoaded = false,
            studyCategories = emptyList(),
            progress = listOf(CategoryProgress("kitchen", total = 30, seen = 12), CategoryProgress("office", total = 50, seen = 40)),
            words = emptyList(),
        )
        assertEquals(52, CompletionReadout(inputs).seen)
        assertEquals(80, CompletionReadout(inputs).total)
    }
}
