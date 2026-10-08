package app.tuji.android.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
fun TujiWindow(
    onDismiss: () -> Unit,
    /**
     * The window is drawn on ink (a camera, a crop), so the status and
     * navigation bar icons turn light. A dialog window keeps its own bar
     * appearance, so the screen behind cannot decide this for it.
     */
    darkGround: Boolean = false,
    content: @Composable () -> Unit,
) {
    // A dialog is a view of its own, and every Compose view provides its own
    // context and configuration at the root — which drops the app's chosen
    // language (`ProvideAppLanguage`) and draws a `stringResource` inside
    // the window in the *device's* language. Carried across by hand.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
            // Only ever turned light: every other window keeps what it inherits.
            if (darkGround) window?.let { setLightBars(it, light = false) }
        }
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides configuration,
            content = content,
        )
    }
}

/** Dark bar icons on a light ground, light icons on ink. */
private fun setLightBars(window: android.view.Window, light: Boolean) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
    } else {
        @Suppress("DEPRECATION")
        val decor = window.decorView
        @Suppress("DEPRECATION")
        val mask = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = if (light) decor.systemUiVisibility or mask else decor.systemUiVisibility and mask.inv()
    }
}
