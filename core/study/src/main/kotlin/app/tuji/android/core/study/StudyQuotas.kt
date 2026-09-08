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
    fun computeNewLimit(goal: Int, due: Int): Int = when {
        due <= 20 -> goal
        due <= 50 -> (goal * 0.75).toInt()
        due <= 100 -> (goal * 0.5).toInt()
        else -> 0
    }
}
