package app.tuji.android.core.study

/**
 * The five-tier mastery ladder, derived purely from the 0–100 score the server
 * keeps in `user_words.mastery`.
 *
 * The backend's `lib/mastery.ts` has its own **four**-level scheme for the web.
 * This is the app's own five-level display, and the answer endpoint's `level`
 * object is deliberately ignored: the tier a user sees is a client decision, so
 * it is re-derived here rather than trusted from a payload that answers a
 * different question.
 *
 * Thresholds are progressive rather than even. One correct answer lands the EMA
 * around 21–30, so 精通 takes many reviews to reach and stays worth something:
 *
 * | 分數 | 級別 |
 * |---|---|
 * | 無紀錄 / 0 | 未學 |
 * | 1–34 | 知道 |
 * | 35–59 | 熟悉 |
 * | 60–79 | 熟練 |
 * | 80–100 | 精通 |
 *
 * The decision lives here and `core:design` only colours it — the same split
 * [StudyOptionState] has, and for the same reason: a rule inside a composable
 * is a rule no test can reach.
 */
enum class MasteryLevel {
    NotLearned,
    Know,
    Familiar,
    Proficient,
    Expert,
    ;

    /**
     * How many of the five scale segments are filled.
     *
     * 精通 fills all five, skipping four: the badge is read as an amount, and a
     * top tier that leaves one segment empty says "nearly" about the tier that
     * means "done".
     */
    val filledSegments: Int
        get() = when (this) {
            NotLearned -> 0
            Know -> 1
            Familiar -> 2
            Proficient -> 3
            Expert -> 5
        }

    companion object {
        /** Null (no `user_words` row) or 0 both mean 未學. */
        fun of(score: Int?): MasteryLevel {
            val s = score ?: return NotLearned
            return when {
                s <= 0 -> NotLearned
                s >= 80 -> Expert
                s >= 60 -> Proficient
                s >= 35 -> Familiar
                else -> Know
            }
        }
    }
}
