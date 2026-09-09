package app.tuji.android.core.study

/**
 * How this account's studied words are spread across the tiers.
 *
 * 我的 could say how *wide* the account had gone — 完成度 and the 明細 rows are
 * both seen/total — and nothing said how *deep*. Pushing a word from 30 to 85
 * moved no number on that screen, and 精通, the tier this system calls
 * prestigious, was never counted at all. This is that count.
 *
 * A pure function over the score map, like [ThemeStatus.of], so the arithmetic
 * is reachable from a test rather than trapped in a composable.
 */
data class MasteryDistribution(
    val know: Int = 0,
    val familiar: Int = 0,
    val proficient: Int = 0,
    val expert: Int = 0,
) {
    /** Studied words — every tier summed. **Not** the dictionary's size. */
    val total: Int get() = know + familiar + proficient + expert

    val isEmpty: Boolean get() = total == 0

    /**
     * One tier and its count, in ladder order — the order *is* the meaning, so
     * this is a list and not a map.
     */
    val segments: List<Segment>
        get() = listOf(
            Segment(MasteryLevel.Know, know),
            Segment(MasteryLevel.Familiar, familiar),
            Segment(MasteryLevel.Proficient, proficient),
            Segment(MasteryLevel.Expert, expert),
        )

    /** `words`, not `count`: this is a tally of words, not a collection. */
    data class Segment(val level: MasteryLevel, val words: Int)

    companion object {
        val empty = MasteryDistribution()

        /**
         * Count the scores into tiers.
         *
         * **未學 is deliberately absent.** A word never studied has no row and
         * so no entry here, so counting it would need a denominator — and the
         * only honest denominator is the scoped seen/total 完成度 already owns.
         * Minting a second one here is how a percentage describing a selection
         * nobody made comes back under a new name. Width is 完成度's question;
         * this answers depth, over the words that have an answer at all.
         *
         * The key *shape* is irrelevant: bare dictionary ids, `atlas:<itemId>`
         * for 自製圖鑑 and `saved:<slug>` for 物見 are all words this account
         * studies. The map is already scoped to the current 學習方向 by the
         * `learning` query the store sends, so there is nothing left to filter.
         */
        fun of(scores: Map<String, Int>): MasteryDistribution {
            var know = 0
            var familiar = 0
            var proficient = 0
            var expert = 0
            for (score in scores.values) {
                // Through MasteryLevel, not a copy of its thresholds: a 0 is
                // 未學, and the one place that rule lives stays the one place.
                when (MasteryLevel.of(score)) {
                    MasteryLevel.Know -> know++
                    MasteryLevel.Familiar -> familiar++
                    MasteryLevel.Proficient -> proficient++
                    MasteryLevel.Expert -> expert++
                    MasteryLevel.NotLearned -> Unit
                }
            }
            return MasteryDistribution(know, familiar, proficient, expert)
        }
    }
}
