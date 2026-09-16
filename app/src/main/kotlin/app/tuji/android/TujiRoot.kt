package app.tuji.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import app.tuji.android.core.design.TujiGlyph
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.tuji.android.auth.WelcomeScreen
import app.tuji.android.core.auth.AuthState
import app.tuji.android.core.model.LaunchAccountState
import app.tuji.android.core.model.LaunchContext
import app.tuji.android.core.design.TujiFace
import app.tuji.android.core.design.TujiTheme
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.gloss.GlossBookmarks
import app.tuji.android.core.model.LaunchDestination
import app.tuji.android.core.model.LaunchRouting
import app.tuji.android.settings.SettingsBusy
import app.tuji.android.settings.SettingsReadiness
import app.tuji.android.settings.SettingsScreen
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.study.MasteryDistribution
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.onboarding.LearningDirectionScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import app.tuji.android.atlas.AtlasSearchScreen
import app.tuji.android.atlas.SearchViewModel
import app.tuji.android.atlas.AtlasCardsScreen
import app.tuji.android.atlas.AtlasThemeScreen
import app.tuji.android.atlas.AtlasThemesScreen
import app.tuji.android.atlas.WordDetailScreen
import app.tuji.android.atlas.WordDetailViewModel
import app.tuji.android.account.AccountScreen
import app.tuji.android.account.AccountViewModel
import app.tuji.android.capture.CaptureScreen
import app.tuji.android.capture.CaptureViewModel
import app.tuji.android.community.AuthorScreen
import app.tuji.android.community.CommunityScreen
import app.tuji.android.community.CollectionScreen
import app.tuji.android.community.CommunityViewModel
import app.tuji.android.community.CollectionDetailViewModel
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.community.PublicItemScreen
import app.tuji.android.profile.BlockedAuthorsScreen
import app.tuji.android.manage.AtlasManageScreen
import app.tuji.android.manage.AtlasManageViewModel
import app.tuji.android.manage.CollectionEditScreen
import app.tuji.android.manage.CollectionEditViewModel
import app.tuji.android.manage.MyCollectionsViewModel
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.manage.ManageCardScreen
import app.tuji.android.profile.EditProfileScreen
import app.tuji.android.profile.EditProfileViewModel
import app.tuji.android.community.PublicItemViewModel
import app.tuji.android.community.AuthorViewModel
import app.tuji.android.core.community.ViewerRelationship
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.catalog.StudyThemes
import app.tuji.android.core.study.CompletionReadout
import app.tuji.android.core.study.StudyQuotas
import app.tuji.android.core.study.ThemeStatus
import app.tuji.android.settings.StudyThemesScreen
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavLeading
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.BuildConfig
import app.tuji.android.spike.FuriganaSpikeScreen
import app.tuji.android.study.AnswerDrainWorker
import app.tuji.android.study.NewFlowScreen
import app.tuji.android.study.NewFlowViewModel
import app.tuji.android.today.TodayScreen
import app.tuji.android.today.TodayViewModel
import app.tuji.android.study.isOnline
import app.tuji.android.study.ReviewScreen
import app.tuji.android.study.ReviewViewModel
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.Word
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

    val online by app.connectivity.online.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
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
        // Over every screen but the splash, which says nothing that needs the
        // network yet. Requests explain their own failures; this says why.
        if (destination != LaunchDestination.Splash && online == false) {
            OfflineBanner(Modifier.align(Alignment.TopCenter))
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
        Text("Tuji", style = TujiType.h1, color = TujiColor.Ink)
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
 * The signed-in app: the tabs, one back stack, and the study flows on top.
 *
 * The stack is [NavStack] and nothing else — the hardware back button and 返回
 * pop the same list, so the two can never disagree about where "back" is.
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

    // 收藏 happens in 物見 and lands on a shelf 圖鑑 draws. A tick rather than a
    // call from inside the view model, for the same reason `refreshTick` is one:
    // the effect that reloads has to be the thing the composition owns.
    var savedTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(savedTick) {
        if (savedTick == 0) return@LaunchedEffect
        app.cardsSourceStore.load(uiLang, direction, force = true)
    }
    val progress by app.progressStore.snapshot.collectAsStateWithLifecycle()
    val session by app.auth.session.collectAsStateWithLifecycle()
    val isGuest = session.state is AuthState.Guest
    val settingsLoaded by app.settingsStore.loaded.collectAsStateWithLifecycle()
    val settingsLoadFailed by app.settingsStore.loadFailed.collectAsStateWithLifecycle()
    val settingsReadiness = SettingsReadiness.of(isGuest, settingsLoaded, settingsLoadFailed)

    // Every word the 學習主題 selection can reach. 自定義 and 物見 are themes
    // with nothing in the catalogue — their words are the user's own and
    // taken-in cards — so a strip, a theme page or a 完成度 built from the
    // catalogue alone leaves both out while showing them ticked in 設定.
    val studyWords = remember(catalog.words, personal.mine, personal.taken) {
        StudyThemes.words(catalog.words, personal.mine, personal.taken)
    }
    val studyShelves = remember(catalog.categories, studyWords) {
        CategoryShelf.shelves(catalog.categories, studyWords)
    }
    // 完成度, once, for 今日's 主題進度 and 我's card: two screens printing two
    // numbers for one account read as a server bug for a week.
    val completion = remember(isGuest, settingsLoaded, settings.studyCategories, progress.categories, studyWords) {
        CompletionReadout(
            CompletionReadout.Inputs.from(
                isGuest = isGuest,
                settingsLoaded = settingsLoaded,
                studyCategories = settings.studyCategories,
                progress = progress.categories,
                words = studyWords,
            ),
        )
    }
    val themeStatus: (String) -> ThemeStatus = { id ->
        ThemeStatus.of(
            wordIds = CategoryShelf.words(id, studyWords).map { it.id },
            masteryScore = scores::score,
            seenAndTotal = progress.seenAndTotal(id),
        )
    }

    // Bumped as a study flow closes. A signal here rather than a call at the
    // close site, because the fetch has to outlive the composable that asked
    // for it — and that composable is the one being popped.
    //
    // **A counter, not a flag.** The first version was a `Boolean` the effect
    // reset on entry, which changed the very key it was launched on: Compose
    // cancelled the effect mid-flight and restarted it with the new key, which
    // returned immediately. The refresh never ran once. A key that only ever
    // moves forward, and nothing inside the effect touching it, is the shape
    // that cannot do that.
    var settingsBusy by remember { mutableStateOf(SettingsBusy()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0) return@LaunchedEffect
        app.masteryStore.load(direction, force = true)
        app.progressStore.load(direction)
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

    // 圖鑑管理's shelf, above both screens that draw it, so a card deleted on
    // its own page is already gone from the list behind.
    val manage = remember(direction) {
        AtlasManageViewModel(
            shelf = app.atlas,
            language = direction.targetLanguage,
            // A card went, or came off 物見: 我做的, the study queue and the
            // counts on 今日 all include it.
            onChanged = {
                savedTick++
                refreshTick++
            },
        )
    }
    val manageState by manage.state.collectAsStateWithLifecycle()
    val myCollections = remember(direction) {
        MyCollectionsViewModel(
            authoring = app.atlas,
            language = direction.targetLanguage,
            // A public collection came or went: 物見's shelves and the
            // author's own row count it.
            onChanged = { community.load() },
        )
    }
    val myCollectionsState by myCollections.state.collectAsStateWithLifecycle()
    val communityReported by community.reported.collectAsStateWithLifecycle()

    val account = remember {
        AccountViewModel(accounts = app.atlas, entitlements = app.atlas, weakWords = app.atlas)
    }
    val accountState by account.state.collectAsStateWithLifecycle()

    val today = remember(direction) {
        TodayViewModel(
            stats = app.study,
            direction = direction,
            isGuest = { app.auth.session.value.state is AuthState.Guest },
        )
    }
    val todayInputs by today.inputs.collectAsStateWithLifecycle()

    // Held above the routes rather than inside 搜尋's branch, so opening a
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

    var nav by remember { mutableStateOf(NavStack()) }
    var showSpike by remember { mutableStateOf(false) }

    // A 已收進 card belongs to somebody else, and its page has an author, a
    // 取消收藏 and a 檢舉 that the dictionary's entry has none of. The id says
    // which — for 圖鑑's grid and for a theme page alike.
    val openCard: (String) -> Unit = { id ->
        nav = nav.push(CardsSourceRules.savedSlug(id)?.let { AppRoute.PublicItem(it) } ?: AppRoute.Word(id))
    }

    // One saved-state slot per stack entry. Only the current entry is ever
    // composed, so without this every `rememberSaveable` below a route — which
    // source chip 圖鑑 was on, what 搜尋 had typed — was thrown away the moment
    // a word was pushed over it, and back came to a fresh screen. A slot is
    // dropped once its entry leaves the stack, so a *new* 搜尋 opens empty.
    val routeState = rememberSaveableStateHolder()
    val liveKeys = nav.entries.mapIndexed { index, route -> "$index:$route" }
    var heldKeys by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(liveKeys) {
        (heldKeys - liveKeys.toSet()).forEach(routeState::removeState)
        heldKeys = liveKeys.toSet()
    }

    BackHandler(enabled = nav.canGoBack || showSpike) {
        if (showSpike) showSpike = false else nav = nav.pop()
    }

    if (showSpike) {
        FuriganaSpikeScreen(catalog = app.catalogReading)
        return
    }

    // The study flows own the whole screen — no tab bar, no account row: a
    // session that can be left by tapping a tab is a session that gets left by
    // accident.
    when (nav.current) {
        AppRoute.Review -> {
            val haptics = rememberTujiHaptics()
            val vm = remember {
                ReviewViewModel(
                    queues = app.study,
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
                ).also { it.load(StudyMode.Review) }
            }
            ReviewScreen(
                vm = vm,
                showChinese = settings.showZh,
                bookmarked = { id -> id in personal.bookmarked },
                onBookmark = app.cardsSourceStore::toggle,
                onClose = {
                    // A session just moved the scores every badge in 圖鑑
                    // draws. Without this the user finishes twenty cards,
                    // opens 圖鑑, and sees the tiers they had before they
                    // started — on the screen they went to in order to see
                    // the change.
                    refreshTick++
                    nav = nav.pop()
                },
            )
            return
        }
        AppRoute.LearnNew -> {
            val haptics = rememberTujiHaptics()
            val vm = remember {
                NewFlowViewModel(
                    queues = app.study,
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
                    // The goal from 設定, tapered by the backlog, from the
                    // themes 設定 picked — the same numbers 今日 printed on
                    // the button that opened this.
                    it.load(
                        StudyQuotas.newQueue(
                            goal = settings.dailyGoal,
                            due = todayInputs.stats?.due ?: 0,
                            categories = settings.studyCategories,
                        ),
                    )
                }
            }
            NewFlowScreen(
                vm = vm,
                showChinese = settings.showZh,
                session = direction.targetLanguage,
                uiLang = uiLang,
                speech = app.speech,
                accent = settings.accent,
                onClose = {
                    refreshTick++
                    nav = nav.pop()
                },
            )
            return
        }
        else -> Unit
    }

    // Every return to 今日, not only at launch: a session that just wrote three
    // ratings has changed every number on that screen. And every return of the
    // network: a tab opened offline read nothing, and without this showed
    // nothing until the user left it and came back.
    val reconnects by app.connectivity.reconnects.collectAsStateWithLifecycle()
    LaunchedEffect(nav.current, reconnects) {
        // 設定 and 學習主題 stay inert until the account's settings are here.
        // A launch whose read failed asks nowhere else, so arriving asks again.
        val editsSettings = nav.current == AppRoute.Settings || nav.current == AppRoute.StudyThemes
        if (editsSettings && !isGuest && !app.settingsStore.loaded.value) {
            app.settingsStore.load(deviceLanguage)
        }
        if (nav.current == AppRoute.Today) {
            today.refresh()
            // 主題進度 and the streak chip read it now, and both move without
            // the user doing anything here — a session elsewhere, or midnight.
            app.progressStore.load(direction)
        }
        if (nav.current == AppRoute.Me) {
            account.refresh()
            if (!isGuest) account.loadWeakWords()
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
    }
    // The row at the top of 物見 is this account's public page, and it needs
    // the UID to find it — which arrives with 我的's account read.
    val myUid = accountState.me?.username
    LaunchedEffect(myUid) { if (!isGuest && myUid != null) community.loadMe(myUid) }

    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        Spacer(Modifier.height(insets.calculateTopPadding()))

        // A pushed screen's way back is an arrow on the page margin, and its
        // name — where it has one — is the page's own large title below it.
        // The 「← 標題」 text link this replaces said the name twice, once in
        // the link and once in the title under it.
        //
        // Not at a tab root, where the bar below already reaches every tab in
        // one tap. Not over 主題頁's hero, which bleeds and floats its own
        // arrow, and not on 搜尋, whose field carries its own 取消.
        if (nav.canGoBack && hasBackBar(nav.current)) {
            TujiNavBar(
                onLeading = { nav = nav.pop() },
                leading = if (nav.current == AppRoute.Capture) TujiNavLeading.Close else TujiNavLeading.Back,
                leadingLabel = stringResource(
                    if (nav.current == AppRoute.Capture) R.string.nav_close else R.string.atlas_back,
                ),
            )
        }

        Box(Modifier.weight(1f)) {
            routeState.SaveableStateProvider(liveKeys.last()) {
            when (val route = nav.current) {
                AppRoute.Today -> TodayColumn(
                    inputs = todayInputs,
                    identity = identity,
                    onReview = { nav = nav.push(AppRoute.Review) },
                    onLearnNew = { nav = nav.push(AppRoute.LearnNew) },
                    onSearch = { nav = nav.push(AppRoute.Search) },
                    onCreateAccount = { app.auth.exitGuestMode() },
                    completion = completion,
                    streak = progress.streak?.current ?: 0,
                    shelves = remember(studyShelves, settings.studyCategories, isGuest) {
                        StudyThemes.todayShelves(studyShelves, settings.studyCategories, isGuest)
                    },
                    themeStatus = themeStatus,
                    uiLang = uiLang,
                    // Pushed on 今天, as on iOS: the theme is a place you come
                    // back from to the strip you left, not a trip to 圖鑑.
                    onOpenShelf = { id ->
                        val shelf = studyShelves.first { it.category.id == id }
                        nav = nav.push(AppRoute.Shelf(id, CategoryShelf.title(shelf.category, uiLang)))
                    },
                    onOpenStudyThemes = { nav = nav.push(AppRoute.StudyThemes) },
                    onSpike = { showSpike = true },
                )

                AppRoute.Atlas -> AtlasCardsScreen(
                    words = catalog.words,
                    isGuest = isGuest,
                    onSearch = { nav = nav.push(AppRoute.Search) },
                    personal = personal,
                    scores = scores,
                    loading = !catalog.loaded,
                    bottomPadding = 0.dp,
                    onOpenThemes = { nav = nav.push(AppRoute.Themes) },
                    onOpen = openCard,
                    onOpenManage = if (isGuest) null else ({ nav = nav.push(AppRoute.AtlasManage) }),
                )

                AppRoute.AtlasManage -> {
                    LaunchedEffect(Unit) { manage.load() }
                    AtlasManageScreen(
                        state = manageState,
                        onBack = { nav = nav.pop() },
                        onRetry = manage::load,
                        onSelecting = manage::setSelecting,
                        onToggle = manage::toggle,
                        onOpen = { nav = nav.push(AppRoute.ManageCard(it)) },
                        onDelete = { ids -> manage.delete(ids) },
                        collections = myCollectionsState,
                        onLoadCollections = myCollections::load,
                        onCreateCollection = { title, description, onCreated -> myCollections.create(title, description) { onCreated() } },
                        onDismissCreateError = myCollections::dismissCreateError,
                        onOpenCollection = { nav = nav.push(AppRoute.CollectionEdit(it)) },
                    )
                }

                is AppRoute.CollectionEdit -> {
                    val vm = remember(route.collectionId) {
                        CollectionEditViewModel(
                            collectionId = route.collectionId,
                            authoring = app.atlas,
                            onChanged = {
                                myCollections.load()
                                community.load()
                            },
                        ).also { it.load() }
                    }
                    val editState by vm.state.collectAsStateWithLifecycle()
                    CollectionEditScreen(
                        state = editState,
                        deleteWarning = DeleteWarning.of(editState.review),
                        deleting = myCollectionsState.deleting,
                        onBack = { nav = nav.pop() },
                        onRetry = vm::load,
                        onTitle = vm::setTitle,
                        onDescription = vm::setDescription,
                        onSaveMeta = vm::saveMeta,
                        onAvatar = vm::uploadAvatar,
                        onOpenPicker = vm::loadCandidates,
                        onAdd = vm::addMember,
                        onRemove = vm::removeMember,
                        onSubmit = vm::submit,
                        onWithdraw = vm::withdraw,
                        onDelete = {
                            myCollections.delete(
                                AtlasMyCollection(id = route.collectionId, reviewStatus = editState.collection?.reviewStatus),
                            ) { if (nav.current == route) nav = nav.pop() }
                        },
                    )
                }

                is AppRoute.ManageCard -> ManageCardScreen(
                    row = manageState.row(route.imageId),
                    withdrawing = manageState.withdrawing,
                    actionFailed = manageState.actionFailed,
                    onDelete = { manage.delete(setOf(route.imageId)) { if (nav.current == route) nav = nav.pop() } },
                    onWithdraw = manage::withdraw,
                )

                AppRoute.Settings -> SettingsScreen(
                    readiness = settingsReadiness,
                    onRetryLoad = { scope.launch { app.settingsStore.load(deviceLanguage) } },
                    settings = settings,
                    busy = settingsBusy,
                    bottomPadding = 0.dp,
                    onChange = { change -> app.settingsStore.update(change) },
                    onClearProgress = {
                        scope.launch {
                            settingsBusy = settingsBusy.copy(clearing = true)
                            runCatching { app.study.clearProgress() }
                            settingsBusy = settingsBusy.copy(clearing = false)
                            // Everything this screen just erased is drawn
                            // somewhere else, and none of those stores can
                            // know it happened.
                            app.masteryStore.load(direction, force = true)
                            app.progressStore.load(direction)
                            today.refresh()
                            nav = nav.pop()
                        }
                    },
                    onDeleteAccount = {
                        scope.launch {
                            settingsBusy = settingsBusy.copy(deleting = true)
                            runCatching { app.study.deleteAccount() }
                            settingsBusy = settingsBusy.copy(deleting = false)
                            // Signing out is what takes the user off this
                            // screen; the account it belonged to is gone.
                            app.auth.signOut()
                        }
                    },
                    onSignOut = { scope.launch { app.auth.signOut() } },
                    onOpenStudyThemes = { nav = nav.push(AppRoute.StudyThemes) },
                    onEditProfile = if (isGuest) null else ({ nav = nav.push(AppRoute.EditProfile) }),
                    onOpenBlocked = if (isGuest) null else ({ nav = nav.push(AppRoute.BlockedAuthors) }),
                )

                AppRoute.EditProfile -> {
                    val vm = remember {
                        EditProfileViewModel(
                            accounts = app.atlas,
                            profiles = app.atlas,
                            onSaved = { author ->
                                // The name and face other people see changed:
                                // the session's greeting, 我's row and 物見's
                                // own-page row all draw it.
                                app.auth.applyProfile(
                                    nickname = author.displayName?.takeIf { it != author.handle },
                                    avatar = author.avatar,
                                )
                                account.refresh()
                                community.loadMe(author.handle, force = true)
                                if (nav.current == AppRoute.EditProfile) nav = nav.pop()
                            },
                        ).also { it.load() }
                    }
                    val profileState by vm.state.collectAsStateWithLifecycle()
                    EditProfileScreen(
                        state = profileState,
                        onBack = { nav = nav.pop() },
                        onSave = vm::save,
                        onNickname = vm::setNickname,
                        onBio = vm::setBio,
                        onImage = vm::stageImage,
                        onUseDefaultAvatar = vm::useDefaultAvatar,
                    )
                }

                AppRoute.BlockedAuthors -> {
                    var working by remember { mutableStateOf<String?>(null) }
                    BlockedAuthorsScreen(
                        handles = communityBlocked.sorted,
                        working = working,
                        onUnblock = { handle ->
                            working = handle
                            community.unblock(handle) { working = null }
                        },
                    )
                }

                AppRoute.StudyThemes -> StudyThemesScreen(
                    readiness = settingsReadiness,
                    onRetryLoad = { scope.launch { app.settingsStore.load(deviceLanguage) } },
                    selected = settings.studyCategories,
                    categories = catalog.categories,
                    uiLang = uiLang,
                    onChange = { picked -> app.settingsStore.update { it.copy(studyCategories = picked) } },
                )

                AppRoute.Themes -> AtlasThemesScreen(
                    shelves = catalog.shelves,
                    words = catalog.words,
                    scores = scores,
                    seenAndTotal = progress::seenAndTotal,
                    uiLang = uiLang,
                    loading = !catalog.loaded,
                    bottomPadding = 0.dp,
                    onOpen = { id ->
                        val shelf = catalog.shelves.first { it.category.id == id }
                        nav = nav.push(
                            AppRoute.Shelf(id, CategoryShelf.title(shelf.category, uiLang)),
                        )
                    },
                )

                AppRoute.Community -> CommunityScreen(
                    explore = communityExplore,
                    saved = communitySaved,
                    me = communityMe.takeIf { !isGuest },
                    isGuest = isGuest,
                    language = direction.targetLanguage,
                    onShowSaved = community::loadSaved,
                    onRetry = community::load,
                    onSignIn = { app.auth.exitGuestMode() },
                    onOpenCollection = { nav = nav.push(AppRoute.Collection(it)) },
                    onOpenMyPage = { nav = nav.push(AppRoute.Author(it)) },
                )

                is AppRoute.PublicItem -> {
                    val vm = remember(route.slug, isGuest) {
                        PublicItemViewModel(
                            slug = route.slug,
                            atlas = app.atlas,
                            saver = app.atlas,
                            audio = app.clipPlayer,
                            direction = direction,
                            uiLang = uiLang,
                            signedIn = !isGuest,
                            accent = settings.accent,
                            speech = app.speech,
                            // The card went into, or out of, the reader's own
                            // 圖鑑 and study queue — 已收進 and the counts on 今日
                            // are drawn from those.
                            onSaveChanged = {
                                savedTick++
                                refreshTick++
                            },
                        ).also { it.open() }
                    }
                    val itemState by vm.state.collectAsStateWithLifecycle()
                    val authorHandle = itemState.item?.author?.handle
                    PublicItemScreen(
                        state = itemState,
                        relationship = ViewerRelationship.of(authorHandle, viewerHandle = myUid, isGuest = isGuest),
                        authorBlocked = communityBlocked.hides(authorHandle),
                        reported = communityReported == ReportTarget.Item(route.slug),
                        canPlay = vm.canPlay(),
                        session = direction.targetLanguage,
                        uiLang = uiLang,
                        showChinese = settings.showZh,
                        scores = scores,
                        onRetry = vm::open,
                        onSignIn = { app.auth.exitGuestMode() },
                        onToggleSave = vm::toggleSave,
                        onPlay = vm::play,
                        onOpenAuthor = { nav = nav.push(AppRoute.Author(it)) },
                        onReport = { reason -> community.report(ReportTarget.Item(route.slug), reason) },
                        onBlock = {
                            authorHandle?.let { handle ->
                                // Only the screen that asked leaves, and only
                                // if it is still the one on top.
                                community.block(handle) { if (nav.current == route) nav = nav.pop() }
                            }
                        },
                        onUnblock = { authorHandle?.let(community::unblock) },
                        speech = app.speech,
                        accent = settings.accent,
                    )
                }

                is AppRoute.Collection -> {
                    val vm = remember(route.slug, myUid, isGuest) {
                        CollectionDetailViewModel(
                            slug = route.slug,
                            atlas = app.atlas,
                            bookmarks = app.atlas,
                            learning = app.atlas,
                            viewerHandle = myUid,
                            signedIn = !isGuest,
                            blocked = { community.blockList },
                            onBookmarkChanged = community::loadSaved,
                            // Members went into the study queue: 已收進 and the
                            // counts on 今日 and 我 are drawn from it.
                            onLearned = {
                                savedTick++
                                refreshTick++
                            },
                        ).also { it.open() }
                    }
                    val collectionState by vm.state.collectAsStateWithLifecycle()
                    CollectionScreen(
                        state = collectionState,
                        isGuest = isGuest,
                        reported = communityReported == ReportTarget.Collection(route.slug),
                        onBack = { nav = nav.pop() },
                        onRetry = vm::open,
                        onSignIn = { app.auth.exitGuestMode() },
                        onSave = vm::save,
                        onUnsave = vm::unsave,
                        onLearn = vm::learnRemaining,
                        onDismissError = vm::dismissError,
                        onOpenItem = { nav = nav.push(AppRoute.PublicItem(it)) },
                        onOpenAuthor = { nav = nav.push(AppRoute.Author(it)) },
                        onReport = { reason -> community.report(ReportTarget.Collection(route.slug), reason) },
                    )
                }

                is AppRoute.Author -> {
                    val vm = remember(route.handle) {
                        AuthorViewModel(handle = route.handle, atlas = app.atlas).also { it.load() }
                    }
                    val authorState by vm.state.collectAsStateWithLifecycle()
                    AuthorScreen(
                        state = authorState,
                        relationship = ViewerRelationship.of(route.handle, viewerHandle = myUid, isGuest = isGuest)
                            ?: ViewerRelationship.Theirs,
                        blocked = communityBlocked.hides(route.handle),
                        reported = communityReported == ReportTarget.Author(route.handle),
                        onBack = { nav = nav.pop() },
                        onRetry = vm::load,
                        onOpenCollection = { nav = nav.push(AppRoute.Collection(it)) },
                        onOpenItem = { nav = nav.push(AppRoute.PublicItem(it)) },
                        onReport = { reason -> community.report(ReportTarget.Author(route.handle), reason) },
                        onBlock = { community.block(route.handle) { if (nav.current == route) nav = nav.pop() } },
                        onUnblock = { community.unblock(route.handle) },
                    )
                }

                AppRoute.Capture -> {
                    val vm = remember {
                        CaptureViewModel(authoring = app.atlas, direction = direction)
                    }
                    CaptureScreen(
                        vm = vm,
                        bottomPadding = 0.dp,
                        onDone = { nav = nav.pop() },
                    )
                }

                AppRoute.Me -> AccountScreen(
                    state = accountState,
                    isGuest = isGuest,
                    // The same readout 今日's 主題進度 prints.
                    completion = completion,
                    progress = progress,
                    spread = remember(scores) { MasteryDistribution.of(scores.byId) },
                    masteryLoaded = scores.loaded,
                    categories = catalog.categories,
                    bottomPadding = 0.dp,
                    onOpenSettings = { nav = nav.push(AppRoute.Settings) },
                    showChinese = settings.showZh,
                    onOpenWord = openCard,
                )

                AppRoute.Search -> AtlasSearchScreen(
                    vm = search,
                    direction = direction,
                    showChinese = settings.showZh,
                    recents = app.recentSearches,
                    onCancel = { nav = nav.pop() },
                    onOpen = { nav = nav.push(AppRoute.Word(it)) },
                )

                is AppRoute.Shelf -> AtlasThemeScreen(
                    category = catalog.categories.firstOrNull { it.id == route.categoryId },
                    words = CategoryShelf.words(route.categoryId, studyWords),
                    scores = scores,
                    uiLang = uiLang,
                    bottomPadding = 0.dp,
                    onBack = { nav = nav.pop() },
                    onOpen = openCard,
                )

                is AppRoute.Word -> {
                    val vm = remember(route.wordId) {
                        WordDetailViewModel(
                            catalog = app.catalogReading,
                            atlas = app.atlas,
                            audio = app.clipPlayer,
                            direction = direction,
                            uiLang = uiLang,
                            accent = settings.accent,
                            speech = app.speech,
                        ).also { it.load(route.wordId) }
                    }
                    WordDetailScreen(
                        vm = vm,
                        bottomPadding = 0.dp,
                        session = direction.targetLanguage,
                        uiLang = uiLang,
                        showChinese = settings.showZh,
                        onBack = { nav = nav.pop() },
                        resolve = { id -> catalog.words.firstOrNull { it.id == id } },
                        bookmarked = route.wordId in personal.bookmarked,
                        // 書籤 filters the *catalogue* by marked id, so a mark
                        // on a card the catalogue never had would go nowhere.
                        onBookmark = if (CardsSourceRules.isCustom(route.wordId)) null
                        else ({ app.cardsSourceStore.toggle(route.wordId) }),
                        scores = scores,
                        onOpenRelated = { nav = nav.push(AppRoute.Word(it)) },
                        speech = app.speech,
                        accent = settings.accent,
                        glossBookmarks = GlossBookmarks(
                            isMarked = { it in personal.bookmarked },
                            toggle = app.cardsSourceStore::toggle,
                        ),
                    )
                }

                else -> Unit
            }
            }
        }

        if (TabShell.tabBarVisible(nav)) {
            TujiTabBar(
                selected = nav.tab,
                bottomInset = insets.calculateBottomPadding(),
                onSelect = { nav = nav.select(it) },
                onCapture = { nav = nav.push(AppRoute.Capture) },
            )
        } else {
            Spacer(Modifier.height(insets.calculateBottomPadding()))
        }
    }
}

/** Whether the shell draws a back bar over [route]. */
private fun hasBackBar(route: AppRoute): Boolean = when (route) {
    // Not 單字詳情: its picture is the first thing on the page, with 返回 and
    // 書籤 floating over it.
    // Nor a 合集, whose cover bleeds and floats its own arrow, nor 作者主頁,
    // whose bar carries 更多.
    is AppRoute.PublicItem,
    AppRoute.Themes, AppRoute.Settings, AppRoute.StudyThemes, AppRoute.Capture, AppRoute.BlockedAuthors,
    is AppRoute.ManageCard -> true
    else -> false
}

/** 今日, plus the debug-only door to the font spike. */
@Composable
private fun TodayColumn(
    inputs: TodayInputs,
    identity: String?,
    onReview: () -> Unit,
    onLearnNew: () -> Unit,
    onSearch: () -> Unit,
    onCreateAccount: () -> Unit,
    completion: CompletionReadout,
    streak: Int,
    shelves: List<CategoryShelf.Shelf>,
    themeStatus: (String) -> ThemeStatus,
    uiLang: String,
    onOpenShelf: (String) -> Unit,
    onOpenStudyThemes: () -> Unit,
    onSpike: () -> Unit,
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
    Column(Modifier.fillMaxSize().then(spikeDoor).verticalScroll(rememberScrollState())) {
        TodayScreen(
            inputs = inputs,
            name = identity,
            bottomPadding = 0.dp,
            onReview = onReview,
            onLearnNew = onLearnNew,
            onSearch = onSearch,
            onCreateAccount = onCreateAccount,
            completion = completion,
            streak = streak,
            shelves = shelves,
            themeStatus = themeStatus,
            uiLang = uiLang,
            onOpenShelf = onOpenShelf,
            onOpenStudyThemes = onOpenStudyThemes,
        )
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
