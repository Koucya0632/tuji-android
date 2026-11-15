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
 * Two causes here rather than iOS's four. 清除學習進度 is a write, so it is an
 * [AppMutation]; 換介面語言 retunes every store through the shell's language
 * effect, and a row for it here would be a second, quieter copy of that rule.
 */
sealed interface LearningRefreshCause {
    /** 今日 pulled down. */
    data object PulledToday : LearningRefreshCause

    /** 我 pulled down. */
    data object PulledMe : LearningRefreshCause
}

/**
 * A store a refresh reaches, named by what it holds rather than by its class.
 *
 * Shared by the two things that refresh: a pull ([LearningRefreshCause]) and a
 * write somebody made ([AppMutation]). The shell answers each target with one
 * store, in one `when`, so a target with no store behind it does not compile.
 *
 * iOS has a fifth learning target, `queue`, which it *drops* rather than
 * re-reads. Android has no queue cache to drop — every 複習 and 學新字 asks the
 * server when it starts — so a target for it here would be a word with
 * nothing behind it.
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

    /** 圖鑑's 我做的 and 已收進 — the account's own and taken-in cards, as words. */
    PersonalShelves,

    /** The plan and its quotas: 自製圖鑑's slots, what is locked, what is studyable. */
    Entitlement,

    /** 罐頭點數's balance, as 助手 keeps it. */
    CreditBalance,

    /** 我's own payload: the account row, the weakest words. */
    Account,

    /** 物見's public wall — the collections everyone reads. */
    CommunityFeed,

    /** 物見's 已收藏 shelf. */
    CommunitySaved,

    /** The row at the top of 物見: this account's own public page. */
    CommunityMe,

    /** 圖鑑管理's 合集 — the author's own collections, drafts included. */
    MyCollections,
}

/** The whole policy, in one place. */
val LearningRefreshCause.targets: Set<RefreshTarget>
    get() = when (this) {
        LearningRefreshCause.PulledToday -> setOf(
            RefreshTarget.Progress,
            RefreshTarget.Stats,
            RefreshTarget.Mastery,
            RefreshTarget.Catalogue,
        )

        LearningRefreshCause.PulledMe ->
            // No stats: nothing on 我 reads 待複習 or 今天學了幾個. Mastery,
            // because 我 · 熟練度 is a whole section of that page — the exact
            // line iOS had wrong.
            setOf(RefreshTarget.Progress, RefreshTarget.Mastery)
    }
