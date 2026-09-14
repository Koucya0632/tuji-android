package app.tuji.android.core.study

/**
 * The sentence under a streak milestone — iOS's `MilestoneView.subtitle`. The
 * server attaches a milestone only on the answer that crosses 30, 100 or 365
 * days; anything else it may send gets the general line.
 */
enum class MilestoneLine {
    Month, Hundred, Year, KeepGoing;

    companion object {
        fun of(streak: Int): MilestoneLine = when (streak) {
            30 -> Month
            100 -> Hundred
            365 -> Year
            else -> KeepGoing
        }
    }
}
