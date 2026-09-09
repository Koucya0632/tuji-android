package app.tuji.android.core.study

import app.tuji.android.core.study.ReviewSchedule.Countdown
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewScheduleTest {

    private val now = 1_757_000_000_000L
    private fun inDays(d: Double) = ReviewSchedule.countdown(now + (d * 86_400_000).toLong(), now)

    @Test fun `a card already due has nothing to count down`() {
        assertEquals(Countdown.Overdue, ReviewSchedule.countdown(now - 1, now))
        assertEquals(Countdown.Overdue, ReviewSchedule.countdown(now, now))
    }

    /**
     * The handover points are the whole content of this rule, and each one is a
     * place the sentence changes unit.
     */
    @Test fun `each unit takes over where the last one stops`() {
        assertEquals(Countdown.Minutes(30), inDays(30.0 / 1440))
        assertEquals(Countdown.Hours(3), inDays(3.0 / 24))
        assertEquals(Countdown.Days(3), inDays(3.0))
        assertEquals(Countdown.Weeks(2), inDays(14.0))
        assertEquals(Countdown.Months(3), inDays(90.0))
        assertEquals(Countdown.Years(2.0), inDays(730.0))
    }

    /**
     * 59 minutes must not round up to an hour: a user who comes back when the
     * app said to and finds nothing waiting stops trusting the number.
     */
    @Test fun `just under an hour is still minutes`() {
        assertEquals(Countdown.Minutes(59), inDays(59.0 / 1440))
        assertEquals(Countdown.Hours(1), inDays(61.0 / 1440))
    }

    @Test fun `a day is days, not hours`() {
        assertEquals(Countdown.Days(1), inDays(1.0))
        assertEquals(Countdown.Hours(23), inDays(23.0 / 24))
    }

    @Test fun `a week is weeks and a month is months`() {
        assertEquals(Countdown.Days(6), inDays(6.0))
        assertEquals(Countdown.Weeks(1), inDays(7.0))
        assertEquals(Countdown.Weeks(4), inDays(29.0))
        assertEquals(Countdown.Months(1), inDays(30.0))
    }

    /** A year keeps a decimal — 1.5 年 and 2 年 are not the same promise. */
    @Test fun `years keep one decimal`() {
        val c = inDays(547.0) as Countdown.Years
        assertEquals(1.5, c.value, 0.01)
    }
}
