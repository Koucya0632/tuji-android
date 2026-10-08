package app.tuji.android.core.study

import app.tuji.android.core.model.StudyStats

/**
 * What 今日 says and offers, derived from the day's state — iOS's `TodayDecisions`.
 *
 * Enums, not strings: the screen owns the words, this owns the verdict. That
 * split is what keeps it testable without a resource bundle, and it is also
 * what stops a hard-coded Chinese line leaking into a ja/en UI from here —
 * there is no string to leak.
 *
 * This used to be a deliberate subset, missing iOS's 主題 cases because
 * Android had no theme selection or completion readout yet. It has both now,
 * so what is left to learn is counted over the themes being studied, and the
 * no-themes and empty-themes dead ends are named as iOS names them.
 */
data class TodayInputs(
    /** Null until the first fetch lands. */
    val stats: StudyStats? = null,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    /**
     * The selected themes' readout — the hero's 主題進度. With it, what is
     * left to learn is counted over the themes being studied; without it, the
     * server's `new` stands in.
     */
    val completion: CompletionReadout? = null,
    /** Whether the per-theme progress has arrived; until then its 0s are not facts. */
    val progressLoaded: Boolean = false,
) {
    companion object {
        /** iOS's `UserSettings` default. */
        const val DEFAULT_DAILY_GOAL = 10
    }
}

/**
 * Which line sits under the greeting. One case per thing 今日 can truthfully
 * say, in the order the rules resolve.
 */
enum class TodaySubtitle {
    /** No themes picked: the first thing to do is pick some. */
    PickThemes,

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

    /** No themes picked. The theme prompt below says so; the hero adds no caption. */
    NoThemes,

    /** The picked themes have no words behind them. */
    NoCards,

    /** Nothing left to learn in the themes being studied. */
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

    private val showThemePrompt: Boolean get() = inputs.completion?.showsThemePrompt == true

    val goalReached: Boolean
        get() = (inputs.stats?.todayNew ?: 0) >= goal

    /**
     * New words still to learn: in the selected themes once their progress has
     * loaded, so 學新字 is not lit for words outside what is being studied;
     * the server's count before that. Zero until either lands.
     */
    val newAvailable: Int
        get() {
            val selection = inputs.completion?.inputs
            return if (selection != null && inputs.progressLoaded) {
                maxOf(0, selection.totalInSelection - selection.seenInSelection)
            } else {
                inputs.stats?.new ?: 0
            }
        }

    val reviewDisabled: Boolean
        get() = (inputs.stats?.due ?: 0) == 0

    val newBlock: TodayNewBlock
        get() {
            if (showThemePrompt) return TodayNewBlock.NoThemes
            val selection = inputs.completion?.inputs
            if (selection != null && inputs.progressLoaded && selection.totalInSelection == 0) {
                return TodayNewBlock.NoCards
            }
            // Nothing known yet is not "all learned".
            if (inputs.stats == null && !inputs.progressLoaded) return TodayNewBlock.None
            if (newAvailable == 0) return TodayNewBlock.AllLearned
            // Grey the button rather than let the user in only to bounce back
            // out of an empty session.
            if (StudyQuotas.computeNewLimit(goal = goal, due = inputs.stats?.due ?: 0) == 0) {
                return TodayNewBlock.ReviewBacklog
            }
            return TodayNewBlock.None
        }

    val newDisabled: Boolean
        get() = newBlock != TodayNewBlock.None

    val subtitle: TodaySubtitle
        get() {
            if (showThemePrompt) return TodaySubtitle.PickThemes
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
     * shown before today's numbers exist, is a guess. No themes is said by the
     * theme prompt below, not by the hero.
     */
    val heroHint: TodayHeroHint?
        get() {
            if (newBlock != TodayNewBlock.None && newBlock != TodayNewBlock.NoThemes) return TodayHeroHint.NewBlocked
            if (quotaAdjustment != null) return TodayHeroHint.QuotaAdjusted
            if (reviewDisabled && inputs.stats != null) return TodayHeroHint.NothingToReview
            return null
        }
}
