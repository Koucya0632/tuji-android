package app.tuji.android

import app.tuji.android.assistant.TopicChatEntry
import app.tuji.android.core.catalog.CardsSource
import app.tuji.android.core.model.StudyMode
import app.tuji.android.study.StudySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStackTest {

    private val shelf = AtlasRoute.Shelf("kitchen", "廚房")
    private val word = AtlasRoute.Word("kettle")
    private val review = StudyRoute(StudyMode.Review)
    private val learnNew = StudyRoute(StudyMode.New)

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
            .push(AtlasRoute.Word("kettle-lid"))

        assertEquals(AtlasRoute.Word("kettle-lid"), nav.current)
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
        val nav = NavStack().push(review)
        assertEquals(review, nav.current)
        assertEquals(AppRoute.Today, nav.pop().current)
    }

    @Test fun `two different words are two entries, not one`() {
        val nav = NavStack().push(AtlasRoute.Word("a")).push(AtlasRoute.Word("b"))
        assertEquals(3, nav.entries.size)
        assertEquals(AtlasRoute.Word("a"), nav.pop().current)
    }

    @Test fun `物見 stacks the same way 圖鑑 does`() {
        val nav = NavStack()
            .select(AppRoute.Community)
            .push(CommunityRoute.Item("atlas-abc"))
            .push(CommunityRoute.Author("TJ11111111"))
        assertEquals(AppRoute.Community, nav.tab)
        assertEquals(CommunityRoute.Item("atlas-abc"), nav.pop().current)
    }

    @Test fun `going back to a tab you never opened lands on it fresh`() {
        val nav = NavStack().select(AppRoute.Me)
        assertEquals(AppRoute.Me, nav.current)
        assertTrue(nav.canGoBack)
    }

    @Test fun `搜尋 is pushed on the tab it was opened from, not a tab of its own`() {
        val nav = NavStack().select(AppRoute.Atlas).push(AtlasRoute.Search)
        assertEquals(AppRoute.Atlas, nav.tab)
        assertEquals(AppRoute.Atlas, nav.pop().current)
    }

    // ── A page moves the stack only while it is on top ──

    @Test fun `the page on top moves the stack`() {
        val nav = NavStack().push(shelf).push(word)
        assertEquals(shelf, nav.from(word) { pop() }.current)
    }

    @Test fun `a page that has already left cannot pop the one that replaced it`() {
        // 封鎖 answering after 返回: the author's page is gone, and the pop
        // would otherwise take the 合集 under it.
        val author = CommunityRoute.Author("TJ1")
        val nav = NavStack().select(AppRoute.Community).push(CommunityRoute.Collection("c")).push(author).pop()
        assertSame(nav, nav.from(author) { pop() })
    }

    @Test fun `a second tap during the push does not stack the page twice`() {
        val once = NavStack().push(shelf).from(shelf) { push(word) }
        assertSame(once, once.from(shelf) { push(word) })
        assertEquals(3, once.entries.size)
    }

    // ── The tab bar ──

    @Test fun `every tab root has the bar`() {
        TabShell.tabs.forEach { tab ->
            assertTrue("$tab", TabShell.tabBarVisible(NavStack().select(tab)))
        }
    }

    /**
     * Every page, by name. A page added without a line here still compiles —
     * [TabShell.ownsWindow] has no `else`, so it cannot compile without a
     * decision — but this is where the decision is read.
     */
    private val everyPage: Map<AppRoute.Page, Boolean> = mapOf(
        review to true,
        learnNew to true,
        StudyRoute(StudyMode.Review, StudySource.WordList("l1")) to true,
        StudyRoute(StudyMode.New, StudySource.Spot("asakusa")) to true,
        AtlasRoute.Search to true,
        AtlasRoute.SearchFor("cat") to true,
        word to true,
        shelf to false,
        ManageRoute.Capture() to true,
        ManageRoute.Capture(resume = "img-1") to true,
        ManageRoute.Home to false,
        ManageRoute.Card("img-1") to false,
        ManageRoute.CollectionEdit("c1") to false,
        CommunityRoute.Search to true,
        CommunityRoute.Item("atlas-abc") to false,
        CommunityRoute.Author("TJ1") to false,
        CommunityRoute.Collection("c") to false,
        TravelRoute.Destination("japan") to false,
        TravelRoute.City("tokyo", "japan") to false,
        TravelRoute.Spot("asakusa") to false,
        SceneRoute.Practice("konbini-checkout") to true,
        SceneRoute.All to false,
        SceneRoute.Detail("konbini-checkout") to false,
        WordListRoute.All to false,
        WordListRoute.Detail("l1") to false,
        AssistantRoute.CreditStatement to true,
        AssistantRoute.Vocabulary to true,
        AssistantRoute.Grammar to true,
        AssistantRoute.Story to true,
        AssistantRoute.TopicChat(TopicChatEntry.Choose) to true,
        AssistantRoute.TopicChatHistory to true,
        AccountRoute.Membership to true,
        AccountRoute.Settings to false,
        AccountRoute.EditProfile to false,
        AccountRoute.BlockedAuthors to false,
        AccountRoute.StudyThemes to false,
        AccountRoute.ProgressStats to false,
    )

    @Test fun `a page that owns the window hides the bar whichever tab it was opened from`() {
        everyPage.filterValues { it }.keys.forEach { page ->
            TabShell.tabs.forEach { tab ->
                assertFalse("$page from $tab", TabShell.tabBarVisible(NavStack().select(tab).push(page)))
            }
        }
    }

    @Test fun `every page's own rule is the one written down`() {
        everyPage.forEach { (page, owns) -> assertEquals("$page", owns, TabShell.ownsWindow(page)) }
    }

    @Test fun `今天 and 圖鑑 keep the bar through a push`() {
        everyPage.filterValues { !it }.keys.forEach { page ->
            assertTrue("$page from 今天", TabShell.tabBarVisible(NavStack().push(page)))
            assertTrue("$page from 圖鑑", TabShell.tabBarVisible(NavStack().select(AppRoute.Atlas).push(page)))
        }
    }

    /** Opened from 今日's strip it keeps the bar; opened from 設定 under 我 it does not. */
    @Test fun `學習主題 follows the tab it was opened from`() {
        assertTrue(TabShell.tabBarVisible(NavStack().push(AccountRoute.StudyThemes)))
        assertFalse(TabShell.tabBarVisible(NavStack().select(AppRoute.Me).push(AccountRoute.Settings).push(AccountRoute.StudyThemes)))
    }

    @Test fun `物見 and 我 hand the window to whatever they open`() {
        everyPage.keys.forEach { page ->
            assertFalse("$page from 物見", TabShell.tabBarVisible(NavStack().select(AppRoute.Community).push(page)))
            assertFalse("$page from 我", TabShell.tabBarVisible(NavStack().select(AppRoute.Me).push(page)))
        }
    }

    @Test fun `拍照 sits in the middle of the four tabs`() {
        val at = TabShell.tabs.indexOf(TabShell.captureFollows)
        assertEquals(TabShell.tabs.size / 2 - 1, at)
    }

    // ── The swipe ──

    @Test fun `every tab root can be swiped between`() {
        TabShell.tabs.forEach { tab ->
            assertTrue("$tab", TabShell.swipeEnabled(NavStack().select(tab)))
        }
    }

    @Test fun `a pushed screen turns the swipe off on every tab`() {
        // Not only on the tab it was pushed from: the race is with the
        // platform's own back gesture, and that exists everywhere.
        everyPage.keys.forEach { page ->
            TabShell.tabs.forEach { tab ->
                assertFalse("$page over $tab", TabShell.swipeEnabled(NavStack().select(tab).push(page)))
            }
        }
    }

    // ── 拍照's ways out ──

    @Test fun `a queued capture closes onto 圖鑑's 我做的, with back still leading home`() {
        val fromToday = TabShell.afterCaptureQueued(NavStack().push(ManageRoute.Capture()))
        assertEquals(listOf(AppRoute.Today, AppRoute.Atlas), fromToday.nav.entries)
        assertEquals(CardsSource.Mine, fromToday.cardsSource)
        // Opened from 圖鑑 itself, it unwinds to it rather than stacking a second one.
        val fromAtlas = TabShell.afterCaptureQueued(NavStack().select(AppRoute.Atlas).push(ManageRoute.Capture()))
        assertEquals(listOf(AppRoute.Today, AppRoute.Atlas), fromAtlas.nav.entries)
        // From 我, 圖鑑 is pushed over it so back leads out the way they came in.
        val fromMe = TabShell.afterCaptureQueued(NavStack().select(AppRoute.Me).push(ManageRoute.Capture()))
        assertEquals(listOf(AppRoute.Today, AppRoute.Me, AppRoute.Atlas), fromMe.nav.entries)
    }

    @Test fun `拍照's 圖鑑管理 link opens it over 圖鑑`() {
        val nav = TabShell.captureToManage(NavStack().push(ManageRoute.Capture()))
        assertEquals(listOf(AppRoute.Today, AppRoute.Atlas, ManageRoute.Home), nav.entries)
    }

    // ── Which page a card opens ──

    @Test fun `a taken-in card opens its public page, anything else the dictionary's`() {
        assertEquals(CommunityRoute.Item("atlas-abc"), ShellContext.cardRoute("saved:atlas-abc"))
        assertEquals(AtlasRoute.Word("kettle"), ShellContext.cardRoute("kettle"))
        assertEquals(AtlasRoute.Word("atlas:item-1"), ShellContext.cardRoute("atlas:item-1"))
    }
}
