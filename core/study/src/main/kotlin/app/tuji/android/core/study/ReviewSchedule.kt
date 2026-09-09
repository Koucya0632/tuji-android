package app.tuji.android.core.study

import kotlin.math.roundToInt

/**
 * When a word comes back, said the way a person would say it.
 *
 * A port of the backend's `humanizeInterval` (`lib/srs.ts`), which is also what
 * iOS prints. The unit is chosen so the number stays small and the reader never
 * has to divide: 「約 3 週後」 rather than 「21 天後」.
 *
 * A pure function over a duration, so the interesting part — where each unit
 * takes over from the last — is testable without a clock.
 */
object ReviewSchedule {

    /** What the countdown says. [Overdue] is the one that is not a duration. */
    sealed interface Countdown {
        /** The card is already due; there is nothing to count down to. */
        data object Overdue : Countdown

        data class Minutes(val value: Int) : Countdown
        data class Hours(val value: Int) : Countdown
        data class Days(val value: Int) : Countdown
        data class Weeks(val value: Int) : Countdown
        data class Months(val value: Int) : Countdown

        /** Kept to one decimal — 「約 1.5 年後」 rather than 「約 2 年後」. */
        data class Years(val value: Double) : Countdown
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun countdown(untilMs: Long, nowMs: Long): Countdown {
        val remaining = untilMs - nowMs
        if (remaining <= 0) return Countdown.Overdue
        val days = remaining.toDouble() / DAY
        if (days < 1) {
            val minutes = (remaining.toDouble() / MINUTE).roundToInt()
            // Under an hour is said in minutes even at 59, because "1 小時後"
            // for something 59 minutes away is the kind of rounding that makes
            // a user come back and find nothing waiting.
            if (minutes < 60) return Countdown.Minutes(minutes)
            return Countdown.Hours((remaining.toDouble() / HOUR).roundToInt())
        }
        if (days < 7) return Countdown.Days(days.roundToInt())
        if (days < 30) return Countdown.Weeks((days / 7).roundToInt())
        if (days < 365) return Countdown.Months((days / 30).roundToInt())
        return Countdown.Years(days / 365)
    }
}
