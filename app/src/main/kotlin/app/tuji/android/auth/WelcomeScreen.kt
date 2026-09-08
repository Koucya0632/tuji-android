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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.auth.AuthAttempt
import app.tuji.android.core.auth.AuthFailure
import app.tuji.android.core.auth.AuthService
import app.tuji.android.core.design.TujiBrandLockup
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRadius
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import kotlinx.coroutines.launch

/**
 * Where an account is reached. A port of `Tuji/Features/Auth/WelcomeView.swift`,
 * not a re-design.
 *
 * The order and the emphasis are the iOS ones and both are decisions: Apple is
 * the black system-style button and comes first because that is how most of
 * these accounts were made; Google is deliberately quiet; Email is a soft brand
 * tint rather than a third loud button; and the two ways *out* — 已有帳號 and
 * 先逛逛 — are plain text, because they are not what the screen is asking for.
 *
 * The mark is [TujiBrandLockup], vertically centred with the buttons pinned
 * low. That whitespace is the design, not an oversight.
 */
@Composable
fun WelcomeScreen(auth: AuthService, modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<EmailRoute?>(null) }

    when (val r = route) {
        null -> WelcomeContent(auth, modifier) { route = it }
        else -> EmailAuthScreen(
            auth = auth,
            mode = r,
            onBack = { route = null },
            onSwitchMode = { route = it },
        )
    }
}

/** Which email screen is showing. iOS spells these as two navigation routes. */
enum class EmailRoute { SignUp, SignIn }

@Composable
private fun WelcomeContent(
    auth: AuthService,
    modifier: Modifier,
    onEmailRoute: (EmailRoute) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val type = TujiType
    val insets = WindowInsets.systemBars.asPaddingValues()

    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AuthFailure?>(null) }
    var openingBrowser by remember { mutableStateOf(false) }

    fun attempt(block: suspend () -> AuthAttempt) {
        if (busy) return
        busy = true
        failure = null
        openingBrowser = false
        scope.launch {
            when (val r = block()) {
                is AuthAttempt.Failed -> failure = r.reason
                is AuthAttempt.LaunchedExternally -> openingBrowser = true
                // Backing out is neither success nor failure, and must not
                // leave a red line under the button.
                else -> Unit
            }
            busy = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        Spacer(Modifier.height(insets.calculateTopPadding()))

        // Reached from inside guest mode this screen is a root swap, not a
        // push — without an explicit way back it is a dead end for an
        // accidental tap.
        if (auth.cameFromGuest) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(TujiColor.Paper, CircleShape)
                        .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.3f), CircleShape)
                        .tujiClickable { auth.enterGuestMode() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = type.bodyStrong, color = TujiColor.Ink2)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        TujiBrandLockup(Modifier.align(Alignment.CenterHorizontally), scale = 0.88f)
        Spacer(Modifier.weight(1f))

        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                // Capped so every button shares one width, as on iOS.
                .widthIn(max = 360.dp)
                .padding(horizontal = TujiSpace.S4)
                .padding(bottom = insets.calculateBottomPadding() + TujiSpace.S5),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            AppleButton(enabled = !busy) { attempt { auth.signInWithApple() } }

            GoogleButton(
                label = stringResource(
                    if (busy) R.string.welcome_google_signing_in
                    else R.string.welcome_continue_with_google
                ),
                enabled = !busy,
            ) { attempt { auth.signInWithGoogle(context) } }

            failure?.let {
                Text(
                    stringResource(it.messageRes()),
                    style = type.label,
                    color = TujiColor.Alert,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TujiSpace.S3),
                )
            }
            if (openingBrowser) {
                Text(
                    stringResource(R.string.welcome_opening_browser),
                    style = type.label,
                    color = TujiColor.Ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EmailButton(enabled = !busy) { onEmailRoute(EmailRoute.SignUp) }

            Text(
                stringResource(R.string.welcome_have_account),
                style = type.bodySmStrong,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TujiSpace.S2)
                    .tujiClickable(enabled = !busy) { onEmailRoute(EmailRoute.SignIn) },
            )

            Text(
                // Someone who *left* guest mode to get here is not choosing a
                // mode — they are going back.
                stringResource(
                    if (auth.cameFromGuest) R.string.welcome_back_to_guest
                    else R.string.welcome_browse_as_guest
                ),
                style = type.label,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .tujiClickable(enabled = !busy) { auth.enterGuestMode() },
            )
        }
    }
}

/**
 * iOS gets `SignInWithAppleButton` from the system: black, the mark, the
 * localized label. There is no such control on Android, so it is rebuilt to
 * Apple's specification rather than restyled into something of ours.
 */
@Composable
private fun AppleButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .background(Color.Black, RoundedCornerShape(TujiRadius.R0))
            .tujiClickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_apple_logo),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(TujiSpace.S2))
        Text(
            stringResource(R.string.welcome_continue_with_apple),
            style = TujiType.bodySmStrong,
            color = Color.White,
        )
    }
}

/** Paper ground with a hairline — deliberately the quietest of the three. */
@Composable
private fun GoogleButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .background(TujiColor.Paper, RoundedCornerShape(TujiRadius.R0))
            .border(
                TujiBorder.Bw1,
                TujiColor.Rule.copy(alpha = 0.25f),
                RoundedCornerShape(TujiRadius.R0),
            )
            .tujiClickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS uses the SF Symbol `g.circle.fill` — a filled disc with the
        // letter knocked out — not Google's multicolour mark.
        Box(
            Modifier
                .size(18.dp)
                .background(TujiColor.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("G", style = TujiType.label, color = TujiColor.Paper)
        }
        Spacer(Modifier.size(TujiSpace.S2))
        Text(label, style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}

/** A soft brand tint: present, but not competing with the two providers. */
@Composable
private fun EmailButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .background(
                TujiColor.BrandPrimary.copy(alpha = 0.18f),
                RoundedCornerShape(TujiRadius.R0),
            )
            .tujiClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.welcome_use_email),
            style = TujiType.bodySmStrong,
            color = TujiColor.BrandSecondary,
        )
    }
}
