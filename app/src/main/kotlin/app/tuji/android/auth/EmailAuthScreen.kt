package app.tuji.android.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.auth.AuthAttempt
import app.tuji.android.core.auth.AuthFailure
import app.tuji.android.core.auth.AuthService
import app.tuji.android.core.auth.SignUpResult
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiSpace
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
 * **The action is pinned to the bottom**, above the keyboard, and the fields
 * scroll above it — iOS's shape. With the button in the scroll, a phone with the
 * keyboard up showed the password field and nothing to press.
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
    onSwitchMode: (EmailRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AuthFailure?>(null) }
    var confirmationSent by remember { mutableStateOf(false) }

    val signUp = mode == EmailRoute.SignUp
    val trimmed = email.trim()
    val asciiOnly = password.all { it.code in 33..126 }
    val canSubmit = trimmed.contains("@") && !busy && if (signUp) password.length >= 8 && asciiOnly else password.isNotEmpty()

    val submit: () -> Unit = submit@{
        if (!canSubmit) return@submit
        focus.clearFocus()
        busy = true
        failure = null
        scope.launch {
            if (signUp) {
                when (val r = auth.signUp(trimmed, password)) {
                    is SignUpResult.Failed -> failure = r.reason
                    is SignUpResult.PendingEmailConfirmation -> confirmationSent = true
                    is SignUpResult.SignedIn -> Unit
                }
            } else {
                when (val r = auth.signIn(trimmed, password)) {
                    is AuthAttempt.Failed -> failure = r.reason
                    else -> Unit
                }
            }
            busy = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(top = insets.calculateTopPadding())
            .imePadding(),
    ) {
        Box(Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2)) {
            BackCircle(enabled = !busy, onClick = onBack)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(
                    stringResource(if (signUp) R.string.auth_create_account else R.string.auth_sign_in),
                    style = TujiType.h2,
                    color = TujiColor.Ink,
                )
                if (signUp) {
                    Text(stringResource(R.string.auth_create_account_detail), style = TujiType.bodySm, color = TujiColor.Ink3)
                }
            }

            LabelledField(
                label = stringResource(R.string.auth_email),
                helper = if (signUp) stringResource(R.string.auth_email_hint) else null,
            ) {
                AuthField(
                    value = email,
                    onValueChange = { email = it; failure = null },
                    label = stringResource(R.string.auth_email),
                    placeholder = if (signUp) stringResource(R.string.auth_email_placeholder) else "",
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                )
            }

            LabelledField(label = stringResource(R.string.auth_password), helper = null) {
                AuthField(
                    value = password,
                    onValueChange = { password = it; failure = null },
                    label = stringResource(R.string.auth_password),
                    placeholder = if (signUp) stringResource(R.string.auth_password_min) else "",
                    enabled = !busy,
                    password = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.focusRequester(passwordFocus),
                )
                // Under the field, where the eye already is: progress, not a
                // scolding — the count says how far there is to go, and the
                // charset line is the one that is an actual problem.
                if (signUp) {
                    when {
                        !asciiOnly -> Text(stringResource(R.string.auth_password_charset), style = TujiType.label, color = TujiColor.Alert)
                        password.isEmpty() -> Text(stringResource(R.string.auth_password_hint), style = TujiType.label, color = TujiColor.Ink3)
                        password.length < 8 -> Text(
                            stringResource(R.string.auth_password_short, 8 - password.length),
                            style = TujiType.label,
                            color = TujiColor.Ink3,
                        )
                    }
                }
            }

            failure?.let { ErrorBox(stringResource(it.messageRes())) }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4)
                .padding(top = TujiSpace.S4, bottom = TujiSpace.S4 + insets.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            TujiButton(
                text = stringResource(
                    when {
                        busy && signUp -> R.string.auth_creating
                        busy -> R.string.auth_signing_in
                        signUp -> R.string.auth_create_account
                        else -> R.string.auth_sign_in
                    },
                ),
                enabled = canSubmit,
                onClick = submit,
            )
            // The whole row is the target, not just the two words at the end of
            // it: a screen whose only way to the other mode is a piece of text
            // that does nothing is a dead end you cannot see.
            Row(
                Modifier
                    .tujiClickable(enabled = !busy) { onSwitchMode(if (signUp) EmailRoute.SignIn else EmailRoute.SignUp) }
                    .padding(vertical = TujiSpace.S2),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
            ) {
                Text(
                    stringResource(if (signUp) R.string.auth_have_account else R.string.auth_no_account),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                )
                Text(
                    stringResource(if (signUp) R.string.auth_sign_in else R.string.auth_sign_up),
                    style = TujiType.bodySm,
                    color = TujiColor.BrandSecondary,
                )
            }
        }
    }

    // A decision to act on, so a prompt rather than two lines under the button:
    // the next thing to do happens somewhere else (the inbox), and the one
    // thing to do here afterwards is sign in.
    if (confirmationSent) {
        TujiPrompt(
            style = TujiPromptStyle.Success,
            title = stringResource(R.string.auth_confirmation_sent),
            message = stringResource(R.string.auth_confirmation_detail),
            confirm = stringResource(R.string.auth_go_sign_in),
            cancel = null,
            onConfirm = {
                confirmationSent = false
                onSwitchMode(EmailRoute.SignIn)
            },
            onCancel = { confirmationSent = false },
        )
    }
}

/** 返回, as a round button on the page margin — the auth flow has no bar to put it in. */
@Composable
private fun BackCircle(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.auth_back)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.3f), CircleShape)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        TujiGlyph.ArrowLeft(size = 18.dp, tint = TujiColor.Ink)
    }
}

@Composable
private fun LabelledField(label: String, helper: String?, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Text(label, style = TujiType.label, color = TujiColor.Ink2)
        field()
        helper?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
    }
}

/**
 * The auth form's field: paper with a light rule, as iOS draws it. Not
 * `TujiTextField`'s 紙2 block — on a page that is nothing but two fields, the
 * blocks were the heaviest thing on it.
 */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    var shown by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.25f))
            .padding(start = TujiSpace.S3, end = if (password) TujiSpace.S1 else TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TujiType.body.copy(color = TujiColor.Ink),
            cursorBrush = SolidColor(TujiColor.Ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = modifier
                .weight(1f)
                .padding(vertical = TujiSpace.S3)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = TujiType.body, color = TujiColor.Ink3)
                    }
                    inner()
                }
            },
        )
        if (password) {
            val toggle = stringResource(if (shown) R.string.auth_password_hide else R.string.auth_password_show)
            Box(
                Modifier
                    .size(44.dp)
                    .tujiClickable { shown = !shown }
                    .semantics { contentDescription = toggle },
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Eye(crossed = shown, size = 20.dp, tint = TujiColor.Ink3)
            }
        }
    }
}

/** A failure the user can act on, in its own ground rather than a red sentence under the button. */
@Composable
private fun ErrorBox(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Alert.copy(alpha = 0.08f))
            .padding(TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).background(TujiColor.Alert))
        Text(message, style = TujiType.bodySm, color = TujiColor.Ink2)
    }
}
