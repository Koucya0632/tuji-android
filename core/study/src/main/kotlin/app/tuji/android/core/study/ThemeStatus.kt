package app.tuji.android.core.study

/**
 * Whether a theme is finished, and in which sense.
 *
 * Two different claims, and they are not the same one twice:
 *
 * - **全精通** — every word in the theme sits at [MasteryLevel.Expert]. An
 *   unstudied word reads as 未學, so all-精通 also means all-seen; it is the
 *   stronger statement and therefore wins.
 * - **完成** — the server says every published card in the theme has been
 *   studied at least once, even if some have since decayed below 精通.
 *
 * A pure function over both inputs rather than a method on a tile, so the
 * precedence between them is something a test can state. Empty inputs give
 * [None], which is also how a guest — who has neither — renders.
 */
enum class ThemeStatus {
    None,
    Completed,
    Mastered,
    ;

    companion object {

        /**
         * @param wordIds every word in the theme.
         * @param masteryScore 0–100 for a word, or null if never studied.
         * @param seenAndTotal the server's per-theme (seen, total) row, or null
         *   when that readout has not been fetched — which is Android's state
         *   today: `/api/users/progress` is not wired, so [Completed] is
         *   currently unreachable from the app even though the rule is whole.
         *   Passing null is the same shape iOS uses for a guest.
         */
        fun of(
            wordIds: List<String>,
            masteryScore: (String) -> Int?,
            seenAndTotal: Pair<Int, Int>?,
        ): ThemeStatus {
            if (wordIds.isEmpty()) return None
            val allMastered = wordIds.all { MasteryLevel.of(masteryScore(it)) == MasteryLevel.Expert }
            if (allMastered) return Mastered
            val (seen, total) = seenAndTotal ?: return None
            return if (total > 0 && seen >= total) Completed else None
        }
    }
}
