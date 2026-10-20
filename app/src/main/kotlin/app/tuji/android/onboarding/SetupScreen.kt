package app.tuji.android.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.tuji.android.R
import app.tuji.android.core.design.LockupEntrance
import app.tuji.android.core.design.TujiBrandLockup
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.network.ApiError

/**
 * The account's first-run settings, written without a screen of questions —
 * iOS's `SetupView` since it stopped asking.
 *
 * Setup used to ask for themes and a daily goal. Neither is asked any more: a
 * new user goes from sign-in straight to 今日. The account still needs them
 * written — the server hands out no new words with no theme — so [run] seeds
 * both from `SetupChoices` (the beginner themes and default goal for a new
 * account, the account's own for a returning one) while the mark stays up.
 * 設定 changes either later.
 *
 * Only a failure shows anything, and iOS's split decides what: a session the
 * server will not accept (401) offers 重新登入, which signs out; anything else
 * is retried, not answered by throwing the session away.
 */
@Composable
fun AutoSetupScreen(
    run: suspend () -> Result<Unit>,
    onSignOut: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        running = true
        failure = null
        failure = run().exceptionOrNull()
        running = false
    }

    val insets = WindowInsets.systemBars.asPaddingValues()
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        // The settled mark, not the entrance: the launch screen already played
        // it, and this reads as the same load carrying on into 今日.
        TujiBrandLockup(Modifier.align(Alignment.Center), entrance = LockupEntrance.Finished)

        failure?.let { error ->
            val unauthorized = error is ApiError.NotAuthenticated || (error as? ApiError.Http)?.status == 401
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S4)
                    .padding(bottom = insets.calculateBottomPadding() + TujiSpace.S5),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (unauthorized) R.string.setup_unauthorized else R.string.setup_failed),
                    style = TujiType.label,
                    color = TujiColor.Alert,
                    textAlign = TextAlign.Center,
                )
                TujiButton(
                    text = stringResource(R.string.retry),
                    enabled = !running,
                    onClick = { attempt++ },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (unauthorized) {
                    TujiButton(
                        text = stringResource(R.string.setup_sign_in_again),
                        style = TujiButtonStyle.Secondary,
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
