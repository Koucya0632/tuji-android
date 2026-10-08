package app.tuji.android.core.study

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** When the daily reminder fires, on the phone's clock. */
data class ReminderTime(val hour: Int, val minute: Int) {
    companion object {
        val Default = ReminderTime(20, 0)
    }
}

/**
 * 每日學習提醒 — iOS's `StudyReminderPlan`: which reminders to lay out, as
 * plain data, so the rules are testable without an alarm in sight.
 *
 * One reminder per day for the next [HORIZON_DAYS], each its own one-shot
 * alarm rather than a repeating one: today's can then be skipped once today
 * has been studied, and today's alone can carry the due count — the only day
 * that number is known for.
 */
object StudyReminderPlan {
    const val HORIZON_DAYS = 7

    /** Every id this feature lays out starts with this, so clearing touches nothing else. */
    const val ID_PREFIX = "tuji.reminder."

    data class Entry(
        /** `tuji.reminder.yyyy-MM-dd` — one per day, so a re-layout replaces rather than stacks. */
        val id: String,
        val fireAt: ZonedDateTime,
        /** Today's due count, when there is one; null for every other day. */
        val dueCount: Int?,
    )

    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun entries(now: ZonedDateTime, time: ReminderTime, due: Int?, studiedToday: Boolean): List<Entry> {
        val today = now.toLocalDate()
        return (0 until HORIZON_DAYS).mapNotNull { offset ->
            // Studying is what the reminder asks for; once done, today's is moot.
            if (offset == 0 && studiedToday) return@mapNotNull null
            val date = today.plusDays(offset.toLong())
            val fire = date.atTime(time.hour, time.minute).atZone(now.zone)
            if (!fire.isAfter(now)) return@mapNotNull null
            Entry(
                id = ID_PREFIX + date.format(day),
                fireAt = fire,
                dueCount = if (offset == 0) due?.takeIf { it > 0 } else null,
            )
        }
    }
}
