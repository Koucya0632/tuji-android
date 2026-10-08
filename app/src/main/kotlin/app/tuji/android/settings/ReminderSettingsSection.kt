package app.tuji.android.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSection
import app.tuji.android.core.design.TujiSettingRow
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.study.ReminderTime
import app.tuji.android.reminders.StudyReminders
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * 提醒 — iOS's `ReminderSettingsSection`: the switch, the time while it is on,
 * and a way to the system's notification settings once they have been refused.
 */
@Composable
fun ReminderSettingsSection(reminders: StudyReminders) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by reminders.enabled.collectAsStateWithLifecycle()
    val time by reminders.time.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(reminders.notificationsAllowed()) }
    var picking by remember { mutableStateOf(false) }
    // Coming back from the system's settings is when the answer may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = reminders.notificationsAllowed() }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        reminders.markAsked()
        allowed = reminders.notificationsAllowed()
        // A refusal leaves the switch off, as on iOS; the row below says why.
        if (granted) scope.launch { reminders.setEnabled(true) }
    }

    fun toggle(on: Boolean) {
        when {
            !on -> scope.launch { reminders.setEnabled(false) }
            reminders.notificationsAllowed() -> scope.launch { reminders.setEnabled(true) }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            // Older systems never ask: notifications are on unless switched off by hand.
            else -> { reminders.markAsked(); allowed = false }
        }
    }

    val denied = !allowed && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || reminders.askedForPermission)

    TujiSection(title = stringResource(R.string.reminder_section), footer = stringResource(R.string.reminder_footer)) {
        TujiSettingRow(
            label = stringResource(R.string.reminder_daily),
            subtitle = stringResource(R.string.reminder_daily_why),
            showsArrow = false,
            trailing = { TujiCheckbox(enabled && allowed) { toggle(it) } },
        )
        if (enabled && allowed) {
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.reminder_time),
                value = time.label(DateFormat.is24HourFormat(context)),
                onClick = { picking = true },
            )
        }
        if (denied) {
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.reminder_denied),
                subtitle = stringResource(R.string.reminder_denied_why),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }

    if (picking) {
        ReminderTimeSheet(
            initial = time,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { picked ->
                picking = false
                scope.launch { reminders.setTime(picked) }
            },
            onDismiss = { picking = false },
        )
    }
}

private fun ReminderTime.label(is24Hour: Boolean): String {
    val at = LocalTime.of(hour, minute)
    return if (is24Hour) at.format(DateTimeFormatter.ofPattern("HH:mm")) else at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

/** The time, picked in a sheet of this app's own — a dial on paper, not the platform's dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeSheet(
    initial: ReminderTime,
    is24Hour: Boolean,
    onConfirm: (ReminderTime) -> Unit,
    onDismiss: () -> Unit,
) = TujiWindow(onDismiss = onDismiss) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    Box(
        Modifier.fillMaxSize().background(TujiColor.Scrim).tujiClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                // Swallows taps so one on the sheet does not reach the scrim.
                .tujiClickable {}
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = TujiSpace.S3).background(TujiColor.Ink).padding(top = TujiBorder.Bw3))
            Text(
                stringResource(R.string.reminder_time),
                style = TujiType.h2,
                color = TujiColor.Ink,
                modifier = Modifier.padding(horizontal = TujiSpace.S4),
            )
            Box(Modifier.fillMaxWidth().padding(TujiSpace.S4), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = TujiColor.Paper2,
                        selectorColor = TujiColor.Current,
                        containerColor = TujiColor.Paper,
                        clockDialSelectedContentColor = TujiColor.Ink,
                        clockDialUnselectedContentColor = TujiColor.Ink,
                        periodSelectorSelectedContainerColor = TujiColor.Current,
                        periodSelectorUnselectedContainerColor = TujiColor.Paper2,
                        periodSelectorSelectedContentColor = TujiColor.Ink,
                        periodSelectorUnselectedContentColor = TujiColor.Ink2,
                        timeSelectorSelectedContainerColor = TujiColor.Current,
                        timeSelectorUnselectedContainerColor = TujiColor.Paper2,
                        timeSelectorSelectedContentColor = TujiColor.Ink,
                        timeSelectorUnselectedContentColor = TujiColor.Ink2,
                    ),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = TujiSpace.S4, end = TujiSpace.S4, bottom = TujiSpace.S4),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                TujiButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    style = TujiButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                )
                TujiButton(
                    text = stringResource(R.string.reminder_time_confirm),
                    onClick = { onConfirm(ReminderTime(state.hour, state.minute)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
