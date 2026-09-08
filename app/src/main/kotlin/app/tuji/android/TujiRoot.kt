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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tuji.android.auth.WelcomeScreen
import app.tuji.android.core.auth.AuthState
import app.tuji.android.core.model.LaunchAccountState
import app.tuji.android.core.model.LaunchContext
import app.tuji.android.core.model.LaunchDestination
import app.tuji.android.core.model.LaunchRouting
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.onboarding.LearningDirectionScreen
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.spike.FuriganaSpikeScreen
import app.tuji.android.study.AnswerDrainWorker
import app.tuji.android.study.isOnline
import app.tuji.android.study.ReviewScreen
import app.tuji.android.study.ReviewViewModel
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.Word
import app.tuji.android.core.design.TujiButtonStyle
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
    // Held in composition as well as on disk so picking a language re-routes
    // immediately rather than on the next launch.
    var direction by remember { mutableStateOf<LearningDirection?>(app.onboarding.learningDirection) }

    val account = when (val s = session.state) {
        is AuthState.Checking -> LaunchAccountState.Checking
        is AuthState.SignedOut -> LaunchAccountState.SignedOut
        is AuthState.Guest -> LaunchAccountState.Guest
        // `setupDone` is 設定 the account's first-run profile step, which
        // arrives with M4. Until it exists, nobody is held at it.
        is AuthState.SignedIn -> LaunchAccountState.SignedIn(s.user.id, setupDone = true)
    }

    val destination = LaunchRouting.destination(
        LaunchContext(
            account = account,
            learningDirectionSelected = direction != null,
            introDone = app.onboarding.introDone,
        ),
        // The 3-page intro is not ported yet, so nobody is held waiting for a
        // catalogue that no screen consumes.
        catalogReady = true,
    )

    when (destination) {
        is LaunchDestination.Splash -> SplashScreen()
        is LaunchDestination.LearningDirection -> LearningDirectionScreen(
            onPick = {
                app.onboarding.learningDirection = it
                direction = it
            },
        )
        // The 3-page marketing intro is not ported. Treating it as seen sends a
        // signed-out user to Welcome, which is where they were going anyway.
        is LaunchDestination.Onboarding -> WelcomeScreen(app.auth)
        is LaunchDestination.Welcome -> WelcomeScreen(app.auth)
        is LaunchDestination.Setup -> SignedInShell(app, identity = null)
        is LaunchDestination.Main -> SignedInShell(
            app,
            identity = (session.state as? AuthState.SignedIn)?.user?.let {
                it.nickname ?: it.username ?: it.email
            },
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

    val direction = app.onboarding.learningDirection ?: LearningDirection.ZH_EN

    // Ahead of 複習 rather than inside it: 聽句 asks the catalogue for its
    // second picture the moment the first card is prepared, and a pool that
    // arrives late does not make that question worse, it makes it not happen.
    LaunchedEffect(direction) { app.loadCatalog(direction) }

    var mode by remember { mutableStateOf<StudyMode?>(null) }
    mode?.let { picked ->
        val vm = remember(picked) {
            ReviewViewModel(
                queues = app.study,
                writer = app.answerWriter,
                direction = direction,
                uiLang = "zh-Hant",
                pool = { app.catalogPool },
                requestDrain = { AnswerDrainWorker.enqueue(app) },
                audio = app.clipPlayer,
                online = { app.isOnline() },
            ).also { it.load(picked) }
        }
        ReviewScreen(vm = vm, onClose = { mode = null })
        return
    }

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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            TujiButton(
                text = stringResource(R.string.study_start_review),
                onClick = { mode = StudyMode.Review },
            )
            // A debug affordance, and labelled as one: a brand-new account has
            // nothing due, so this is the only way to see a card before the
            // 學新字 flow exists.
            TujiButton(
                text = stringResource(R.string.study_start_new_debug),
                style = TujiButtonStyle.Secondary,
                onClick = { mode = StudyMode.New },
            )
        }
        FuriganaSpikeScreen(catalog = app.catalog)
    }
}
