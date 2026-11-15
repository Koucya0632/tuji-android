package app.tuji.android.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.BuildConfig
import app.tuji.android.ShellContext
import app.tuji.android.StudyRoute
import app.tuji.android.atlas.WordDetailPanel
import app.tuji.android.atlas.rememberWordInsights
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.model.CheckInDecision
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.study.AppMutation
import kotlinx.coroutines.launch

/**
 * 學新字 and 複習, over the whole window — no tab bar, no account row: a
 * session that can be left by tapping a tab is a session that gets left by
 * accident.
 *
 * Everything about the session that is not the flow itself — which queue, how
 * many, whether there is another round — is [StudyRoute.source]'s.
 */
@Composable
fun StudySessionHost(route: StudyRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val direction = shell.direction
    val uiLang = shell.uiLang
    val settings = shell.settings
    val source = route.source
    val queue = source.queue(themes = app.study, lists = app.study, travel = app.travel)
    val personal = shell.learning.personal

    // 報錯 from inside either session: the same version string and interface language.
    val reporter = remember(uiLang) {
        StudyReporter(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            uiLang = uiLang,
            submit = app.study::submitReport,
        )
    }
    val fullDetail: @Composable (String) -> Unit = { wordId ->
        WordDetailPanel(
            wordId = wordId,
            catalog = app.catalogReading,
            atlas = app.atlas,
            audio = app.clipPlayer,
            speech = app.speech,
            direction = direction,
            uiLang = uiLang,
            accent = settings.accent,
            showChinese = settings.showZh,
            session = direction.targetLanguage,
            insights = rememberWordInsights(app.wordInsightsStore, wordId, direction, uiLang, shell.entitlement),
        )
    }
    // A session just moved the scores every badge in 圖鑑 draws. Without this
    // the user finishes twenty cards, opens 圖鑑, and sees the tiers they had
    // before they started — on the screen they went to in order to see the change.
    val close = {
        app.invalidator.post(AppMutation.StudySessionClosed)
        page.pop()
    }

    when (route.mode) {
        StudyMode.Review -> {
            val haptics = rememberTujiHaptics()
            val vm = remember(route) {
                ReviewViewModel(
                    queues = queue,
                    writer = app.answerWriter,
                    direction = direction,
                    uiLang = uiLang,
                    accent = settings.accent,
                    pool = { app.catalog.words },
                    requestDrain = { AnswerDrainWorker.enqueue(app) },
                    audio = app.clipPlayer,
                    online = { app.isOnline() },
                    hints = app.onboarding,
                    haptics = haptics,
                ).also { it.load(StudyMode.Review, limit = source.reviewLimit) }
            }
            // 再來一輪's count: what is still due once this round's answers have
            // landed. Re-read every time a round finishes, so a second round's
            // summary does not offer the first round's number.
            val reviewState by vm.state.collectAsStateWithLifecycle()
            val roundFinished = reviewState is ReviewViewModel.State.Done
            var remainingDue by remember(vm) { mutableIntStateOf(0) }
            LaunchedEffect(vm, roundFinished) {
                remainingDue = 0
                if (!roundFinished || !source.offersAnotherRound) return@LaunchedEffect
                vm.awaitWrites()
                remainingDue = runCatching { app.study.stats(direction).stats?.due ?: 0 }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .getOrDefault(0)
            }
            // 個人筆記 under the reveal, once the plan says the feature exists.
            val notes by app.wordNotesStore.notes.collectAsStateWithLifecycle()
            val notesExist = shell.access(MemberFeature.WordNote, hasOwnData = true) != MemberAccessLevel.Hidden
            ReviewScreen(
                vm = vm,
                remainingDue = remainingDue,
                onAnotherRound = if (source.offersAnotherRound) ({ vm.anotherRound() }) else null,
                noteFor = { id -> if (notesExist) notes.byWordId[id]?.body else null },
                showChinese = settings.showZh,
                fullDetail = fullDetail,
                bookmarked = { id -> id in personal.bookmarked },
                onBookmark = app.cardsSourceStore::toggle,
                checkIn = { FinishCheckIn(shell, direction, vm::awaitWrites, it) },
                reporter = reporter,
                onClose = close,
            )
        }
        StudyMode.New -> {
            val haptics = rememberTujiHaptics()
            val vm = remember(route) {
                NewFlowViewModel(
                    queues = queue,
                    writer = app.answerWriter,
                    direction = direction,
                    uiLang = uiLang,
                    pool = { app.catalog.words },
                    requestDrain = { AnswerDrainWorker.enqueue(app) },
                    audio = app.clipPlayer,
                    accent = settings.accent,
                    online = { app.isOnline() },
                    haptics = haptics,
                    catalog = app.catalogReading,
                ).also {
                    it.load(
                        source.newQueue(
                            goal = settings.dailyGoal,
                            due = shell.learning.today.stats?.due ?: 0,
                            themes = shell.learning.studyCategories,
                        ),
                    )
                }
            }
            NewFlowScreen(
                vm = vm,
                showChinese = settings.showZh,
                session = direction.targetLanguage,
                uiLang = uiLang,
                fullDetail = fullDetail,
                bookmarked = { it in personal.bookmarked },
                onBookmark = app.cardsSourceStore::toggle,
                speech = app.speech,
                accent = settings.accent,
                reporter = reporter,
                checkIn = { FinishCheckIn(shell, direction, vm::awaitWrites, it) },
                onClose = close,
            )
        }
    }
}

/**
 * 今天已打卡 on a study finish screen — the refresh iOS's
 * `refreshesFinishedSession` runs, then the card.
 *
 * Waits for the session's answers to land, then re-reads the streak and the
 * wallet: before that the server may not count today, and the streak handed
 * in at the start is yesterday's number. Draws nothing until the server says
 * today was studied, so answers parked offline never claim a check-in.
 */
@Composable
private fun FinishCheckIn(
    shell: ShellContext,
    direction: LearningDirection,
    awaitWrites: suspend () -> Unit,
    modifier: Modifier,
) {
    val app = shell.app
    var refreshed by remember { mutableStateOf(false) }
    val progress by app.progressStore.snapshot.collectAsStateWithLifecycle()
    val checkIn by app.checkInStore.snapshot.collectAsStateWithLifecycle()
    val haptics = rememberTujiHaptics()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        awaitWrites()
        app.progressStore.load(direction)
        app.checkInStore.loadReward()
        refreshed = true
    }
    val streak = progress.streak
    // Fades in, as iOS's `.transition(.opacity)`: it arrives a beat after the
    // screen, once the server counts today.
    AnimatedVisibility(
        visible = refreshed && streak != null && streak.todayCount > 0,
        enter = fadeIn(TujiMotion.ease(TujiMotion.D2)),
        exit = fadeOut(TujiMotion.ease(TujiMotion.D2)),
        modifier = modifier,
    ) {
        SessionCheckInCard(
            streak = streak?.current ?: 0,
            reward = CheckInDecision.finishReward(checkIn.reward(fallbackStudiedToday = true)),
            claiming = checkIn.claiming,
            claimFailed = checkIn.claimFailed,
            onClaim = { scope.launch { if (app.checkInStore.claim()) haptics.success() } },
        )
    }
}
