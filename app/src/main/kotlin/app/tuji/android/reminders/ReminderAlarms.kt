package app.tuji.android.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tuji.android.MainActivity
import app.tuji.android.R
import app.tuji.android.TujiApplication
import app.tuji.android.core.study.StudyReminderPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The reminders as alarms, each allowed to land within [WINDOW_MS] of its
 * time. Not exact on purpose: a study nudge a few minutes late costs nothing,
 * and exact alarms are a permission of their own that a reminder should not
 * have to ask for. Not plain inexact either — that gives the system an hour,
 * and an 8pm reminder at nine is a different evening.
 */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduling {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun add(entry: StudyReminderPlan.Entry, title: String, body: String) {
        val intent = fireIntent(context, entry.id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body)
        val pending = PendingIntent.getBroadcast(
            context, requestCode(entry.id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setWindow(AlarmManager.RTC_WAKEUP, entry.fireAt.toInstant().toEpochMilli(), WINDOW_MS, pending)
    }

    override fun cancel(ids: Collection<String>) {
        ids.forEach { id ->
            val pending = PendingIntent.getBroadcast(
                context, requestCode(id), fireIntent(context, id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    companion object {
        const val ACTION_FIRE = "app.tuji.android.reminder.FIRE"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val CHANNEL = "study_reminders"
        /** The smallest window the system honours for a non-exact alarm. */
        private const val WINDOW_MS = 10 * 60 * 1000L

        private fun fireIntent(context: Context, id: String) =
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id)

        /** `tuji.reminder.2026-10-08` → 20261008: one alarm slot per day. */
        fun requestCode(id: String): Int = id.removePrefix(StudyReminderPlan.ID_PREFIX).filter(Char::isDigit).toIntOrNull() ?: id.hashCode()

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}

/** Posts a reminder when its alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmReminderScheduler.ACTION_FIRE) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val id = intent.getStringExtra(AlarmReminderScheduler.EXTRA_ID) ?: return
        AlarmReminderScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AlarmReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(intent.getStringExtra(AlarmReminderScheduler.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(AlarmReminderScheduler.EXTRA_BODY))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(AlarmReminderScheduler.requestCode(id), notification)
    }
}

/**
 * A reboot drops every alarm, and a clock or time-zone change moves what "8pm"
 * means: lay the week out again from what was last remembered.
 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcasts; anything else is ignored.
        if (intent.action !in RESTORE_ACTIONS) return
        val app = context.applicationContext as? TujiApplication ?: return
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { app.studyReminders.rescheduleFromSaved() } finally { done.finish() }
        }
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
