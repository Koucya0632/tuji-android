package app.tuji.android.core.model

/**
 * 打卡's decisions, ported from iOS's `CheckInDecision`.
 *
 * The rule they encode: studying *is* the check-in (one word-card answer, the
 * same thing the streak counts), and the points are a separate tap to collect.
 * Points are a 永久會員 benefit; everyone else gets the streak.
 */
object CheckInDecision {
    sealed interface Reward {
        /** Nothing to say: the catalog did not load, or check-in is paused. */
        data object Hidden : Reward

        /** Not on points billing — the upgrade is what unlocks it. */
        data class Locked(val daily: Int) : Reward

        /** Eligible, nothing studied yet today. */
        data class NeedsStudy(val daily: Int) : Reward

        data class Claimable(val points: Int) : Reward

        data object Claimed : Reward

        /** This month's cap is already granted. */
        data class Capped(val cap: Int) : Reward
    }

    const val DEFAULT_DAILY = 10
    const val DEFAULT_MONTHLY_CAP = 300

    /**
     * @param fallbackStudiedToday for a server that predates
     *   `benefits.studiedToday`. The streak's `todayCount` is per direction, so
     *   this can say "not yet" for someone who studied the other language; the
     *   server's 409 then explains it.
     */
    fun reward(catalog: CreditCatalog?, wallet: CreditWallet?, fallbackStudiedToday: Boolean): Reward {
        catalog ?: return Reward.Hidden
        val daily = catalog.policy?.checkInDaily ?: DEFAULT_DAILY
        val cap = catalog.policy?.checkInMonthlyCap ?: DEFAULT_MONTHLY_CAP
        if (catalog.billingMode != "credits") return Reward.Locked(daily)
        if (!catalog.checkInEnabled || wallet == null) return Reward.Hidden
        val benefits = wallet.benefits
        if (!benefits.hasLifetime) return Reward.Locked(daily)
        if (benefits.checkedInToday) return Reward.Claimed
        val left = cap - benefits.checkInGrantedThisMonth
        if (left <= 0) return Reward.Capped(cap)
        if (!(benefits.studiedToday ?: fallbackStudiedToday)) return Reward.NeedsStudy(daily)
        return Reward.Claimable(minOf(daily, left))
    }

    /**
     * The points line on a study finish screen. Only what today's studying
     * earned: no upgrade pitch on a celebration, and 還沒學習 there only means
     * the answers are still parked offline — the unsynced notice says that.
     */
    fun finishReward(reward: Reward): Reward = when (reward) {
        is Reward.Claimable, Reward.Claimed, is Reward.Capped -> reward
        Reward.Hidden, is Reward.Locked, is Reward.NeedsStudy -> Reward.Hidden
    }
}
