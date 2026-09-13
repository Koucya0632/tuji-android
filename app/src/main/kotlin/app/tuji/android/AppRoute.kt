package app.tuji.android

/**
 * Where the signed-in shell is.
 *
 * A hand-rolled stack rather than Navigation-Compose. What this app needs from
 * navigation is a list it can push onto and pop off, and the one place it is
 * genuinely stack-shaped — 圖鑑 → 分類 → 詞 → 相關的詞 → 詞 — is stack-shaped in
 * exactly the way a `List` is. A navigation library would add a second place
 * for state to live and a string DSL to keep in step with these types.
 */
sealed interface AppRoute {

    /** The four tabs. Popping to one of these clears the stack above it. */
    sealed interface Tab : AppRoute

    data object Today : Tab
    data object Atlas : Tab
    data object Community : Tab
    data object Me : Tab

    /**
     * 搜尋, opened from the magnifier on 今天 and 圖鑑.
     *
     * Not a tab, as on iOS: it is something you do from where you are, and a
     * fifth slot for it pushed 我 off the thumb's reach and gave the bar a
     * text-only place to stand that none of the other four needed.
     */
    data object Search : AppRoute

    /** 設定, reached from the gear on 我的. */
    data object Settings : AppRoute

    /** The index of every theme, reached from 圖鑑's count row. */
    data object Themes : AppRoute

    /** One theme's page: its hero, its description, its words. */
    data class Shelf(val categoryId: String, val title: String) : AppRoute

    /** One catalogue entry. */
    data class Word(val wordId: String) : AppRoute

    /** One published 物見 word. */
    data class PublicItem(val slug: String) : AppRoute

    /** One author's public shelf. */
    data class Author(val handle: String) : AppRoute

    /** One published 合集. */
    data class Collection(val slug: String) : AppRoute

    /** 自製圖鑑：拍照 → 辨識 → 確認. */
    data object Capture : AppRoute

    /** The study flows, which take over the whole screen. */
    data object Review : AppRoute
    data object LearnNew : AppRoute
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
     * - A **focused** screen owns the window: the two study flows, a word's
     *   page, 搜尋 and 拍照. A session that can be left by tapping a tab is a
     *   session that gets left by accident.
     * - Anything opened from **物見 or 我** owns it too: both tabs are hubs of
     *   entry points, and a screen opened from one is a window you are handed.
     * - 今天 and 圖鑑 keep the bar through a push, because a theme is a place
     *   you come back from.
     */
    fun tabBarVisible(nav: NavStack): Boolean {
        val current = nav.current
        if (current in focused || current is AppRoute.Word) return false
        if (current is AppRoute.Tab) return true
        return when (nav.tab) {
            AppRoute.Community, AppRoute.Me -> false
            else -> true
        }
    }

    private val focused: Set<AppRoute> = setOf(
        AppRoute.Review,
        AppRoute.LearnNew,
        AppRoute.Search,
        AppRoute.Capture,
    )
}
