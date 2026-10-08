package app.tuji.android.reminders

import android.content.SharedPreferences
import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.study.ReminderTime
import app.tuji.android.core.study.StudyReminderPlan

import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the reminders physically go — alarms in production, a list in a test. */
interface ReminderScheduling {
    /** Whether the app may post notifications at all. */
    fun notificationsAllowed(): Boolean
    fun add(entry: StudyReminderPlan.Entry, title: String, body: String)
    fun cancel(ids: Collection<String>)
}

/**
 * What a reminder says, resolved in the app's own language by the screen that
 * schedules it. [titleDue] keeps its `%1$d` for today's count.
 */
data class ReminderTexts(val titleDue: String, val titleGeneric: String, val body: String) {
    fun title(due: Int?): String = due?.let { titleDue.format(it) } ?: titleGeneric
}

/**
 * 每日學習提醒 — iOS's `StudyReminders`.
 *
 * Owns the switch and the time, and lays out [StudyReminderPlan]'s week of
 * one-shot reminders whenever anything they depend on moves: the setting, the
 * due count, whether today has been studied, the language they are written in.
 * Every layout first clears what the last one left.
 *
 * The inputs of the last layout are kept on disk with the date they were true
 * for, so a reboot — which drops every alarm — can lay the week out again
 * before the app is opened; a due count or a "studied" from another day is
 * not carried over.
 */
class StudyReminders(
    private val scheduler: ReminderScheduling,
    private val prefs: SharedPreferences,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) : AccountScopedStore {

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _time = MutableStateFlow(
        if (prefs.contains(KEY_HOUR)) ReminderTime(prefs.getInt(KEY_HOUR, 20), prefs.getInt(KEY_MINUTE, 0)) else ReminderTime.Default,
    )
    val time: StateFlow<ReminderTime> = _time.asStateFlow()

    private val layout = Mutex()

    /**
     * Whether the switch has asked for the notification permission before.
     * Android 13+ reports "not allowed" both before the question and after a
     * refusal; only the second is worth the 通知已關閉 row.
     */
    val askedForPermission: Boolean get() = prefs.getBoolean(KEY_ASKED, false)

    fun markAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    fun notificationsAllowed(): Boolean = scheduler.notificationsAllowed()

    suspend fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        rescheduleFromSaved()
    }

    suspend fun setTime(time: ReminderTime) {
        _time.value = time
        prefs.edit().putInt(KEY_HOUR, time.hour).putInt(KEY_MINUTE, time.minute).apply()
        rescheduleFromSaved()
    }

    /** The screen's view of the world changed: remember it, and lay the week out again. */
    suspend fun reschedule(due: Int?, studiedToday: Boolean, texts: ReminderTexts) {
        val today = now().toLocalDate().toString()
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_DUE, due ?: -1)
            .putBoolean(KEY_STUDIED, studiedToday)
            .putString(KEY_TITLE_DUE, texts.titleDue)
            .putString(KEY_TITLE, texts.titleGeneric)
            .putString(KEY_BODY, texts.body)
            .apply()
        rescheduleFromSaved()
    }

    /** Lay the week out from what was last remembered — what a reboot does. */
    suspend fun rescheduleFromSaved() = layout.withLock {
        clearPending()
        if (!_enabled.value || !scheduler.notificationsAllowed()) return@withLock
        val texts = savedTexts() ?: return@withLock
        val current = now()
        val sameDay = prefs.getString(KEY_DATE, null) == current.toLocalDate().toString()
        val entries = StudyReminderPlan.entries(
            now = current,
            time = _time.value,
            due = prefs.getInt(KEY_DUE, -1).takeIf { sameDay && it >= 0 },
            studiedToday = sameDay && prefs.getBoolean(KEY_STUDIED, false),
        )
        entries.forEach { entry ->
            runCatching { scheduler.add(entry, texts.title(entry.dueCount), texts.body) }
                .onFailure { Log.w(TAG, "reminder add failed: ${entry.id}", it) }
        }
        prefs.edit().putStringSet(KEY_IDS, entries.map { it.id }.toSet()).apply()
    }

    private fun clearPending() {
        val ours = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        if (ours.isNotEmpty()) scheduler.cancel(ours)
        prefs.edit().remove(KEY_IDS).apply()
    }

    private fun savedTexts(): ReminderTexts? {
        val due = prefs.getString(KEY_TITLE_DUE, null) ?: return null
        val generic = prefs.getString(KEY_TITLE, null) ?: return null
        val body = prefs.getString(KEY_BODY, null) ?: return null
        return ReminderTexts(due, generic, body)
    }

    /** Sign-out: the next account starts with reminders off, and nothing of this one's stays scheduled. */
    override fun reset() {
        clearPending()
        _enabled.value = false
        _time.value = ReminderTime.Default
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "TujiReminders"
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val KEY_DATE = "inputs.date"
        const val KEY_DUE = "inputs.due"
        const val KEY_STUDIED = "inputs.studied"
        const val KEY_TITLE_DUE = "text.titleDue"
        const val KEY_TITLE = "text.title"
        const val KEY_BODY = "text.body"
        const val KEY_IDS = "scheduled"
        const val KEY_ASKED = "asked"
    }
}

