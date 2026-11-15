package app.tuji.android

import app.tuji.android.assistant.TopicChatEntry
import app.tuji.android.core.catalog.CardsSource
import app.tuji.android.core.model.StudyMode
import app.tuji.android.study.StudySource

/**
 * Where the signed-in shell is.
 *
 * A hand-rolled stack rather than Navigation-Compose. What this app needs from
 * navigation is a list it can push onto and pop off, and the one place it is
 * genuinely stack-shaped — 圖鑑 → 分類 → 詞 → 相關的詞 → 詞 — is stack-shaped in
 * exactly the way a `List` is. A navigation library would add a second place
 * for state to live and a string DSL to keep in step with these types.
 *
 * Two kinds: the four [Tab]s, which live in the pager, and a [Page] pushed over
 * them. Pages come in families, one per feature, and each family is drawn by
 * that feature's host — so the shell's `when` names families, not screens, and
 * a page nobody draws does not compile. (Kotlin keeps a sealed type's direct
 * subtypes in its package, which is why the families are declared here rather
 * than beside their hosts.)
 */
sealed interface AppRoute {

    /** The four tabs. Popping to one of these clears the stack above it. */
    sealed interface Tab : AppRoute

    data object Today : Tab
    data object Atlas : Tab
    data object Community : Tab
    data object Me : Tab

    /** Anything pushed over the tabs. */
    sealed interface Page : AppRoute
}

/** 圖鑑: the dictionary, a theme, a word, and 搜尋. Drawn by `atlas.AtlasHost`. */
sealed interface AtlasRoute : AppRoute.Page {

    /**
     * 搜尋, opened from the magnifier on 今天 and 圖鑑.
     *
     * Not a tab, as on iOS: it is something you do from where you are, and a
     * fifth slot for it pushed 我 off the thumb's reach and gave the bar a
     * text-only place to stand that none of the other four needed.
     */
    data object Search : AtlasRoute

    /** 搜尋 opened on a query — AI 查詞's 「圖鑑裡有 N 個相關詞」. */
    data class SearchFor(val query: String) : AtlasRoute

    /** One theme's page: its hero, its description, its words. */
    data class Shelf(val categoryId: String, val title: String) : AtlasRoute

    /**
     * One catalogue entry. `fromScene` is the 場面 it was opened from, which
     * its 場面 section leaves out so it does not lead straight back.
     */
    data class Word(val wordId: String, val fromScene: String? = null) : AtlasRoute
}

/** 自製圖鑑: 拍照, 圖鑑管理 and the account's own 合集. Drawn by `manage.ManageHost`. */
sealed interface ManageRoute : AppRoute.Page {

    /** 圖鑑管理, reached from 圖鑑's 我做的. */
    data object Home : ManageRoute

    /** One of the account's own photos in 圖鑑管理. */
    data class Card(val imageId: String) : ManageRoute

    /** 編輯合集, from 圖鑑管理's 合集. */
    data class CollectionEdit(val collectionId: String) : ManageRoute

    /** 拍照 → 辨識 → 確認. [resume] is the photo 圖片詳情's 繼續確認 reopens; null from the camera button. */
    data class Capture(val resume: String? = null) : ManageRoute
}

/** 物見: other people's cards, pages and 合集. Drawn by `community.CommunityHost`. */
sealed interface CommunityRoute : AppRoute.Page {

    /** One published 物見 word. */
    data class Item(val slug: String) : CommunityRoute

    /** One author's public shelf. */
    data class Author(val handle: String) : CommunityRoute

    /** One published 合集. */
    data class Collection(val slug: String) : CommunityRoute

    /** 物見搜尋, from the magnifier on 物見 — 合集 and words in the language being learned. */
    data object Search : CommunityRoute
}

/** 跟著 Moira 去旅遊. Drawn by `travel.TravelHost`. */
sealed interface TravelRoute : AppRoute.Page {

    /** One destination's map, from the band on 物見's 探索. */
    data class Destination(val destinationId: String) : TravelRoute

    /** One city's map and its 景點. */
    data class City(val cityId: String, val destinationId: String) : TravelRoute

    /** One 景點: its progress, a way to study it, and its words. */
    data class Spot(val spotId: String) : TravelRoute
}

/** 場面練習. Drawn by `scenes.SceneHost`. */
sealed interface SceneRoute : AppRoute.Page {

    /** The list, from 今天's scene card. */
    data object All : SceneRoute

    /** One 場面's page, which is also 進店前看一眼. */
    data class Detail(val sceneId: String) : SceneRoute

    /** Practising one 場面; `routeId` picks an optional trip. Full screen, like a study session. */
    data class Practice(val sceneId: String, val routeId: String? = null) : SceneRoute
}

/** 個人詞表. Drawn by `wordlists.WordListHost`. */
sealed interface WordListRoute : AppRoute.Page {

    /** Every list, from 我. */
    data object All : WordListRoute

    /** One list. */
    data class Detail(val listId: String) : WordListRoute
}

/**
 * 學新字 or 複習, which take over the whole screen. [source] is where the
 * cards come from — 今日's themes, a 個人詞表 or a 景點 — and decides
 * everything about the session that is not the flow itself. Drawn by
 * `study.StudySessionHost`.
 */
data class StudyRoute(
    val mode: StudyMode,
    val source: StudySource = StudySource.Themes,
) : AppRoute.Page

/** Moira's pages, reached from 今天. Drawn by `assistant.AssistantHost`. */
sealed interface AssistantRoute : AppRoute.Page {

    /** 點數明細, from the can on 今天 and from AI 查詞. */
    data object CreditStatement : AssistantRoute

    /** AI 查詞. */
    data object Vocabulary : AssistantRoute

    /** AI 語法. */
    data object Grammar : AssistantRoute

    /** 情境故事 — pick 圖鑑 items, a place and a difficulty; Moira writes a short dialogue around them. */
    data object Story : AssistantRoute

    /** 圖鑑主題對話 — from 今天, a theme page's 「用這個主題聊聊」, or 對話紀錄. */
    data class TopicChat(val entry: TopicChatEntry) : AssistantRoute

    /** 對話紀錄 — saved 主題對話 in the language being learned. */
    data object TopicChatHistory : AssistantRoute
}

/** 我, 設定 and the plan. Drawn by `account.AccountHost`. */
sealed interface AccountRoute : AppRoute.Page {

    /** 設定, reached from the gear on 我的. */
    data object Settings : AccountRoute

    /** 編輯個人資料, reached from 設定. */
    data object EditProfile : AccountRoute

    /** 已封鎖的人, reached from 設定 — the one place a block is undone without finding the person. */
    data object BlockedAuthors : AccountRoute

    /** 學習主題, reached from 設定 and from 今日's theme strip. */
    data object StudyThemes : AccountRoute

    /** 會員方案 — where every lock and every 402 leads. */
    data object Membership : AccountRoute

    /** 學習統計 — 我's numbers, from the trend icon on 今日's ink block. */
    data object ProgressStats : AccountRoute
}

/**
 * The back stack.
 *
 * Immutable, so a recomposition cannot half-apply a navigation: every move
 * returns the next stack.
 */
data class NavStack(val entries: List<AppRoute> = listOf(AppRoute.Today)) {

    val current: AppRoute get() = entries.last()

    /** Which tab the bar should mark, whatever is stacked above it. */
    val tab: AppRoute.Tab? get() = entries.filterIsInstance<AppRoute.Tab>().lastOrNull()

    /** True when there is somewhere to go back to. */
    val canGoBack: Boolean get() = entries.size > 1

    fun push(route: AppRoute): NavStack = copy(entries = entries + route)

    fun pop(): NavStack = if (canGoBack) copy(entries = entries.dropLast(1)) else this

    /**
     * Switch tabs.
     *
     * A tab already in the stack is *unwound to*, not pushed again: the gesture
     * means "take me to the top of this", which is what every tab bar on both
     * platforms does, and pushing a second copy would make 返回 walk through
     * two 圖鑑s.
     *
     * A tab that is not in the stack is pushed rather than replacing it, so
     * back still leads out the way the user came in.
     */
    fun select(tab: AppRoute.Tab): NavStack {
        val at = entries.indexOfLast { it == tab }
        return if (at >= 0) copy(entries = entries.take(at + 1)) else push(tab)
    }

    /**
     * [change], applied only while [page] is the one on top; otherwise this
     * stack, unchanged.
     *
     * Every page's moves go through here. A page is still composed while it
     * slides out, and a network answer can land after it has gone — a 返回
     * from either would pop whatever is on top now, which is somebody else's
     * page. The shell wrote this guard by hand eighteen times, and the other
     * thirty-odd moves had none.
     */
    fun from(page: AppRoute, change: NavStack.() -> NavStack): NavStack =
        if (current == page) change() else this
}

/**
 * The tab shell's decisions, as pure functions over the stack.
 *
 * iOS keeps these in `TabShellDecisions` for the same reason: which screens
 * get the bar is a policy, and a policy written inline in a 700-line
 * composable is one nobody can test.
 */
object TabShell {

    /** The bar's order. 時間 → 內容 → 他人 → 自己. */
    val tabs: List<AppRoute.Tab> = listOf(AppRoute.Today, AppRoute.Atlas, AppRoute.Community, AppRoute.Me)

    /** 拍照 sits after this tab, which puts it in the middle of the bar. */
    val captureFollows: AppRoute.Tab = AppRoute.Atlas

    /**
     * Whether the tab bar is drawn.
     *
     * - A page that **owns the window** never has it: see [ownsWindow].
     * - Anything opened from **物見 or 我** owns it too: both tabs are hubs of
     *   entry points, and a screen opened from one is a window you are handed.
     * - 今天 and 圖鑑 keep the bar through a push, because a theme is a place
     *   you come back from.
     */
    fun tabBarVisible(nav: NavStack): Boolean = when (val current = nav.current) {
        is AppRoute.Tab -> true
        is AppRoute.Page -> !ownsWindow(current) && when (nav.tab) {
            AppRoute.Community, AppRoute.Me -> false
            else -> true
        }
    }

    /**
     * Whether a page hides the bar wherever it was opened from.
     *
     * Every page is named, with no `else`, so a new one cannot get the bar by
     * omission: a study session that can be left by tapping a tab is a session
     * that gets left by accident.
     */
    fun ownsWindow(page: AppRoute.Page): Boolean = when (page) {
        // Focused: the study flows, a word's page, 搜尋 and 拍照.
        is StudyRoute -> true
        AtlasRoute.Search, is AtlasRoute.SearchFor, is AtlasRoute.Word -> true
        is AtlasRoute.Shelf -> false
        is ManageRoute.Capture -> true
        ManageRoute.Home, is ManageRoute.Card, is ManageRoute.CollectionEdit -> false
        CommunityRoute.Search -> true
        is CommunityRoute.Item, is CommunityRoute.Author, is CommunityRoute.Collection -> false
        is TravelRoute.Destination, is TravelRoute.City, is TravelRoute.Spot -> false
        // Practising a 場面 is a session, like 學新字.
        is SceneRoute.Practice -> true
        SceneRoute.All, is SceneRoute.Detail -> false
        WordListRoute.All, is WordListRoute.Detail -> false
        // Moira's pages are places you study in, as on iOS (`studyFocus.enter()`).
        AssistantRoute.CreditStatement, AssistantRoute.Vocabulary, AssistantRoute.Grammar,
        AssistantRoute.Story, is AssistantRoute.TopicChat, AssistantRoute.TopicChatHistory -> true
        // A page read to the end — its last lines are the ones a bar would cover.
        AccountRoute.Membership -> true
        AccountRoute.Settings, AccountRoute.EditProfile, AccountRoute.BlockedAuthors,
        AccountRoute.StudyThemes, AccountRoute.ProgressStats -> false
    }

    /**
     * Whether the four tab roots may be swiped between.
     *
     * Structurally this can only be true where the pager exists, which is the
     * point: it is written down so the next person who hosts the pager
     * somewhere else has to read the rule rather than rediscover it. iOS words
     * it as `!studyFocusActive && currentTabAtRoot` — a pushed screen disables
     * the swipe on *every* tab, because the race it causes is with the
     * platform's own back gesture, and that exists everywhere.
     */
    fun swipeEnabled(nav: NavStack): Boolean = nav.current is AppRoute.Tab

    /**
     * Where 拍照 closes once 生成佇列 has the card: 圖鑑, opened on 我做的 where
     * its 生成中 tile is. Back still leads home: 圖鑑 is unwound to if it is
     * under the capture page, not stacked a second time.
     */
    fun afterCaptureQueued(nav: NavStack): Landing = Landing(nav.pop().select(AppRoute.Atlas), CardsSource.Mine)

    /** 拍照's 圖鑑管理 link: the capture page goes, and 圖鑑管理 opens over 圖鑑. */
    fun captureToManage(nav: NavStack): NavStack = nav.pop().select(AppRoute.Atlas).push(ManageRoute.Home)

    /** A stack, and the 圖鑑 shelf it should open on. */
    data class Landing(val nav: NavStack, val cardsSource: CardsSource?)
}
