package app.tuji.android.reminders

import android.content.SharedPreferences
import app.tuji.android.core.study.ReminderTime
import app.tuji.android.core.study.StudyReminderPlan
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 每日學習提醒's store: what gets laid out, cleared, and remembered across a reboot. */
class StudyRemindersTest {

    private class Scheduler(var allowed: Boolean = true) : ReminderScheduling {
        val pending = linkedMapOf<String, Pair<StudyReminderPlan.Entry, String>>()
        override fun notificationsAllowed() = allowed
        override fun add(entry: StudyReminderPlan.Entry, title: String, body: String) { pending[entry.id] = entry to title }
        override fun cancel(ids: Collection<String>) { ids.forEach { pending.remove(it) } }
    }

    private val zone = ZoneId.of("Asia/Tokyo")
    private var now = ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, zone)
    private val texts = ReminderTexts("今天有 %1\$d 個字要複習", "今天學一點吧", "花幾分鐘複習，記得更牢。")

    private fun reminders(scheduler: Scheduler, prefs: SharedPreferences = MemoryPrefs()) =
        StudyReminders(scheduler, prefs, now = { now })

    @Test fun `off lays out nothing, on lays out the week with today's count`() = runTest {
        val scheduler = Scheduler()
        val r = reminders(scheduler)
        r.reschedule(due = 12, studiedToday = false, texts = texts)
        assertTrue(scheduler.pending.isEmpty())

        r.setEnabled(true)
        assertEquals(7, scheduler.pending.size)
        assertEquals("今天有 12 個字要複習", scheduler.pending.values.first().second)
        assertEquals("今天學一點吧", scheduler.pending.values.last().second)
    }

    @Test fun `studying today drops today's, and a new time replaces the week`() = runTest {
        val scheduler = Scheduler()
        val r = reminders(scheduler)
        r.reschedule(due = 3, studiedToday = false, texts = texts)
        r.setEnabled(true)
        r.reschedule(due = 0, studiedToday = true, texts = texts)
        assertEquals(6, scheduler.pending.size)
        assertFalse("tuji.reminder.2026-10-08" in scheduler.pending)

        r.setTime(ReminderTime(7, 30))
        assertEquals(6, scheduler.pending.size)
        assertTrue(scheduler.pending.values.all { it.first.fireAt.hour == 7 && it.first.fireAt.minute == 30 })
    }

    @Test fun `a reboot the next day does not carry yesterday's count or studied`() = runTest {
        val scheduler = Scheduler()
        val prefs = MemoryPrefs()
        reminders(scheduler, prefs).apply {
            reschedule(due = 9, studiedToday = true, texts = texts)
            setEnabled(true)
        }
        // Next morning, after a reboot dropped every alarm.
        now = now.plusDays(1)
        scheduler.pending.clear()
        reminders(scheduler, prefs).rescheduleFromSaved()
        assertEquals("tuji.reminder.2026-10-09", scheduler.pending.keys.first())
        assertEquals("今天學一點吧", scheduler.pending.values.first().second)
    }

    @Test fun `notifications refused or signing out leaves nothing scheduled`() = runTest {
        val scheduler = Scheduler()
        val r = reminders(scheduler)
        r.reschedule(due = 1, studiedToday = false, texts = texts)
        r.setEnabled(true)
        scheduler.allowed = false
        r.rescheduleFromSaved()
        assertTrue(scheduler.pending.isEmpty())

        scheduler.allowed = true
        r.rescheduleFromSaved()
        r.reset()
        assertTrue(scheduler.pending.isEmpty())
        assertFalse(r.enabled.value)
        assertEquals(ReminderTime.Default, r.time.value)
    }
}

/** Just enough `SharedPreferences` for a store that writes through `edit().apply()`. */
private class MemoryPrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = (map[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in map
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) map.clear()
            removals.forEach { map.remove(it) }
            map.putAll(changes)
        }
    }
}
