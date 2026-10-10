package app.tuji.android.update

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tuji.android.R
import app.tuji.android.core.design.TujiPrompt
import kotlinx.coroutines.launch

/**
 * The prompt itself: one sentence and two buttons, hung on the shell.
 *
 * Here rather than inline in `TujiRoot` because it has nothing to do with the
 * tabs, the stack or the tour — it only needs somewhere that is always on
 * screen. The shell gives it the two things only the shell knows.
 *
 * No release notes: that is a second piece of copy to write, in four
 * languages, on every release, and the only decision being asked for is now or
 * later.
 */
@Composable
fun AppUpdatePrompt(
    updates: AppUpdateStore,
    play: PlayAppUpdates,
    studyFocusActive: Boolean,
    tourRunning: Boolean,
) {
    val pending by updates.pendingVersionCode.collectAsState()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    // ON_RESUME is replayed to a new observer, so this is the cold start as
    // well as every return to the foreground — the second is for somebody who
    // has not closed the app in days.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { updates.checkIfNeeded() }
        activity?.let(play::resumeIfInProgress)
    }

    if (AppUpdatePolicy.mayPresent(pending, studyFocusActive, tourRunning)) {
        TujiPrompt(
            title = stringResource(R.string.app_update_title),
            message = stringResource(R.string.app_update_message),
            confirm = stringResource(R.string.app_update_confirm),
            cancel = stringResource(R.string.app_update_later),
            onConfirm = {
                updates.dismiss()
                activity?.let(play::start)
            },
            onCancel = updates::dismiss,
        )
    }
}
