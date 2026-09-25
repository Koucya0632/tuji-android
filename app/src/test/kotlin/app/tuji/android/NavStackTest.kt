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
        val nav = NavStack().select(AppRoute.Atlas).select(AppRoute.Community)
        assertEquals(AppRoute.Community, nav.current)
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
        val nav = NavStack().select(AppRoute.Me)
        assertEquals(AppRoute.Me, nav.current)
        assertTrue(nav.canGoBack)
    }

    @Test fun `搜尋 is pushed on the tab it was opened from, not a tab of its own`() {
        val nav = NavStack().select(AppRoute.Atlas).push(AppRoute.Search)
        assertEquals(AppRoute.Atlas, nav.tab)
        assertEquals(AppRoute.Atlas, nav.pop().current)
    }

    @Test fun `every tab root has the bar`() {
        TabShell.tabs.forEach { tab ->
            assertTrue("$tab", TabShell.tabBarVisible(NavStack().select(tab)))
        }
    }

    @Test fun `a focused screen hides the bar whichever tab it was opened from`() {
        listOf(AppRoute.Review, AppRoute.LearnNew, AppRoute.Search, AppRoute.Capture, word).forEach { route ->
            assertFalse("$route from 今天", TabShell.tabBarVisible(NavStack().push(route)))
            assertFalse("$route from 圖鑑", TabShell.tabBarVisible(NavStack().select(AppRoute.Atlas).push(route)))
        }
    }

    @Test fun `今天 and 圖鑑 keep the bar through a push`() {
        assertTrue(TabShell.tabBarVisible(NavStack().select(AppRoute.Atlas).push(shelf)))
        assertTrue(TabShell.tabBarVisible(NavStack().select(AppRoute.Atlas).push(AppRoute.PublicItem("saved-abc"))))
    }

    /** Opened from 今日's strip it keeps the bar; opened from 設定 under 我 it does not. */
    @Test fun `學習主題 follows the tab it was opened from`() {
        assertTrue(TabShell.tabBarVisible(NavStack().push(AppRoute.StudyThemes)))
        assertFalse(TabShell.tabBarVisible(NavStack().select(AppRoute.Me).push(AppRoute.Settings).push(AppRoute.StudyThemes)))
    }

    @Test fun `物見 and 我 hand the window to whatever they open`() {
        assertFalse(TabShell.tabBarVisible(NavStack().select(AppRoute.Community).push(AppRoute.Collection("c"))))
        assertFalse(TabShell.tabBarVisible(NavStack().select(AppRoute.Community).push(AppRoute.Author("TJ1"))))
        assertFalse(TabShell.tabBarVisible(NavStack().select(AppRoute.Me).push(AppRoute.Settings)))
    }

    @Test fun `拍照 sits in the middle of the four tabs`() {
        val at = TabShell.tabs.indexOf(TabShell.captureFollows)
        assertEquals(TabShell.tabs.size / 2 - 1, at)
    }

    @Test fun `every tab root can be swiped between`() {
        TabShell.tabs.forEach { tab ->
            assertTrue("$tab", TabShell.swipeEnabled(NavStack().select(tab)))
        }
    }

    @Test fun `a pushed screen turns the swipe off on every tab`() {
        // Not only on the tab it was pushed from: the race is with the
        // platform's own back gesture, and that exists everywhere.
        assertFalse(TabShell.swipeEnabled(NavStack().select(AppRoute.Atlas).push(shelf)))
        assertFalse(TabShell.swipeEnabled(NavStack().select(AppRoute.Me).push(AppRoute.Settings)))
        assertFalse(TabShell.swipeEnabled(NavStack().push(AppRoute.Word("kettle"))))
    }

    @Test fun `a study session cannot be swiped out of`() {
        // The one that matters most: a session left by accident is answers lost.
        assertFalse(TabShell.swipeEnabled(NavStack().push(AppRoute.Review)))
        assertFalse(TabShell.swipeEnabled(NavStack().push(AppRoute.LearnNew)))
    }
}
