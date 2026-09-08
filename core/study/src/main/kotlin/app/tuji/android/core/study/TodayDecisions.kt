package app.tuji.android.core.study

import app.tuji.android.core.model.StudyStats

/**
 * What 今日 says and offers, derived from the day's state.
 *
 * Enums, not strings: the screen owns the words, this owns the verdict. That
 * split is what keeps it testable without a resource bundle, and it is also
 * what stops a hard-coded Chinese line leaking into a ja/en UI from here —
 * there is no string to leak.
 *
 * **A deliberate subset of iOS's.** iOS also reasons about 主題 selection and a
 * per-category completion readout; Android has neither yet, so the cases that
 * depend on them (`pickThemes`, `noThemes`) are absent rather than stubbed. A
 * case that can never be returned is worse than a missing one: it looks handled.
 */
data class TodayInputs(
    val isGuest: Boolean = false,
    /** Null until the first fetch lands. */
    val stats: StudyStats? = null,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
) {
    companion object {
        /** iOS's `UserSettings` default; the settings screen that changes it is M3. */
        const val DEFAULT_DAILY_GOAL = 10
    }
}

/**
 * Which line sits under the greeting. One case per thing 今日 can truthfully
 * say, in the order the rules resolve.
 */
enum class TodaySubtitle {
    GuestBrowsing,

    /**
     * Stats have not arrived. A neutral line beats a wrong verdict —
     * 「都學過了」 flashing on a brand-new account while the first fetch is in
     * flight is worse than saying nothing specific.
     */
    Unknown,
    ReviewDue,
    GoalReached,
    NewDoneToday,
    NewAvailable,
    AllLearned,
}

/** Why 學新字 is unavailable, so the screen can explain the dead end. */
enum class TodayNewBlock {
    None,

    /** Nothing left to learn in the catalogue. */
    AllLearned,

    /** The review backlog has eaten the new-word quota. */
    ReviewBacklog,
}

/** Which caption the hero shows under its buttons, when it shows one. */
enum class TodayHeroHint {
    NewBlocked,
    QuotaAdjusted,
    NothingToReview,
}

class TodayDecisions(private val inputs: TodayInputs) {

    private val goal: Int get() = maxOf(1, inputs.dailyGoal)

    val goalReached: Boolean
        get() = !inputs.isGuest && (inputs.stats?.todayNew ?: 0) >= goal

    /** New words still to learn. Zero until stats land, so nothing claims otherwise. */
    val newAvailable: Int get() = inputs.stats?.new ?: 0

    val reviewDisabled: Boolean
        get() = inputs.isGuest || (inputs.stats?.due ?: 0) == 0

    val newBlock: TodayNewBlock
        get() {
            // A guest cannot study new words at all; the prompt to sign in is
            // a different message from a dead end, so this stays None.
            if (inputs.isGuest) return TodayNewBlock.None
            val stats = inputs.stats ?: return TodayNewBlock.None
            if (newAvailable == 0) return TodayNewBlock.AllLearned
            // Grey the button rather than let the user in only to bounce back
            // out of an empty session.
            if (StudyQuotas.computeNewLimit(goal = goal, due = stats.due) == 0) {
                return TodayNewBlock.ReviewBacklog
            }
            return TodayNewBlock.None
        }

    val newDisabled: Boolean
        get() = inputs.isGuest || newBlock != TodayNewBlock.None

    val subtitle: TodaySubtitle
        get() {
            if (inputs.isGuest) return TodaySubtitle.GuestBrowsing
            val stats = inputs.stats ?: return TodaySubtitle.Unknown
            if (stats.due > 0) return TodaySubtitle.ReviewDue
            // Goal reached wins over everything below, so this line can never
            // contradict the 達成 badge next to it.
            if (goalReached) return TodaySubtitle.GoalReached
            if ((stats.todayNew ?: 0) > 0) return TodaySubtitle.NewDoneToday
            if (newAvailable > 0) return TodaySubtitle.NewAvailable
            return TodaySubtitle.AllLearned
        }

    /**
     * The backlog-tapers-the-quota fact, or null when it is not happening.
     *
     * @return due count and the reduced limit.
     */
    val quotaAdjustment: Pair<Int, Int>?
        get() {
            if (inputs.isGuest) return null
            val stats = inputs.stats ?: return null
            if ((stats.todayNew ?: 0) >= goal) return null
            if (newAvailable <= 0) return null
            val limit = StudyQuotas.computeNewLimit(goal = goal, due = stats.due)
            return if (limit in 1 until goal) stats.due to limit else null
        }

    /**
     * Which of the three captions speaks, if any.
     *
     * None of them speaks before stats land: a hint about today's numbers,
     * shown before today's numbers exist, is a guess.
     */
    val heroHint: TodayHeroHint?
        get() {
            if (newBlock != TodayNewBlock.None) return TodayHeroHint.NewBlocked
            if (quotaAdjustment != null) return TodayHeroHint.QuotaAdjusted
            if (reviewDisabled && inputs.stats != null) return TodayHeroHint.NothingToReview
            return null
        }
}
