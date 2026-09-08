package app.tuji.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.auth.WelcomeScreen
import app.tuji.android.core.auth.AuthState
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.spike.FuriganaSpikeScreen
import kotlinx.coroutines.launch

/**
 * What the app shows, decided by where the account stands.
 *
 * [AuthState.Checking] is a distinct screen rather than a flavour of signed
 * out, and that is the whole reason the state exists: collapsing the two
 * flashes Welcome at every signed-in user on every cold start, for however
 * long the persisted session takes to read.
 */
@Composable
fun TujiRoot(app: TujiApplication) {
    val session by app.auth.session.collectAsStateWithLifecycle()

    when (val state = session.state) {
        is AuthState.Checking -> SplashScreen()
        is AuthState.SignedOut -> WelcomeScreen(app.auth)
        is AuthState.Guest -> SignedInShell(app, identity = null)
        is AuthState.SignedIn -> SignedInShell(
            app,
            identity = state.user.nickname ?: state.user.username ?: state.user.email,
        )
    }
}

@Composable
private fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
        contentAlignment = Alignment.Center,
    ) {
        Text("Tuji", style = TujiType.h1, color = TujiColor.Ink)
    }
}

/**
 * The catalogue, with a one-line account bar above it.
 *
 * A placeholder shell, not the real 首頁 — M1 replaces the body. The bar is
 * here because M0's gate is "sign in to the production account and pull real
 * data", and that is only demonstrated if the screen says *which* account.
 */
@Composable
private fun SignedInShell(app: TujiApplication, identity: String?) {
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = insets.calculateTopPadding())
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                identity ?: stringResource(R.string.guest_mode),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
            )
            Text(
                stringResource(
                    if (identity == null) R.string.sign_in_or_up else R.string.sign_out
                ),
                style = TujiType.bodySmStrong,
                color = TujiColor.Ink2,
                modifier = Modifier
                    .tujiClickable {
                        if (identity == null) {
                            app.auth.exitGuestMode()
                        } else {
                            scope.launch { app.auth.signOut() }
                        }
                    }
                    .padding(TujiSpace.S1),
            )
        }
        FuriganaSpikeScreen(catalog = app.catalog)
    }
}
