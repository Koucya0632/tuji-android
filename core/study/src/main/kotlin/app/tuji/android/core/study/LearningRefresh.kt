package app.tuji.android.core.study

/**
 * What a pull-to-refresh re-reads.
 *
 * Ported from iOS's `LearningRefresh`, which exists because four call sites
 * each wrote their own list and two of them had drifted: 我 re-read progress
 * and not mastery, while the 熟練度 bar it draws comes from mastery. A list
 * written at the call site is a list that goes stale the next time a screen
 * gains a number.
 *
 * Two causes here rather than iOS's four: 清除學習進度 and 換介面語言 reload
 * through their own paths on Android, and adding them to this table without
 * moving those call sites would be a second, quieter copy of the same rule.
 */
sealed interface LearningRefreshCause {
    /** Whether the reader has no account, and so no account-scoped anything. */
    val isGuest: Boolean

    /** 今日 pulled down. */
    data class PulledToday(override val isGuest: Boolean) : LearningRefreshCause

    /** 我 pulled down. */
    data class PulledMe(override val isGuest: Boolean) : LearningRefreshCause
}

/**
 * A store a refresh reaches, named by what it holds rather than by its class.
 *
 * iOS has a fifth, `queue`, which it *drops* rather than re-reads. Android has
 * no queue cache to drop — every 複習 and 學新字 asks the server when it starts
 * — so a target for it here would be a word with nothing behind it.
 */
enum class RefreshTarget {
    /** 已學 / 總數, the heatmap, the streak. */
    Progress,

    /** 待複習 and 今天學了幾個 — 今日's numbers, and nothing else reads them. */
    Stats,

    /** Every 熟練度 score. */
    Mastery,

    /** The dictionary and the themes: one read on this side, two stores on iOS. */
    Catalogue,
}

/**
 * The whole policy, in one place.
 *
 * A guest has no account-scoped stores, so 我 refreshes **nothing** — the page
 * has nothing on it that came from a server. 今日 still re-reads the catalogue,
 * because the word list and themes are public and a guest reads them too.
 */
val LearningRefreshCause.targets: Set<RefreshTarget>
    get() = when (this) {
        is LearningRefreshCause.PulledToday ->
            if (isGuest) {
                setOf(RefreshTarget.Catalogue)
            } else {
                setOf(
                    RefreshTarget.Progress,
                    RefreshTarget.Stats,
                    RefreshTarget.Mastery,
                    RefreshTarget.Catalogue,
                )
            }

        is LearningRefreshCause.PulledMe ->
            // No stats: nothing on 我 reads 待複習 or 今天學了幾個. Mastery,
            // because 我 · 熟練度 is a whole section of that page — the exact
            // line iOS had wrong.
            if (isGuest) emptySet() else setOf(RefreshTarget.Progress, RefreshTarget.Mastery)
    }
