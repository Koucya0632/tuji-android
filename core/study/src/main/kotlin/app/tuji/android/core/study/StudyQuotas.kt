package app.tuji.android.core.study

/**
 * Daily-quota math for the study flow. Mirrors `lib/scheduling.ts` on the
 * backend: the new-card quota tapers off as the review backlog grows, so users
 * dig out of due cards before piling on more new ones.
 *
 * Shared between 今日 (button disable state) and the launcher (queue sizing) so
 * both surfaces stay in sync — on iOS these had drifted apart once already.
 */
object StudyQuotas {
    /** What 學新字 asks the queue for: iOS's `StudyQueueStore.params(for: .new)`. */
    data class NewQueue(val limit: Int, val categories: List<String>)

    /**
     * The new-word request for today.
     *
     * The size is the daily goal tapered by the review backlog — the same
     * number 今日 prints in its hint, so the screen and the session cannot
     * disagree — and the themes are the selection **as picked**: 自定義 cards
     * join only when 自定義 is ticked, and an empty selection is sent empty,
     * which the server reads as everything.
     *
     * Android used to ask for a fixed 5 with no themes, so a user whose 設定
     * said 10 got half, from topics they had not picked.
     */
    fun newQueue(goal: Int, due: Int, categories: List<String>): NewQueue =
        NewQueue(limit = computeNewLimit(goal, due), categories = categories)

    fun computeNewLimit(goal: Int, due: Int): Int = when {
        due <= 20 -> goal
        due <= 50 -> (goal * 0.75).toInt()
        due <= 100 -> (goal * 0.5).toInt()
        else -> 0
    }
}
