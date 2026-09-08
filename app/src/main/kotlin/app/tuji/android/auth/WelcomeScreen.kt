package app.tuji.android.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.tuji.android.R
import app.tuji.android.core.auth.AuthAttempt
import app.tuji.android.core.auth.AuthFailure
import app.tuji.android.core.auth.AuthService
import app.tuji.android.core.auth.SignUpResult
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiTextField
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import kotlinx.coroutines.launch

/**
 * Where an account is reached.
 *
 * Three routes, and the order is deliberate: the two providers first, because
 * that is how most accounts here were made, and email underneath because it is
 * the one people fall back to. 先逛逛 is last and quiet — a way in, not a way
 * around.
 *
 * The screen offers a way *back* when it was reached by leaving guest mode
 * ([AuthService.cameFromGuest]); without that it is an exit-less dead end for
 * someone who tapped 登入 by accident.
 */
@Composable
fun WelcomeScreen(auth: AuthService, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val type = TujiType

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    // The activity draws edge to edge, so the content has to say where the
    // system bars are. Without this the wordmark sits under the clock.
    val insets = WindowInsets.systemBars.asPaddingValues()

    fun run(block: suspend () -> Notice?) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            notice = block()
            busy = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(insets.calculateTopPadding() + TujiSpace.S5))
        Text("Tuji", style = type.h1, color = TujiColor.Ink)
        Spacer(Modifier.height(TujiSpace.S2))
        Text(
            stringResource(R.string.welcome_tagline),
            style = type.body,
            color = TujiColor.Ink2,
        )

        Spacer(Modifier.height(TujiSpace.S5))

        TujiButton(
            text = stringResource(R.string.welcome_continue_with_google),
            enabled = !busy,
            onClick = {
                run {
                    when (val r = auth.signInWithGoogle(context)) {
                        is AuthAttempt.Succeeded -> null
                        // Backing out of the sheet must not leave a red line
                        // under the button. It is neither success nor failure.
                        is AuthAttempt.Cancelled -> null
                        is AuthAttempt.Failed -> Notice.Error(r.reason)
                        is AuthAttempt.LaunchedExternally -> null
                    }
                }
            },
        )

        Spacer(Modifier.height(TujiSpace.S2))

        TujiButton(
            text = stringResource(R.string.welcome_continue_with_apple),
            style = TujiButtonStyle.Secondary,
            enabled = !busy,
            onClick = {
                run {
                    when (val r = auth.signInWithApple()) {
                        // The browser has the flow now; nothing is
                        // authenticated yet. Saying so beats a screen that
                        // looks idle while Chrome is loading.
                        is AuthAttempt.LaunchedExternally -> Notice.OpeningBrowser
                        is AuthAttempt.Failed -> Notice.Error(r.reason)
                        else -> null
                    }
                }
            },
        )

        Spacer(Modifier.height(TujiSpace.S5))

        TujiTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.welcome_email),
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(TujiSpace.S2))
        TujiTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.welcome_password),
            isPassword = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.height(TujiSpace.S3))

        TujiButton(
            text = stringResource(R.string.welcome_sign_in),
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                run {
                    when (val r = auth.signIn(email.trim(), password)) {
                        is AuthAttempt.Failed -> Notice.Error(r.reason)
                        else -> null
                    }
                }
            },
        )
        Spacer(Modifier.height(TujiSpace.S2))
        TujiButton(
            text = stringResource(R.string.welcome_sign_up),
            style = TujiButtonStyle.Secondary,
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                run {
                    when (val r = auth.signUp(email.trim(), password)) {
                        is SignUpResult.Failed -> Notice.Error(r.reason)
                        // No session yet, and the screen must say why rather
                        // than sit there looking as if nothing happened.
                        is SignUpResult.PendingEmailConfirmation -> Notice.CheckYourEmail
                        is SignUpResult.SignedIn -> null
                    }
                }
            },
        )

        notice?.let {
            Spacer(Modifier.height(TujiSpace.S3))
            NoticeLine(it)
        }

        Spacer(Modifier.height(TujiSpace.S5))

        Text(
            stringResource(
                if (auth.cameFromGuest) R.string.welcome_back_to_browsing
                else R.string.welcome_browse_as_guest
            ),
            style = type.bodySmStrong,
            color = TujiColor.Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .tujiClickable(enabled = !busy) { auth.enterGuestMode() }
                .padding(vertical = TujiSpace.S3),
        )
        Spacer(Modifier.height(insets.calculateBottomPadding() + TujiSpace.S5))
    }
}

private sealed interface Notice {
    data class Error(val reason: AuthFailure) : Notice
    data object CheckYourEmail : Notice
    data object OpeningBrowser : Notice
}

@Composable
private fun NoticeLine(notice: Notice) {
    val (res, color) = when (notice) {
        is Notice.Error -> notice.reason.messageRes() to TujiColor.Alert
        Notice.CheckYourEmail -> R.string.welcome_check_your_email to TujiColor.Ink2
        Notice.OpeningBrowser -> R.string.welcome_opening_browser to TujiColor.Ink2
    }
    Text(stringResource(res), style = TujiType.bodySm, color = color)
}
