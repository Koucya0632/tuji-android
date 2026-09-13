package app.tuji.android.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A window of its own over everything, for prompts and sheets.
 *
 * Drawn inside a screen, an overlay covers only that screen: the shell's back
 * arrow above it stays live and system back pops the page out from under the
 * open question — the bug `TujiPrompt`, 設定's sheets and 物見's 檢舉 each had
 * once. A dialog window brings back (as [onDismiss]) and a modal boundary for
 * TalkBack with it.
 *
 * Full-screen and edge to edge; the platform's dim and window animation are
 * turned off, because the content draws its own scrim and fade under this
 * app's motion rules.
 */
@Composable
fun TujiWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        content()
    }
}
