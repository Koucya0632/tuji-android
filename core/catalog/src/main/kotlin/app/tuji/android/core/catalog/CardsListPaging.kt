package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word

/**
 * The 圖鑑 grid's page window.
 *
 * A value rather than a view model: there is nothing async here, and keeping it
 * a pure derivation means a test can hand it words and get the answer. The
 * "is there another page" rule in particular is one nobody can see is wrong —
 * a 顯示更多 button that stays after the last page, or vanishes one page early,
 * both look like an ordinary list.
 */
object CardsListPaging {

    /**
     * One screenful. Deliberately large: the grid is dense, and a small page
     * makes 顯示更多 feel like the list is fighting back.
     */
    const val PAGE_SIZE = 60

    data class Page(
        /** What the grid renders. */
        val words: List<Word>,
        /** Whether 顯示更多 has anything left to reveal. */
        val canShowMore: Boolean,
        /** How many words there are in total, before the window. */
        val matchCount: Int,
    )

    fun page(words: List<Word>, visibleCount: Int): Page = Page(
        words = words.take(visibleCount.coerceAtLeast(0)),
        canShowMore = visibleCount < words.size,
        matchCount = words.size,
    )
}
