package app.tuji.android.core.study

import app.tuji.android.core.model.CategoryProgress
import app.tuji.android.core.model.Word

/** What the 完成度 denominator is describing — the label above the number changes with it. */
enum class CompletionScope {
    /** The user's picked 學習主題: 「所選主題完成度」. */
    SelectedThemes,

    /** No selection, so the whole dictionary stands in: 「圖鑑完成度」. */
    WholeDictionary,

    /**
     * Nothing to describe yet: signed in, settings loaded, no themes picked.
     * Reads 0 / 0 rather than inventing a denominator.
     */
    Pending,
}

/**
 * 完成度 — how far through the selected themes the account is, as one value.
 *
 * iOS's `CompletionReadout`, and for the reason iOS built it: 今日's 主題進度
 * and 我's completion card are the same question, and while each screen
 * answered it for itself they disagreed. Android's two copies both read the
 * study-stats `seen / total`, which counts the whole dictionary whatever the
 * user picked — 「33 / 593」 on a phone whose iPhone said 「33 / 560」.
 */
data class CompletionReadout(val inputs: Inputs) {

    /** Every fact the rule depends on. Assembled by the screen; nothing in here is fetched. */
    data class Inputs(
        val isGuest: Boolean,
        /** An empty theme list means nothing until settings have actually arrived. */
        val settingsLoaded: Boolean,
        val studyCategories: List<String>,
        /** Guests have no SRS state; their progress is the local learned set. */
        val guestLearnedCount: Int = 0,
        /** The server's per-theme `seen`, summed over the selection. */
        val seenInSelection: Int,
        /** The server's per-theme `total`, summed over the selection. */
        val totalInSelection: Int,
        /** Whole local dictionary — the denominator when no themes are picked. */
        val dictionaryCount: Int,
        /** Local dictionary scoped to the selection. */
        val dictionaryCountInSelection: Int,
    ) {
        companion object {
            /**
             * Read the facts out of what the app holds.
             *
             * **The selection is the filter for all four scoped numbers**, read
             * once. iOS's version of this bug was a denominator that stopped
             * being scoped in one of the places that assembled these by hand;
             * with one assembly there is one place for it to be right.
             *
             * An empty selection sums **every** server row — iOS's
             * `ProgressStore.seenCount(filter: [])` — and counts no local words;
             * the rule above decides what either means.
             *
             * @param words every word the selection can reach: the catalogue
             *   plus the user's own and taken-in cards.
             */
            fun from(
                isGuest: Boolean,
                settingsLoaded: Boolean,
                studyCategories: List<String>,
                progress: List<CategoryProgress>,
                words: List<Word>,
            ): Inputs {
                val picked = studyCategories.toSet()
                val rows = if (picked.isEmpty()) progress else progress.filter { it.category in picked }
                return Inputs(
                    isGuest = isGuest,
                    settingsLoaded = settingsLoaded,
                    studyCategories = studyCategories,
                    seenInSelection = rows.sumOf { it.seen },
                    totalInSelection = rows.sumOf { it.total },
                    dictionaryCount = words.size,
                    dictionaryCountInSelection = words.count { it.category in picked },
                )
            }
        }
    }

    /**
     * Signed in, settings have arrived, and no themes are picked. 今日 branches
     * on this to show its 選擇主題 prompt; 我 to avoid labelling an
     * all-category number as a scoped one.
     */
    val showsThemePrompt: Boolean
        get() = !inputs.isGuest && inputs.settingsLoaded && inputs.studyCategories.isEmpty()

    val scope: CompletionScope
        get() = when {
            showsThemePrompt -> CompletionScope.Pending
            inputs.studyCategories.isEmpty() -> CompletionScope.WholeDictionary
            else -> CompletionScope.SelectedThemes
        }

    /** Words studied at least once. With no themes picked it reads 0, matching the prompt. */
    val seen: Int
        get() = when {
            inputs.isGuest -> inputs.guestLearnedCount
            showsThemePrompt -> 0
            else -> inputs.seenInSelection
        }

    /**
     * Published words in the selection: the server's count when it has one,
     * else the local dictionary — **scoped the same way**. The fallback fires
     * for guests, always, but also whenever the picked themes hold nothing on
     * the server (自定義 and 物見), and an unscoped fallback there prints a
     * denominator describing a selection nobody made.
     */
    val total: Int
        get() = when {
            showsThemePrompt -> 0
            inputs.totalInSelection > 0 -> inputs.totalInSelection
            inputs.studyCategories.isEmpty() -> inputs.dictionaryCount
            else -> inputs.dictionaryCountInSelection
        }

    /** 0…1, clamped. */
    val ratio: Double get() = ratio(seen, total)

    /** The whole-number percentage, derived from [ratio] so it cannot disagree with the bar. */
    val percent: Int get() = Math.round(ratio * 100).toInt()

    companion object {
        /**
         * The one seen/total ratio in the app, clamped and zero-safe.
         *
         * `seen` counts words studied; `total` counts *published* cards. A
         * withdrawn word leaves seen > total, and an unclamped ratio prints
         * 「103%」 beside a bar pinned at full.
         */
        fun ratio(seen: Int, total: Int): Double =
            if (total <= 0) 0.0 else (seen.toDouble() / total).coerceIn(0.0, 1.0)
    }
}
