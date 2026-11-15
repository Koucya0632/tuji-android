package app.tuji.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.BuildConfig
import app.tuji.android.account.AccountHost
import app.tuji.android.account.AccountScreen
import app.tuji.android.account.AccountViewModel
import app.tuji.android.assistant.AssistantHomeFeature
import app.tuji.android.assistant.AssistantHost
import app.tuji.android.assistant.TopicChatEntry
import app.tuji.android.assistant.prefetchAssistant
import app.tuji.android.atlas.AtlasCardsScreen
import app.tuji.android.atlas.AtlasHost
import app.tuji.android.atlas.SearchViewModel
import app.tuji.android.auth.WelcomeScreen
import app.tuji.android.checkin.CheckInSheet
import app.tuji.android.community.CommunityHost
import app.tuji.android.community.CommunityScreen
import app.tuji.android.community.CommunitySearchViewModel
import app.tuji.android.community.CommunityViewModel
import app.tuji.android.core.auth.AuthState
import app.tuji.android.core.catalog.CardsSource
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.catalog.StudyThemes
import app.tuji.android.core.design.LockupEntrance
import app.tuji.android.core.design.TujiBrandLockup
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiFace
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavLeading
import app.tuji.android.core.design.TujiPullToRefresh
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiTheme
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberReduceMotion
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.model.CheckInDecision
import app.tuji.android.core.model.LaunchAccountState
import app.tuji.android.core.model.LaunchContext
import app.tuji.android.core.model.LaunchDestination
import app.tuji.android.core.model.LaunchRouting
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.MemberAccess
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.study.AppMutation
import app.tuji.android.core.study.CompletionReadout
import app.tuji.android.core.study.FreeThemesProgress
import app.tuji.android.core.study.LearningRefreshCause
import app.tuji.android.core.study.MasteryDistribution
import app.tuji.android.core.study.RefreshTarget
import app.tuji.android.core.study.SetupChoices
import app.tuji.android.core.study.TodayCall
import app.tuji.android.core.study.TodayDecisions
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.core.study.targets
import app.tuji.android.manage.AtlasManageViewModel
import app.tuji.android.manage.ManageHost
import app.tuji.android.manage.MyCollectionsViewModel
import app.tuji.android.onboarding.AutoSetupScreen
import app.tuji.android.onboarding.LearningDirectionScreen
import app.tuji.android.reminders.ReminderTexts
import app.tuji.android.scenes.SceneHost
import app.tuji.android.scenes.SceneProgressAccount
import app.tuji.android.scenes.hasScenes
import app.tuji.android.scenes.sceneTodayCard
import app.tuji.android.spike.FuriganaSpikeScreen
import app.tuji.android.study.StudySessionHost
import app.tuji.android.today.TodayScreen
import app.tuji.android.today.TodayViewModel
import app.tuji.android.tour.FeatureTourFlow
import app.tuji.android.tour.FeatureTourOverlay
import app.tuji.android.tour.ProvideTourAnchors
import app.tuji.android.tour.TabTip
import app.tuji.android.tour.TabTipBubble
import app.tuji.android.tour.TourAdvance
import app.tuji.android.tour.TourCopy
import app.tuji.android.tour.rememberTourAnchors
import app.tuji.android.travel.TravelEntry
import app.tuji.android.travel.TravelHost
import app.tuji.android.update.AppUpdatePrompt
import app.tuji.android.wordlists.MeWordLists
import app.tuji.android.wordlists.WordListHost
import app.tuji.android.wordlists.WordListsPreload
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    // Read once into state rather than off the store on every recomposition:
    // finishing Setup has to *move* the app, and a plain preference read is not
    // something the routing recomposes for.
    var setupDone by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(session.state) {
        (session.state as? AuthState.SignedIn)?.user?.id?.let { id ->
            if (app.onboarding.setupDone(id)) setupDone = setupDone + id
        }
    }

    val account = when (val s = session.state) {
        is AuthState.Checking -> LaunchAccountState.Checking
        is AuthState.SignedOut -> LaunchAccountState.SignedOut
        is AuthState.SignedIn ->
            LaunchAccountState.SignedIn(s.user.id, setupDone = setupDone.contains(s.user.id))
    }

    // The floor under the launch mark. Without it the splash lasts exactly as
    // long as Supabase takes to say "no session" — tens of milliseconds — and
    // `TujiBrandLockup`'s 650ms entrance is cut off inside the first frame.
    // iOS runs the same gate as a task in `LaunchCoordinator.runLaunchGate()`.
    var launchReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LaunchRouting.MINIMUM_SPLASH_MS)
        launchReady = true
    }

    val destination = LaunchRouting.destination(
        LaunchContext(
            account = account,
            learningDirectionSelected = direction != null,
        ),
        // Setup loads the catalogue before it lets go, so nobody is held
        // waiting here for one.
        catalogReady = true,
        launchReady = launchReady,
    )

    val online by app.connectivity.online.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        // The one cut in the app worth covering: the launch mark handing over
        // to the first real screen. Everything after it is navigation, which
        // has a direction; this one has none, so it fades.
        //
        // 180ms and suppressed outright under 移除動畫 — iOS keeps this number
        // in `LaunchTransitionPolicy.opacityDuration(reduceMotion:)` rather
        // than in its three motion tokens, and returns nil for the second half
        // of that sentence. It is shorter than D2 on purpose: it is the first
        // thing anyone sees and it is in the way of the app.
        val launchReduceMotion = rememberReduceMotion()
        Crossfade(
            targetState = destination,
            animationSpec = tween(
                if (launchReduceMotion) 0 else LAUNCH_FADE_MS,
                easing = TujiMotion.EaseOut,
            ),
            label = "launch",
        ) { destination ->
        when (destination) {
            is LaunchDestination.Splash -> SplashScreen()
            is LaunchDestination.LearningDirection -> LearningDirectionScreen(
                onPick = {
                    app.onboarding.learningDirection = it
                    direction = it
                },
            )
            is LaunchDestination.Welcome -> WelcomeScreen(app.auth)
            is LaunchDestination.Setup -> {
                val setupLanguage = rememberDeviceLanguage()
                val setupScope = rememberCoroutineScope()
                AutoSetupScreen(
                    run = {
                        runCatching {
                            app.settingsStore.load(setupLanguage)
                            if (!app.settingsStore.loaded.value) error("settings did not load")
                            val settings = app.settingsStore.current.value
                            app.catalog.load(settings.direction)
                            // Words and themes load as one call that keeps the
                            // words when the themes fail — and the themes are
                            // what this needs, so ask again rather than write
                            // an account with none.
                            if (app.catalog.contents.value.categories.isEmpty()) {
                                app.catalog.load(settings.direction, force = true)
                            }
                            val categories = app.catalog.contents.value.categories
                            if (categories.isEmpty()) error("themes did not load")
                            val seed = SetupChoices.seed(
                                account = SetupChoices.AccountThemes(settings.studyCategories, settings.dailyGoal),
                                catalogIds = categories.map { it.id }.toSet(),
                                firstThemesFallback = categories.map { it.id },
                                presets = app.catalog.contents.value.presets,
                            )
                            app.settingsStore.completeSetup(seed.topicIds, seed.dailyGoal)
                            // The shell needs the catalogue for the themes just
                            // written; loading it here keeps the mark up instead
                            // of landing on an empty 今日.
                            app.catalog.load(app.settingsStore.current.value.direction, force = true)
                            app.onboarding.markSetupDone(destination.userId)
                            setupDone = setupDone + destination.userId
                        }
                    },
                    onSignOut = { setupScope.launch { app.auth.signOut() } },
                )
            }
            is LaunchDestination.Main -> SignedInShell(
                app,
                // iOS's order, blanks skipped, and the email's local part rather
                // than the whole address: a greeting is not the place to print
                // somebody's email on screen.
                identity = (session.state as? AuthState.SignedIn)?.user?.let { user ->
                    user.nickname?.takeIf { it.isNotBlank() }
                        ?: user.username?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
                },
            )
        }
        }
        // Over every screen but the splash, which says nothing that needs the
        // network yet. Requests explain their own failures; this says why.
        // It arrives from where it sits rather than blinking into existence:
        // a red pill appearing under the clock with no motion reads as a
        // rendering glitch, which is the one thing a message about the network
        // must not read as.
        //
        // 250ms **easeInOut**, which is what `RootView` spells out next to this
        // same banner — not the D2 easeOut everything else here uses. Both
        // halves carry the spec: a bare `fadeIn()` is a spring, and it is
        // slower than the slide it is paired with, so the banner spent the
        // whole slide almost transparent and only turned red once it had
        // stopped moving.
        val enterExit = TujiMotion.easeInOut<Float>(BANNER_MS)
        val slide = TujiMotion.easeInOut<IntOffset>(BANNER_MS)
        AnimatedVisibility(
            visible = destination != LaunchDestination.Splash && online == false,
            enter = slideInVertically(slide) { -it } + fadeIn(enterExit),
            exit = slideOutVertically(slide) { -it } + fadeOut(enterExit),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        ) {
            OfflineBanner()
        }
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
        // The mark, not the word. iOS's launch screen is this lockup with its
        // entrance; Android's was the string 「Tuji」 in a heading style, which
        // is the app introducing itself in the one place it has a face.
        TujiBrandLockup(entrance = LockupEntrance.Animated)
    }
}

/**
 * The signed-in app's one language decision, wrapped around everything that
 * draws. Split from [SignedInScreens] because the screens below it return early
 * — a study flow owns the whole window — and a provider cannot wrap a body that
 * returns out from under it.
 */
@Composable
private fun SignedInShell(app: TujiApplication, identity: String?) {
    val settings by app.settingsStore.current.collectAsStateWithLifecycle()
    val settingsLoaded by app.settingsStore.loaded.collectAsStateWithLifecycle()

    // What the app opened in before 設定 existed, and still the answer for the
    // frames before the account's has arrived.
    val deviceLanguage = rememberDeviceLanguage()
    LaunchedEffect(deviceLanguage) { app.settingsStore.load(deviceLanguage) }

    // The system voice answers "ready" a moment after it is started, and a word
    // page decides whether to draw its pronunciation button as it opens. Started
    // lazily by that page, the first one opened would not get the button.
    LaunchedEffect(Unit) { app.speech }

    // A launch with no connection leaves the account's settings, the
    // catalogue and the personal shelves unread, and nothing asked again: 圖鑑
    // sat on 載入圖鑑中… for good once the network came back. Only a return
    // from offline asks — at launch the effects above already are — and only
    // for what never arrived. The screen on show asks for its own; see the
    // per-tab effect in SignedInScreens.
    val reconnects by app.connectivity.reconnects.collectAsStateWithLifecycle()
    val reconnectsAtLaunch = remember { app.connectivity.reconnects.value }
    LaunchedEffect(reconnects) {
        if (reconnects == reconnectsAtLaunch) return@LaunchedEffect
        if (!app.settingsStore.loaded.value) app.settingsStore.load(deviceLanguage)
        val direction = app.settingsStore.current.value.direction
        if (!app.catalog.contents.value.loaded) app.catalog.load(direction)
        if (!app.cardsSourceStore.personal.value.loaded) {
            val lang = if (app.settingsStore.loaded.value) app.settingsStore.current.value.language else deviceLanguage
            app.cardsSourceStore.load(lang.wire, direction)
        }
        app.masteryStore.load(direction)
    }

    // The account's choice wins once it has arrived. Until then the device's
    // answer stands, so the first frame is not Chinese on a Japanese phone.
    val uiLanguage = if (settingsLoaded) settings.language else deviceLanguage

    // One language for both halves of the screen. It picks the `lang` the
    // *server* writes glosses and definitions in, and — through the provider —
    // the strings this app draws around them. Letting the two disagree is how
    // a fully Japanese interface ends up wrapped around Chinese content.
    ProvideAppLanguage(uiLanguage) {
        // And the face that draws them. `forUiLanguage` has existed since M0
        // with nobody to call it, because until 設定 there was no language to
        // give it: 中文 in Japanese glyph forms is not tofu, it is just subtly
        // the wrong 直 and 骨 on every screen.
        TujiTheme(face = TujiFace.forUiLanguage(uiLanguage.wire)) {
            SignedInScreens(app, identity, settings, uiLanguage)
        }
    }
}

/**
 * The signed-in app: the tabs, one back stack, and the pages pushed over them.
 *
 * The stack is [NavStack] and nothing else — the hardware back button and 返回
 * pop the same list, so the two can never disagree about where "back" is.
 *
 * What lives here is what belongs to the whole shell: the stores' loads, the
 * view models held above the pages, the four tab roots, the tour and the bar.
 * Each page is its feature's: this builds one [ShellContext] and hands every
 * family of [AppRoute.Page] to that feature's host.
 */
@Composable
private fun SignedInScreens(
    app: TujiApplication,
    identity: String?,
    settings: app.tuji.android.core.model.UserSettings,
    uiLanguage: UiLanguage,
) {
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val direction = settings.direction
    val deviceLanguage = rememberDeviceLanguage()
    val uiLang = uiLanguage.wire

    // Ahead of every screen that reads it: 圖鑑 draws it, 搜尋 filters it, and
    // 複習's 聽句 draws its second picture from it — a pool that arrives late
    // does not make that question worse, it makes it not happen.
    LaunchedEffect(direction, uiLang) {
        // Retuning first: the catalogue holds the *server's* words, and those
        // came back in whatever language was asked for last time.
        app.catalog.retune(uiLang)
        app.catalog.load(direction)
        // The two decks score separately — the same word id holds one score as
        // 中→日 and another as 中→英 — so a direction change drops the map
        // before asking for the new one.
        app.masteryStore.retune()
        app.masteryStore.load(direction)
        app.progressStore.retune()
        app.progressStore.load(direction)
        // 已收進 is deck-scoped like the catalogue; 書籤 is not, and `retune`
        // knows the difference.
        app.cardsSourceStore.retune()
        app.cardsSourceStore.load(uiLang, direction)
    }
    val catalog by app.catalog.contents.collectAsStateWithLifecycle()
    val scores by app.masteryStore.scores.collectAsStateWithLifecycle()
    val personal by app.cardsSourceStore.personal.collectAsStateWithLifecycle()
    val captureJobs by app.captureQueue.jobs.collectAsStateWithLifecycle()
    // The buzz for a capture landing belongs to a window, not to the queue —
    // which is why the queue reports it rather than owning a `TujiHaptics`.
    val captureHaptics = rememberTujiHaptics()
    LaunchedEffect(Unit) {
        app.captureQueue.celebrations.collect { captureHaptics.success() }
    }

    val progress by app.progressStore.snapshot.collectAsStateWithLifecycle()
    val session by app.auth.session.collectAsStateWithLifecycle()
    val settingsLoaded by app.settingsStore.loaded.collectAsStateWithLifecycle()

    // Also the one holder of the entitlement for the whole shell: 學習主題's
    // locks, 今日's numbers and 會員方案 read it from here, so a purchase seen
    // on one screen is seen on all of them.
    val account = remember {
        AccountViewModel(accounts = app.atlas, entitlements = app.atlas, weakWords = app.atlas)
    }
    val accountState by account.state.collectAsStateWithLifecycle()
    // The row at the top of 物見 is this account's public page, and it needs
    // the UID to find it — which arrives with 我的's account read.
    val myUid = accountState.me?.username

    // Play purchases paid but not yet granted (a crash, a pending payment that
    // cleared while Tuji was closed) are sent on entering the shell; a grant
    // reports itself as an [AppMutation].
    LaunchedEffect(Unit) { app.playBilling.reconcile() }

    // 個人筆記, once the entitlement says the feature exists: the review reveal
    // reads them with no request of its own. Loaded here, not on the word page,
    // so the first review of the day already has them.
    val notesExist = MemberAccess.level(
        MemberFeature.WordNote, accountState.entitlement, hasOwnData = true,
    ) != MemberAccessLevel.Hidden
    LaunchedEffect(notesExist) { if (notesExist) app.wordNotesStore.loadIfNeeded() }

    var nav by remember { mutableStateOf(NavStack()) }

    // 我做的 on request — iOS's `cardsSourceRequest`. Set when a capture is
    // handed to 生成佇列, so the 圖鑑 tab opens where its 生成中 tile is.
    var cardsSourceRequest by remember { mutableStateOf<CardsSource?>(null) }

    // The themes that actually get studied: the pick, narrowed to what this
    // account may study. The pick itself stays as chosen — 設定 shows it, and
    // it comes back whole the day the account becomes a member.
    val studyable = accountState.entitlement?.membership?.studyableCategories
    val studyCategories = remember(settings.studyCategories, studyable) {
        StudyThemes.effective(settings.studyCategories, studyable)
    }

    // Every word the 學習主題 selection can reach. 自定義 and 物見 are themes
    // with nothing in the catalogue — their words are the user's own and
    // taken-in cards — so a strip, a theme page or a 完成度 built from the
    // catalogue alone leaves both out while showing them ticked in 設定.
    val studyWords = remember(catalog.words, personal.mine, personal.taken) {
        StudyThemes.words(catalog.words, personal.mine, personal.taken)
    }
    // 完成度, once, for 今日's 主題進度 and 我's card: two screens printing two
    // numbers for one account read as a server bug for a week.
    val completion = remember(settingsLoaded, studyCategories, progress.categories, studyWords) {
        CompletionReadout(
            CompletionReadout.Inputs.from(
                settingsLoaded = settingsLoaded,
                studyCategories = studyCategories,
                progress = progress.categories,
                words = studyWords,
            ),
        )
    }

    val community = remember(direction) {
        CommunityViewModel(
            atlas = app.atlas,
            bookmarks = app.atlas,
            reporter = app.atlas,
            blocks = app.atlas,
            direction = direction,
        ).also { it.load() }
    }
    val communityExplore by community.explore.collectAsStateWithLifecycle()
    val communitySaved by community.saved.collectAsStateWithLifecycle()
    val communityMe by community.me.collectAsStateWithLifecycle()
    val communityBlocked by community.blocked.collectAsStateWithLifecycle()
    // 物見搜尋, held above the routes like 搜尋's model so opening a result and
    // coming back finds the results still there. Keyed on the direction, which
    // is in the request.
    val communitySearch = remember(direction) {
        CommunitySearchViewModel(
            remote = app.atlas,
            direction = direction,
            blocks = { community.blockList },
        )
    }
    // A block made on a pushed page applies to the results on the way back.
    LaunchedEffect(communityBlocked) { communitySearch.refilter() }

    // 圖鑑管理's shelf and 合集, above every page that draws them, so a card
    // deleted on its own page is already gone from the list behind.
    val openMembership: () -> Unit = {
        if (nav.current != AccountRoute.Membership) nav = nav.push(AccountRoute.Membership)
    }
    val manage = remember(direction) {
        AtlasManageViewModel(
            shelf = app.atlas,
            language = direction.targetLanguage,
            onMutation = app.invalidator::post,
            onNeedsMembership = { openMembership() },
        )
    }
    val myCollections = remember(direction) {
        MyCollectionsViewModel(
            authoring = app.atlas,
            language = direction.targetLanguage,
            onMutation = app.invalidator::post,
            onNeedsMembership = { openMembership() },
        )
    }

    val today = remember(direction) {
        TodayViewModel(
            stats = app.study,
            direction = direction,
        )
    }
    val todayStats by today.inputs.collectAsStateWithLifecycle()
    // 今日's verdicts read the selected themes too, as iOS's do: what is left
    // to learn is counted over them once their progress has landed.
    val todayInputs = todayStats.copy(
        completion = completion,
        progressLoaded = progress.loaded,
        // 解鎖全部主題 rather than 換幾個主題, once a non-member has learned
        // every free theme.
        freeThemes = remember(studyable, progress.categories) { FreeThemesProgress.of(studyable, progress.categories) },
    )

    // What to re-read is [LearningRefreshCause]'s to say for a pull and
    // [AppMutation]'s for a write; this only knows which store answers each
    // target. One `when`, so a pull and a write cannot answer the same target
    // with two different stores, and a new target does not compile until it
    // has one.
    val refreshTarget: suspend (RefreshTarget) -> Unit = { target ->
        when (target) {
            RefreshTarget.Progress -> app.progressStore.load(direction)
            RefreshTarget.Mastery -> app.masteryStore.load(direction, force = true)
            RefreshTarget.Catalogue -> app.catalog.load(direction, force = true)
            // Held by 今日's own model rather than a store, so it is asked
            // rather than loaded.
            RefreshTarget.Stats -> today.refresh()
            // Forced: the server has a word it did not have a moment ago, and
            // the store's own freshness window would hide it.
            RefreshTarget.PersonalShelves -> app.cardsSourceStore.load(uiLang, direction, force = true)
            RefreshTarget.Entitlement -> account.refreshEntitlement()
            RefreshTarget.CreditBalance -> app.assistantCaches.balanceChanged()
            RefreshTarget.Account -> account.refresh()
            RefreshTarget.CommunityFeed -> community.load()
            RefreshTarget.CommunitySaved -> community.loadSaved()
            RefreshTarget.CommunityMe -> myUid?.let { community.loadMe(it, force = true) }
            RefreshTarget.MyCollections -> myCollections.load()
        }
    }
    val refresh: suspend (Set<RefreshTarget>) -> Unit = { targets ->
        coroutineScope { targets.forEach { launch { refreshTarget(it) } } }
    }
    // Every write reported anywhere in the app lands here. Above the study
    // flows' early return, so a capture finishing mid-session is not left
    // waiting for the session to end.
    val latestRefresh by rememberUpdatedState(refresh)
    LaunchedEffect(Unit) {
        app.invalidator.targets.collect { latestRefresh(it) }
    }

    // 每日學習提醒: laid out again whenever what they say could change — the
    // due count, whether today has been studied, the language they are in —
    // and on every return to the foreground, as iOS does.
    val reminderTexts = ReminderTexts(
        titleDue = stringResource(R.string.reminder_title_due),
        titleGeneric = stringResource(R.string.reminder_title),
        body = stringResource(R.string.reminder_body),
    )
    val reminderDue = todayInputs.stats?.due
    val studiedToday = (progress.streak?.todayCount ?: 0) > 0
    var foregrounds by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { foregrounds++ }
    LaunchedEffect(reminderDue, studiedToday, reminderTexts, foregrounds) {
        app.studyReminders.reschedule(reminderDue, studiedToday, reminderTexts)
    }

    // Held above the routes rather than inside 搜尋's page, so opening a
    // result and coming back finds the results still there — as it does on
    // iOS, where the search screen stays on the stack under the word. Keyed on
    // both, because both are in the request's URL, and a model held across a
    // change of either would answer the next query with the previous deck's
    // scope.
    val search = remember(direction, uiLang) {
        SearchViewModel(
            remote = app.catalogReading,
            // A lambda, not `catalog.words`: the catalogue can still be loading
            // when this screen opens, and a list captured here would stay empty.
            local = { app.catalog.words },
            lang = uiLang,
            direction = direction,
            onFound = app.recentSearches::push,
        )
    }

    val shell = ShellContext(
        app = app,
        stack = { nav },
        setStack = { nav = it },
        settings = settings,
        uiLanguage = uiLanguage,
        ownerId = (session.state as? AuthState.SignedIn)?.user?.id,
        entitlement = accountState.entitlement,
        learning = LearningData(
            catalog = catalog,
            scores = scores,
            personal = personal,
            progress = progress,
            studyCategories = studyCategories,
            studyWords = studyWords,
            completion = completion,
            today = todayInputs,
        ),
        models = ShellModels(
            account = account,
            accountState = accountState,
            today = today,
            community = community,
            communitySearch = communitySearch,
            search = search,
            manage = manage,
            myCollections = myCollections,
        ),
        scope = scope,
    )
    // What features keep loaded for the whole shell, not for one page.
    WordListsPreload(shell)
    SceneProgressAccount(shell)

    var showSpike by remember { mutableStateOf(false) }

    // 打卡, from 今日's streak chip. The store is the app's, so the chip's dot,
    // this sheet and the study finish screens read one wallet.
    var showCheckIn by remember { mutableStateOf(false) }
    val checkInState by app.checkInStore.snapshot.collectAsStateWithLifecycle()
    // A new answer today re-reads the wallet regardless of age: the first one
    // is what makes the points claimable, and the dot should say so.
    val todayCount = progress.streak?.todayCount
    var seenTodayCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(todayCount) {
        val before = seenTodayCount
        seenTodayCount = todayCount
        if (before != null && todayCount != null && todayCount > before) app.checkInStore.loadReward()
    }

    // One saved-state slot per stack entry. Only the current entry is ever
    // composed, so without this every `rememberSaveable` below a route — which
    // source chip 圖鑑 was on, what 搜尋 had typed — was thrown away the moment
    // a word was pushed over it, and back came to a fresh screen. A slot is
    // dropped once its entry leaves the stack, so a *new* 搜尋 opens empty.
    val routeState = rememberSaveableStateHolder()
    // Keyed by the route itself, not by its depth. The push transition composes
    // the outgoing and incoming screens at the same time, and a key built from
    // `nav.current` would hand both of them the same one — which is not a
    // wrong-state bug but a crash, since a state holder rejects a duplicate.
    val liveKeys = nav.entries.map { "route:$it" }
    var heldKeys by remember { mutableStateOf(emptySet<String>()) }
    // The pager's four pages are always alive, whatever the stack says: they
    // are composed side by side, and dropping the neighbour's state the moment
    // it leaves the stack would empty the page a finger is dragging onto.
    val tabKeys = remember { TabShell.tabs.map { "tab:$it" }.toSet() }
    LaunchedEffect(liveKeys) {
        (heldKeys - liveKeys.toSet() - tabKeys).forEach(routeState::removeState)
        heldKeys = liveKeys.toSet()
    }

    BackHandler(enabled = nav.canGoBack || showSpike) {
        if (showSpike) showSpike = false else nav = nav.pop()
    }

    if (showSpike) {
        FuriganaSpikeScreen(catalog = app.catalogReading)
        return
    }

    if (showCheckIn && nav.current == AppRoute.Today) {
        CheckInSheet(
            store = app.checkInStore,
            fallbackStreak = progress.streak,
            onUpgrade = {
                showCheckIn = false
                openMembership()
            },
            // Take the hero's own advice: the one button's `call`, not a
            // second copy of the review-first rule.
            onStudy = {
                showCheckIn = false
                when (val call = TodayDecisions(todayInputs).call) {
                    is TodayCall.Study -> nav = nav.push(StudyRoute(call.mode))
                    TodayCall.PickThemes -> nav = nav.push(AccountRoute.StudyThemes)
                    TodayCall.Unknown -> nav = nav.push(StudyRoute(StudyMode.Review))
                    is TodayCall.Blocked -> Unit
                }
            },
            onDismiss = { showCheckIn = false },
        )
    }

    // The study flows own the whole window — no tab bar, no account row: a
    // session that can be left by tapping a tab is a session that gets left by
    // accident.
    (nav.current as? StudyRoute)?.let { route ->
        StudySessionHost(route, shell)
        return
    }

    // Every return to 今日, not only at launch: a session that just wrote three
    // ratings has changed every number on that screen. And every return of the
    // network: a tab opened offline read nothing, and without this showed
    // nothing until the user left it and came back.
    val reconnects by app.connectivity.reconnects.collectAsStateWithLifecycle()
    LaunchedEffect(nav.current, reconnects) {
        // 設定 and 學習主題 stay inert until the account's settings are here.
        // A launch whose read failed asks nowhere else, so arriving asks again.
        val editsSettings = nav.current == AccountRoute.Settings || nav.current == AccountRoute.StudyThemes
        if (editsSettings && !app.settingsStore.loaded.value) {
            app.settingsStore.load(deviceLanguage)
        }
        if (nav.current == AppRoute.Today) {
            today.refresh()
            // 學習主題's gate and the numbers built on it. Here rather than only
            // on 我的, which a new account may never open.
            account.refreshEntitlement()
            // 主題進度 and the streak chip read it now, and both move without
            // the user doing anything here — a session elsewhere, or midnight.
            app.progressStore.load(direction)
            // The chip's dot. Only when stale: tab swaps should not each ask.
            app.checkInStore.loadRewardIfStale()
        }
        if (nav.current == AppRoute.Me) {
            account.refresh()
            account.loadWeakWords()
            // Unlike a score, the streak and the heatmap move on their own —
            // 「目前連勝 5 天」 left over from yesterday is a claim about today
            // that nobody made. So this one asks again on arrival rather than
            // holding a copy that ages silently.
            app.progressStore.load(direction)
        }
        // 物見 needs it too: the 自製圖鑑 entry is gated on remaining slots, and
        // an entitlement that only loads on 我的 would hide the entry from
        // anyone who never opened that tab.
        if (nav.current == AppRoute.Community) account.refresh()
        // A purchase made on another device shows the moment the page opens.
        if (nav.current == AccountRoute.Membership || nav.current == AccountRoute.StudyThemes) {
            account.refreshEntitlement()
        }
    }
    LaunchedEffect(myUid) { if (myUid != null) community.loadMe(myUid) }

    // 拍照 handed a card to 生成佇列: close onto 圖鑑's 我做的, where its tile is.
    val onCaptureQueued = {
        val landing = TabShell.afterCaptureQueued(nav)
        nav = landing.nav
        cardsSourceRequest = landing.cardsSource
    }

    // The first-run tour, on the first launch only. The marked composables
    // below report where they are; the overlay reads that and nothing else in
    // the shell knows the tour exists.
    val anchors = rememberTourAnchors()
    val tour = remember(direction) { FeatureTourFlow.forDirection(direction.hasScenes) }
    var tourStep by remember { mutableStateOf<Int?>(null) }
    // Mirrors of the store, which is a preference and not state the shell
    // recomposes for.
    var tabTipsArmed by remember { mutableStateOf(app.onboarding.tabTipsArmed) }
    var tabTipsSeen by remember { mutableStateOf(app.onboarding.tabTipsSeen) }
    fun tabTipShows(tab: AppRoute.Tab?): Boolean = tab != null && TabTip.shows(
        tab = tab,
        armed = tabTipsArmed,
        seen = tabTipsSeen,
        atRoot = nav.current is AppRoute.Tab,
        tourRunning = tourStep != null,
        studyFocusActive = !TabShell.tabBarVisible(nav),
    )
    fun dismissTabTip(tab: AppRoute.Tab) {
        val key = TabTip.key(tab) ?: return
        tabTipsSeen = tabTipsSeen + key
        app.onboarding.tabTipsSeen = tabTipsSeen
    }
    // Shown and walked away from counts as read: it is one line, and a tip that
    // waits for a tap on every visit turns into a nag.
    var tipShownOn by remember { mutableStateOf<AppRoute.Tab?>(null) }
    LaunchedEffect(nav.tab) {
        tipShownOn?.let { if (it != nav.tab) dismissTabTip(it) }
        tipShownOn = nav.tab?.takeIf { tabTipShows(it) }
    }
    var tourCrossing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // After the launch beat, not before: everything `mayStart` asks about
        // can change while the shell settles.
        delay(TOUR_START_DELAY)
        val may = FeatureTourFlow.mayStart(
            tourDone = app.onboarding.tourDone,
            studyFocusActive = !TabShell.tabBarVisible(nav),
            // Nothing hands this app a deep link yet. Named rather than
            // dropped, so the day something does, the rule is already here.
            hasPendingLink = false,
            alreadyRunning = tourStep != null,
        )
        if (may) tourStep = 0
    }

    Box(Modifier.fillMaxSize()) {
    ProvideTourAnchors(anchors) {
    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        Spacer(Modifier.height(insets.calculateTopPadding()))

        // The four tab roots live in the pager, not in the page hosts below.
        // A page that only exists while it is the current route cannot be
        // swiped to — the neighbour has to be composable before the finger
        // arrives at it.
        val tabContent: @Composable (AppRoute.Tab) -> Unit = { tab ->
            when (tab) {
                AppRoute.Today -> TodayColumn(
                    inputs = todayInputs,
                    identity = identity,
                    onStudy = { mode -> shell.push(StudyRoute(mode)) },
                    onSearch = { shell.push(AtlasRoute.Search) },
                    themeName = { id -> catalog.categories.firstOrNull { it.id == id }?.let { CategoryShelf.title(it, uiLang) } },
                    onOpenStats = { shell.push(AccountRoute.ProgressStats) },
                    onOpenStatement = { shell.push(AssistantRoute.CreditStatement) },
                    onUnlock = openMembership,
                    onOpenFeature = { feature ->
                        shell.push(
                            when (feature) {
                                AssistantHomeFeature.Lookup -> AssistantRoute.Vocabulary
                                AssistantHomeFeature.Grammar -> AssistantRoute.Grammar
                                AssistantHomeFeature.Story -> AssistantRoute.Story
                                AssistantHomeFeature.TopicChat -> AssistantRoute.TopicChat(TopicChatEntry.Choose)
                            },
                        )
                    },
                    mascotPaused = nav.current != AppRoute.Today,
                    // So 情境故事 opens on its choices and AI 查詞／AI 語法 on
                    // their prices. It hung off 助手's page; the ways in moved
                    // here, so the warm-up did too.
                    prefetchAssistant = { prefetchAssistant(shell) },
                    completion = completion,
                    streak = progress.streak?.current ?: 0,
                    onStreak = { showCheckIn = true },
                    streakBadge = CheckInDecision.chipBadge(
                        checkInState.reward(fallbackStudiedToday = (progress.streak?.todayCount ?: 0) > 0),
                    ),
                    trialPoints = checkInState.trialPoints,
                    onOpenStudyThemes = { shell.push(AccountRoute.StudyThemes) },
                    sceneCard = sceneTodayCard(shell),
                    onSpike = { showSpike = true },
                    onRefresh = { refresh(LearningRefreshCause.PulledToday.targets) },
                )

                AppRoute.Atlas -> AtlasCardsScreen(
                    sourceRequest = cardsSourceRequest,
                    onSourceRequestHandled = { cardsSourceRequest = null },
                    words = catalog.words,
                    onSearch = { shell.push(AtlasRoute.Search) },
                    personal = personal,
                    scores = scores,
                    loading = !catalog.loaded,
                    bottomPadding = 0.dp,
                    shelves = catalog.shelves,
                    seenAndTotal = progress::seenAndTotal,
                    uiLang = uiLang,
                    onOpenTheme = { id ->
                        val shelf = catalog.shelves.first { it.category.id == id }
                        shell.push(AtlasRoute.Shelf(id, CategoryShelf.title(shelf.category, uiLang)))
                    },
                    onRetry = { scope.launch { app.catalog.load(direction, force = true) } },
                    onOpen = shell::openCard,
                    onOpenManage = { shell.push(ManageRoute.Home) },
                    session = direction.targetLanguage,
                    showChinese = settings.showZh,
                    onBookmark = app.cardsSourceStore::toggle,
                    captureJobs = captureJobs,
                    onRetryCapture = { app.captureQueue.retry(it) },
                    studyable = studyable,
                )

                AppRoute.Community -> CommunityScreen(
                    explore = communityExplore,
                    saved = communitySaved,
                    me = communityMe,
                    language = direction.targetLanguage,
                    onShowSaved = community::loadSaved,
                    onRetry = community::load,
                    onOpenCollection = { shell.push(CommunityRoute.Collection(it)) },
                    onOpenMyPage = { shell.push(CommunityRoute.Author(it)) },
                    onSearch = { shell.push(CommunityRoute.Search) },
                    // Not the learning policy: 物見 is other people's shelves,
                    // and none of the numbers this table is about are on it.
                    onRefresh = { community.reload(withSaved = true) },
                    exploreHeader = { TravelEntry(shell) },
                )

                AppRoute.Me -> AccountScreen(
                    state = accountState,
                    // The same readout 今日's 主題進度 prints.
                    completion = completion,
                    progress = progress,
                    spread = remember(scores) { MasteryDistribution.of(scores.byId) },
                    masteryLoaded = scores.loaded,
                    categories = catalog.categories,
                    studyCategories = settings.studyCategories,
                    bottomPadding = 0.dp,
                    onOpenSettings = { shell.push(AccountRoute.Settings) },
                    showChinese = settings.showZh,
                    onOpenWord = shell::openCard,
                    onRefresh = {
                        refresh(LearningRefreshCause.PulledMe.targets)
                        // 我's own payload — the weakest words and the plan —
                        // which no learning store holds.
                        account.reload()
                    },
                    onOpenMembership = openMembership,
                    wordLists = { MeWordLists(shell) },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            // Pushed screens arrive from the right and leave to it, with the
            // one underneath sliding a third of the way — the parallax every
            // stack on both platforms uses to say which way you are going.
            // Before this a push was a cut, and a cut gives no direction: 返回
            // and 前進 looked the same.
            //
            // **All four tabs are one layer here.** The pager is the thing that
            // animates between them, and wrapping each tab as its own target
            // would put a push transition on top of a swipe.
            val depth = nav.entries.size
            var lastDepth by remember { mutableIntStateOf(depth) }
            val forward = depth >= lastDepth
            LaunchedEffect(depth) { lastDepth = depth }
            val layer: ShellLayer = when (val current = nav.current) {
                is AppRoute.Tab -> ShellLayer.Tabs
                is AppRoute.Page -> ShellLayer.Pushed(current)
            }
            AnimatedContent(
                targetState = layer,
                transitionSpec = {
                    // 350ms, not D2. iOS does not write this one down — it
                    // inherits `NavigationStack`'s push, and UIKit's push is
                    // visibly slower than the 220ms enter/exit step. Matching
                    // the token here made every push on Android feel like a
                    // different app from the same push on iOS.
                    val spec = tween<IntOffset>(PUSH_MS, easing = TujiMotion.EaseOut)
                    if (forward) {
                        slideInHorizontally(spec) { it } togetherWith
                            slideOutHorizontally(spec) { -it / 3 }
                    } else {
                        slideInHorizontally(spec) { -it / 3 } togetherWith
                            slideOutHorizontally(spec) { it }
                    }.using(
                        // The tab bar leaves and returns in the same beat, and
                        // an animated size lagged behind the box it sits in —
                        // taller than its room, the pages were centred in it
                        // and the whole page rode up and down during the slide.
                        SizeTransform { _, _ -> snap() },
                    ).apply {
                        // The page on top is always the deeper one: the one
                        // arriving on a push, the one leaving on a pop. The
                        // default puts whatever arrives on top, so a pop hid
                        // the leaving page under the one it was uncovering.
                        targetContentZIndex = if (forward) 1f else -1f
                    }
                },
                label = "route",
            ) { shown ->
            // Every page paints its own paper. Most screens draw nothing
            // behind their content, and while two slide past each other the
            // one underneath showed through the one on top — the 殘影.
            Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
            // A pushed screen's way back is an arrow on the page margin, and its
            // name — where it has one — is the page's own large title below it.
            // The 「← 標題」 text link this replaces said the name twice, once in
            // the link and once in the title under it.
            //
            // Not at a tab root, where the bar below already reaches every tab in
            // one tap. Not over 主題頁's hero, which bleeds and floats its own
            // arrow, and not on 搜尋, whose field carries its own 取消.
            //
            // Part of the page, so it slides with it: drawn above the pages it
            // vanished the instant 返回 was pressed and the leaving page jumped
            // up into the gap.
            if (shown is ShellLayer.Pushed && hasBackBar(shown.page)) {
                val capture = shown.page is ManageRoute.Capture
                TujiNavBar(
                    // A page on its way out keeps its arrow, but only the page
                    // on top may pop.
                    onLeading = { shell.from(shown.page).pop() },
                    leading = if (capture) TujiNavLeading.Close else TujiNavLeading.Back,
                    leadingLabel = stringResource(if (capture) R.string.nav_close else R.string.atlas_back),
                    title = if (capture) stringResource(R.string.capture_add_title) else null,
                )
            }
            Box(Modifier.weight(1f)) {
            when (shown) {
                ShellLayer.Tabs -> {
                    val atTab = nav.tab ?: AppRoute.Today
                    val pager = rememberPagerState(
                        initialPage = TabShell.tabs.indexOf(atTab).coerceAtLeast(0),
                        pageCount = { TabShell.tabs.size },
                    )
                    // Both ways, and each guarded against the other: the bar moves
                    // the pager, a settled swipe moves the stack, and neither may
                    // answer its own move or the two chase each other across the
                    // screen.
                    LaunchedEffect(atTab) {
                        val target = TabShell.tabs.indexOf(atTab)
                        if (target >= 0 && target != pager.currentPage) pager.animateScrollToPage(target)
                    }
                    LaunchedEffect(pager) {
                        snapshotFlow { pager.settledPage }.collect { page ->
                            val tab = TabShell.tabs.getOrNull(page) ?: return@collect
                            if (nav.current != tab) nav = nav.select(tab)
                        }
                    }
                    HorizontalPager(
                        state = pager,
                        userScrollEnabled = TabShell.swipeEnabled(nav),
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        // Keyed by the tab rather than by its place in the stack,
                        // so a scroll position survives being swiped past — which
                        // is the whole reason the four are side by side.
                        routeState.SaveableStateProvider("tab:${TabShell.tabs[page]}") {
                            tabContent(TabShell.tabs[page])
                        }
                    }
                }
                is ShellLayer.Pushed -> routeState.SaveableStateProvider("route:${shown.page}") {
                    PageHost(shown.page, shell, onCaptureQueued)
                }
            }
            }
            }
            }
        }

        // In the column, not over the page: it sits on the bar and pushes the
        // page up rather than covering the bottom of it.
        val tipTab = nav.tab?.takeIf { tabTipShows(it) }
        AnimatedVisibility(
            visible = tipTab != null,
            enter = fadeIn(tween(TujiMotion.D2)),
            exit = fadeOut(tween(TujiMotion.D2)),
        ) {
            val shown = tipTab ?: return@AnimatedVisibility
            TabTip.text(shown)?.let { TabTipBubble(it, onDismiss = { dismissTabTip(shown) }) }
        }

        // The bar leaves downwards, the way it would if it were a thing on
        // the screen rather than a thing being deleted from it.
        AnimatedVisibility(
            visible = TabShell.tabBarVisible(nav),
            enter = slideInVertically(tween(TujiMotion.D2, easing = TujiMotion.EaseOut)) { it },
            exit = slideOutVertically(tween(TujiMotion.D2, easing = TujiMotion.EaseOut)) { it },
        ) {
            TujiTabBar(
                selected = nav.tab,
                bottomInset = insets.calculateBottomPadding(),
                onSelect = { nav = nav.select(it) },
                onCapture = { shell.push(ManageRoute.Capture()) },
            )
        }
        if (!TabShell.tabBarVisible(nav)) {
            Spacer(Modifier.height(insets.calculateBottomPadding()))
        }
    }
    }

    AppUpdatePrompt(
        updates = app.appUpdates,
        play = app.playUpdates,
        studyFocusActive = !TabShell.tabBarVisible(nav),
        tourRunning = tourStep != null,
    )

    tourStep?.let { index ->
        val step = tour.steps[index]
        val end = { finished: Boolean ->
            app.onboarding.tourDone = true
            app.onboarding.tabTipsArmed = true
            tabTipsArmed = true
            tourStep = null
            // Finishing opens 學新字, which is what the closing card offers;
            // skipping stays put.
            if (finished) {
                nav = nav.select(FeatureTourFlow.tabAfterFinishing)
                    .push(tour.routeAfterFinishing)
            }
        }
        FeatureTourOverlay(
            step = step,
            copy = TourCopy.of(step),
            anchors = anchors,
            transitioning = tourCrossing,
            isLast = index == tour.steps.lastIndex,
            onSkip = { end(false) },
            onNext = {
                when (val next = tour.advance(index, nav.tab ?: AppRoute.Today)) {
                    is TourAdvance.Show -> tourStep = next.index
                    is TourAdvance.CrossTab -> {
                        // Hide the hole while the pager slides, so nothing is
                        // dragged across two pages, then show it where it is.
                        tourCrossing = true
                        nav = nav.select(next.to)
                        tourStep = next.index
                        scope.launch {
                            delay(TOUR_CROSS_SETTLE)
                            tourCrossing = false
                        }
                    }
                    TourAdvance.Finish -> end(true)
                }
            },
        )
    }
    }
}

/**
 * Every pushed page, by the feature that owns it. One arm per family and no
 * `else`: a page with no host does not compile — it used to compile to a
 * blank screen.
 */
@Composable
private fun PageHost(page: AppRoute.Page, shell: ShellContext, onCaptureQueued: () -> Unit) {
    when (page) {
        is AtlasRoute -> AtlasHost(page, shell)
        is ManageRoute -> ManageHost(page, shell, onCaptureQueued)
        is CommunityRoute -> CommunityHost(page, shell)
        is TravelRoute -> TravelHost(page, shell)
        is SceneRoute -> SceneHost(page, shell)
        is WordListRoute -> WordListHost(page, shell)
        is AssistantRoute -> AssistantHost(page, shell)
        is AccountRoute -> AccountHost(page, shell)
        // Drawn over the whole window before the shell gets this far; named so
        // the `when` stays whole.
        is StudyRoute -> StudySessionHost(page, shell)
    }
}

/** Long enough for the shell to have laid out and measured its anchors. */
private const val TOUR_START_DELAY = 900L

/** One D2 page slide, plus a frame for the anchors to settle where they land. */
private const val TOUR_CROSS_SETTLE = 320L

/**
 * Whether the shell draws a back bar over [page]. The rest draw their own way
 * back: 單字詳情's picture is the first thing on the page, with 返回 and 書籤
 * floating over it; a 合集's cover bleeds and floats its own arrow; 作者主頁's
 * bar carries 更多; 搜尋's field carries 取消.
 */
private fun hasBackBar(page: AppRoute.Page): Boolean = when (page) {
    is CommunityRoute.Item, is ManageRoute.Card, is ManageRoute.Capture,
    AccountRoute.Settings, AccountRoute.StudyThemes, AccountRoute.BlockedAuthors, AccountRoute.Membership -> true
    else -> false
}

/** What the push transition animates between: the pager, or one pushed page. */
private sealed interface ShellLayer {
    /**
     * The four tabs, as one thing to animate to and from. They are already a
     * pager, and a pager is its own transition; naming each tab as its own
     * target would slide a whole shell sideways underneath a swipe that is
     * already sliding.
     */
    data object Tabs : ShellLayer

    data class Pushed(val page: AppRoute.Page) : ShellLayer
}

/** 今日, plus the debug-only door to the font spike. */
@Composable
private fun TodayColumn(
    inputs: TodayInputs,
    identity: String?,
    onStudy: (StudyMode) -> Unit,
    onSearch: () -> Unit,
    themeName: (String) -> String?,
    onOpenStats: () -> Unit,
    onOpenStatement: () -> Unit,
    onUnlock: () -> Unit,
    onOpenFeature: (AssistantHomeFeature) -> Unit,
    mascotPaused: Boolean,
    prefetchAssistant: suspend () -> Unit,
    completion: CompletionReadout,
    streak: Int,
    onStreak: () -> Unit,
    streakBadge: Boolean,
    trialPoints: Int?,
    onOpenStudyThemes: () -> Unit,
    onSpike: () -> Unit,
    onRefresh: suspend () -> Unit,
    sceneCard: (@Composable (Modifier) -> Unit)? = null,
) {
    // `docs/SPIKE-FURIGANA.md` promises anyone who touches the fonts can re-run
    // it, so the door stays in debug builds — as a long press on the page, not
    // a line of text. A visible 「字型 spike（debug）」 under 今日 made every
    // debug screenshot differ from iOS, and debug builds are what gets compared.
    val spikeDoor = if (BuildConfig.DEBUG) {
        Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onSpike() }) }
    } else {
        Modifier
    }
    LaunchedEffect(Unit) { prefetchAssistant() }
    TujiPullToRefresh(onRefresh = onRefresh) {
    Column(Modifier.fillMaxSize().then(spikeDoor).verticalScroll(rememberScrollState())) {
        TodayScreen(
            inputs = inputs,
            name = identity,
            bottomPadding = 0.dp,
            onStudy = onStudy,
            onSearch = onSearch,
            themeName = themeName,
            onOpenStats = onOpenStats,
            onOpenStatement = onOpenStatement,
            onUnlock = onUnlock,
            onOpenFeature = onOpenFeature,
            mascotPaused = mascotPaused,
            completion = completion,
            streak = streak,
            onStreak = onStreak,
            streakBadge = streakBadge,
            onOpenStudyThemes = onOpenStudyThemes,
            trialPoints = trialPoints,
            sceneCard = sceneCard,
        )
    }
    }
}

/** iOS's `OfflineBanner`: an alert-red pill under the status bar while the device is offline. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .statusBarsPadding()
            .padding(top = TujiSpace.S2)
            .shadow(6.dp, RectangleShape, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
            .background(TujiColor.Alert)
            .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TujiGlyph.WifiOff(size = 16.dp, tint = TujiColor.Paper)
        Text(stringResource(R.string.offline_banner), style = TujiType.bodySmStrong, color = TujiColor.Paper)
    }
}

/** iOS: `LaunchTransitionPolicy.opacityDuration(reduceMotion:)`. */
private const val LAUNCH_FADE_MS = 180

/** iOS: `.animation(.easeInOut(duration: 0.25), value: network.isConnected)`. */
private const val BANNER_MS = 250

/** UIKit's push, which is what iOS's `NavigationStack` uses. */
private const val PUSH_MS = 350
