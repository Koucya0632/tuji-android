package app.tuji.android.core.catalog

/**
 * 最近搜尋 — the list's one rule, kept where a test can reach it.
 *
 * iOS's `LocalCache.pushRecentSearch`: newest first, no repeats, ten at most.
 * Only a query that found something is ever pushed (the caller decides that),
 * because a recent-search row that reliably returns nothing is worse than no
 * row at all.
 */
object RecentSearches {

    const val MAX = 10

    fun push(list: List<String>, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return list
        return (listOf(trimmed) + list.filter { it != trimmed }).take(MAX)
    }
}
