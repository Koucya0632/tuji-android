package app.tuji.android.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
 * The email form, in its two modes.
 *
 * iOS keeps these as two files (`SignupView`, `SigninView`) that differ by a
 * title, a subtitle and one validation rule — and then repeat the other 150
 * lines. The rendered screens here are the same two screens; the duplication is
 * the part not worth porting.
 *
 * Password validation is signup's, verbatim: at least 8 characters, and only
 * printable ASCII. The second rule exists because Supabase accepts a password
 * the user then cannot retype on a different keyboard.
 */
@Composable
fun EmailAuthScreen(
    auth: AuthService,
    mode: EmailRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val type = TujiType
    val insets = WindowInsets.systemBars.asPaddingValues()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AuthFailure?>(null) }
    var confirmationSent by remember { mutableStateOf(false) }

    val trimmed = email.trim()
    val asciiOnly = password.all { it.code in 33..126 }
    val canSubmit = trimmed.contains("@") && !busy && when (mode) {
        EmailRoute.SignUp -> password.length >= 8 && asciiOnly
        EmailRoute.SignIn -> password.isNotEmpty()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
    ) {
        Spacer(Modifier.height(insets.calculateTopPadding() + TujiSpace.S3))
        Text(
            stringResource(R.string.auth_back),
            style = type.bodySmStrong,
            color = TujiColor.Ink2,
            modifier = Modifier.tujiClickable(enabled = !busy, onClick = onBack),
        )
        Spacer(Modifier.height(TujiSpace.S4))

        Text(
            stringResource(
                if (mode == EmailRoute.SignUp) R.string.auth_create_account
                else R.string.auth_sign_in
            ),
            style = type.h2,
            color = TujiColor.Ink,
        )
        if (mode == EmailRoute.SignUp) {
            Spacer(Modifier.height(TujiSpace.S2))
            Text(
                stringResource(R.string.auth_create_account_detail),
                style = type.bodySm,
                color = TujiColor.Ink3,
            )
        }

        Spacer(Modifier.height(TujiSpace.S4))
        FieldLabel(stringResource(R.string.auth_email), stringResource(R.string.auth_email_hint))
        TujiTextField(
            value = email,
            onValueChange = { email = it; failure = null },
            placeholder = stringResource(R.string.auth_email_placeholder),
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(TujiSpace.S3))
        FieldLabel(
            stringResource(R.string.auth_password),
            if (mode == EmailRoute.SignUp) stringResource(R.string.auth_password_hint) else null,
        )
        TujiTextField(
            value = password,
            onValueChange = { password = it; failure = null },
            placeholder = if (mode == EmailRoute.SignUp) {
                stringResource(R.string.auth_password_min)
            } else {
                stringResource(R.string.auth_password)
            },
            isPassword = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )

        // Progress, not a scolding: the count says how far there is to go.
        if (mode == EmailRoute.SignUp && password.isNotEmpty()) {
            val problem = when {
                !asciiOnly -> stringResource(R.string.auth_password_charset)
                password.length < 8 -> stringResource(R.string.auth_password_short, 8 - password.length)
                else -> null
            }
            problem?.let {
                Spacer(Modifier.height(TujiSpace.S1))
                Text(it, style = type.label, color = TujiColor.Ink3)
            }
        }

        Spacer(Modifier.height(TujiSpace.S4))
        TujiButton(
            text = stringResource(
                when {
                    busy && mode == EmailRoute.SignUp -> R.string.auth_creating
                    busy -> R.string.auth_signing_in
                    mode == EmailRoute.SignUp -> R.string.auth_create_account
                    else -> R.string.auth_sign_in
                }
            ),
            enabled = canSubmit,
            onClick = {
                if (busy) return@TujiButton
                busy = true
                failure = null
                confirmationSent = false
                scope.launch {
                    when (mode) {
                        EmailRoute.SignUp -> when (val r = auth.signUp(trimmed, password)) {
                            is SignUpResult.Failed -> failure = r.reason
                            is SignUpResult.PendingEmailConfirmation -> confirmationSent = true
                            is SignUpResult.SignedIn -> Unit
                        }
                        EmailRoute.SignIn -> when (val r = auth.signIn(trimmed, password)) {
                            is AuthAttempt.Failed -> failure = r.reason
                            else -> Unit
                        }
                    }
                    busy = false
                }
            },
        )

        failure?.let {
            Spacer(Modifier.height(TujiSpace.S3))
            Text(stringResource(it.messageRes()), style = type.bodySm, color = TujiColor.Alert)
        }

        if (confirmationSent) {
            Spacer(Modifier.height(TujiSpace.S3))
            Text(
                stringResource(R.string.auth_confirmation_sent),
                style = type.bodySmStrong,
                color = TujiColor.Ink,
            )
            Spacer(Modifier.height(TujiSpace.S1))
            Text(
                stringResource(R.string.auth_confirmation_detail),
                style = type.bodySm,
                color = TujiColor.Ink3,
            )
        }

        Spacer(Modifier.height(TujiSpace.S4))
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(
                stringResource(
                    if (mode == EmailRoute.SignUp) R.string.auth_have_account
                    else R.string.auth_no_account
                ),
                style = type.bodySm,
                color = TujiColor.Ink3,
            )
            Text(
                stringResource(
                    if (mode == EmailRoute.SignUp) R.string.auth_sign_in
                    else R.string.auth_create_account
                ),
                style = type.bodySmStrong,
                color = TujiColor.Ink2,
            )
        }
        Spacer(Modifier.height(insets.calculateBottomPadding() + TujiSpace.S5))
    }
}

@Composable
private fun FieldLabel(title: String, hint: String?) {
    Text(title, style = TujiType.bodySmStrong, color = TujiColor.Ink)
    hint?.let {
        Text(it, style = TujiType.label, color = TujiColor.Ink3)
    }
    Spacer(Modifier.height(TujiSpace.S1))
}
