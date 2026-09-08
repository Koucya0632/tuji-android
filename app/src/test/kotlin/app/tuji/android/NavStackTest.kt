package app.tuji.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStackTest {

    private val shelf = AppRoute.Shelf("kitchen", "廚房")
    private val word = AppRoute.Word("kettle")

    @Test fun `it starts on 今日 with nowhere to go back to`() {
        val nav = NavStack()
        assertEquals(AppRoute.Today, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test fun `back never empties the stack`() {
        // A pop with nothing under it would leave `current` throwing.
        assertEquals(AppRoute.Today, NavStack().pop().current)
    }

    @Test fun `圖鑑 to 分類 to 詞 to 相關的詞 unwinds one at a time`() {
        val nav = NavStack()
            .select(AppRoute.Atlas)
            .push(shelf)
            .push(word)
            .push(AppRoute.Word("kettle-lid"))

        assertEquals(AppRoute.Word("kettle-lid"), nav.current)
        assertEquals(word, nav.pop().current)
        assertEquals(shelf, nav.pop().pop().current)
        assertEquals(AppRoute.Atlas, nav.pop().pop().pop().current)
    }

    @Test fun `the tab bar marks the tab under whatever is stacked on it`() {
        val nav = NavStack().select(AppRoute.Atlas).push(shelf).push(word)
        assertEquals(AppRoute.Atlas, nav.tab)
    }

    @Test fun `tapping the tab you are already on goes back to its top`() {
        val nav = NavStack().select(AppRoute.Atlas).push(shelf).push(word)
        val again = nav.select(AppRoute.Atlas)
        assertEquals(AppRoute.Atlas, again.current)
        assertFalse("and nothing is left stacked above it", again.canGoBack && again.entries.size > 2)
    }

    @Test fun `switching tabs does not lose the one you came from`() {
        val nav = NavStack().select(AppRoute.Atlas).select(AppRoute.Search)
        assertEquals(AppRoute.Search, nav.current)
        assertEquals(AppRoute.Atlas, nav.pop().current)
    }

    @Test fun `a study flow stacks and pops like anything else`() {
        val nav = NavStack().push(AppRoute.Review)
        assertEquals(AppRoute.Review, nav.current)
        assertEquals(AppRoute.Today, nav.pop().current)
    }

    @Test fun `two different words are two entries, not one`() {
        val nav = NavStack().push(AppRoute.Word("a")).push(AppRoute.Word("b"))
        assertEquals(3, nav.entries.size)
        assertEquals(AppRoute.Word("a"), nav.pop().current)
    }

    @Test fun `物見 stacks the same way 圖鑑 does`() {
        val nav = NavStack()
            .select(AppRoute.Community)
            .push(AppRoute.PublicItem("atlas-abc"))
            .push(AppRoute.Author("TJ11111111"))
        assertEquals(AppRoute.Community, nav.tab)
        assertEquals(AppRoute.PublicItem("atlas-abc"), nav.pop().current)
    }

    @Test fun `going back to a tab you never opened lands on it fresh`() {
        val nav = NavStack().select(AppRoute.Search)
        assertEquals(AppRoute.Search, nav.current)
        assertTrue(nav.canGoBack)
    }
}
