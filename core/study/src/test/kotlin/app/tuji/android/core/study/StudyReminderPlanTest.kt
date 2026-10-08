package app.tuji.android.core.study

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyReminderPlanTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 8, hour, minute, 0, 0, zone)
    private val eight = ReminderTime(20, 0)

    @Test fun `a week ahead, today first, today alone carrying the due count`() {
        val out = StudyReminderPlan.entries(at(9), eight, due = 12, studiedToday = false)
        assertEquals(7, out.size)
        assertEquals("tuji.reminder.2026-10-08", out.first().id)
        assertEquals(at(20), out.first().fireAt)
        assertEquals(12, out.first().dueCount)
        assertNull(out[1].dueCount)
        assertEquals("tuji.reminder.2026-10-14", out.last().id)
    }

    @Test fun `studied today skips today`() {
        val out = StudyReminderPlan.entries(at(9), eight, due = 12, studiedToday = true)
        assertEquals(6, out.size)
        assertEquals("tuji.reminder.2026-10-09", out.first().id)
        assertNull(out.first().dueCount)
    }

    @Test fun `a time already past today starts tomorrow`() {
        val out = StudyReminderPlan.entries(at(20, 30), eight, due = 3, studiedToday = false)
        assertEquals(6, out.size)
        assertEquals("tuji.reminder.2026-10-09", out.first().id)
    }

    @Test fun `nothing due reads as no count`() {
        assertNull(StudyReminderPlan.entries(at(9), eight, due = 0, studiedToday = false).first().dueCount)
        assertNull(StudyReminderPlan.entries(at(9), eight, due = null, studiedToday = false).first().dueCount)
    }
}
