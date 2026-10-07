package app.tuji.android.core.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.YearMonth
import java.util.Locale

/**
 * `GET /api/users/study-calendar` — one month of the 打卡 calendar.
 *
 * A filled day is a day with a word-card answer, bucketed in [timezone] (the
 * phone's, sent as `X-Tuji-Timezone`): the same rule and the same calendar as
 * the streak, and the same day the check-in reward is claimed against.
 */
@Serializable
data class StudyCalendarMonth(
    /** YYYY-MM */
    val month: String,
    val timezone: String = "",
    /** YYYY-MM-DD in [timezone]. */
    val today: String,
    /** YYYY-MM-DD, ascending. */
    val studiedDays: List<String> = emptyList(),
    val streak: StudyStreak = StudyStreak(),
)

/**
 * One calendar month as a 7-column grid: leading blanks, then day 1…n — iOS's
 * `MonthGrid`.
 *
 * @property cells null = a blank before day 1.
 */
data class MonthGrid(val month: String, val cells: List<Int?>) {

    /** YYYY-MM-DD for a day of this month. */
    fun date(day: Int): String = String.format(Locale.ROOT, "%s-%02d", month, day)

    companion object {
        /** [firstDayOfWeek] is the locale's, e.g. SUNDAY for zh-TW, MONDAY for much of Europe. */
        fun of(month: String, firstDayOfWeek: DayOfWeek): MonthGrid? {
            val ym = parse(month) ?: return null
            val leading = (ym.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
            return MonthGrid(month, List(leading) { null } + (1..ym.lengthOfMonth()).toList())
        }

        fun parse(month: String): YearMonth? {
            val parts = month.split("-")
            if (parts.size != 2) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            return YearMonth.of(y, m)
        }

        fun shift(month: String, by: Int): String? =
            parse(month)?.plusMonths(by.toLong())?.let { String.format(Locale.ROOT, "%04d-%02d", it.year, it.monthValue) }

        /** How many months [month] lies before [reference] (0 = same month). */
        fun monthsBefore(month: String, reference: String): Int? {
            val a = parse(month) ?: return null
            val b = parse(reference) ?: return null
            return (b.year * 12 + b.monthValue) - (a.year * 12 + a.monthValue)
        }
    }
}
