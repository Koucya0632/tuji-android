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

    /** The three tabs. Popping to one of these clears the stack above it. */
    sealed interface Tab : AppRoute

    data object Today : Tab
    data object Atlas : Tab
    data object Community : Tab
    data object Search : Tab
    data object Me : Tab

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
