package app.tuji.android

import androidx.compose.foundation.background
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
import app.tuji.android.auth.WelcomeScreen
import app.tuji.android.core.auth.AuthState
import app.tuji.android.core.model.LaunchAccountState
import app.tuji.android.core.model.LaunchContext
import app.tuji.android.core.model.LaunchDestination
import app.tuji.android.core.model.LaunchRouting
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.study.MasteryDistribution
import app.tuji.android.core.model.UiLanguage
import java.util.Locale
import app.tuji.android.onboarding.LearningDirectionScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import app.tuji.android.atlas.AtlasSearchScreen
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
import app.tuji.android.community.PublicItemScreen
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.core.design.TujiColor
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
 * The signed-in app: three tabs, one back stack, and the study flows on top.
 *
 * The stack is [NavStack] and nothing else — the hardware back button and 返回
 * pop the same list, so the two can never disagree about where "back" is.
 */
@Composable
private fun SignedInShell(app: TujiApplication, identity: String?) {
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val direction = app.onboarding.learningDirection ?: LearningDirection.ZH_EN
    // From the device, not a constant. The app's own strings come from
    // Android's resource resolution, but the *server* writes glosses and
    // definitions in whatever `lang` asks for — and hard-coding it is how a
    // fully Japanese interface ends up wrapped around Chinese content.
    val configuration = LocalConfiguration.current
    val uiLanguage = remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        UiLanguage.of(locale.language, locale.script, locale.country)
    }
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
    }
    val catalog by app.catalog.contents.collectAsStateWithLifecycle()
    val scores by app.masteryStore.scores.collectAsStateWithLifecycle()
    val progress by app.progressStore.snapshot.collectAsStateWithLifecycle()

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
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0) return@LaunchedEffect
        app.masteryStore.load(direction, force = true)
        app.progressStore.load(direction)
    }
    val community = remember(direction) {
        CommunityViewModel(
            atlas = app.atlas,
            saver = app.atlas,
            reporter = app.atlas,
            blocks = app.atlas,
            direction = direction,
            uiLang = uiLang,
        ).also { it.load() }
    }
    val communityFeed by community.feed.collectAsStateWithLifecycle()
    val communityItem by community.item.collectAsStateWithLifecycle()
    val communityAuthor by community.author.collectAsStateWithLifecycle()
    val communityCollection by community.collection.collectAsStateWithLifecycle()

    val account = remember {
        AccountViewModel(accounts = app.atlas, entitlements = app.atlas)
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

    var nav by remember { mutableStateOf(NavStack()) }
    var showSpike by remember { mutableStateOf(false) }

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
            val vm = remember {
                ReviewViewModel(
                    queues = app.study,
                    writer = app.answerWriter,
                    direction = direction,
                    uiLang = uiLang,
                    pool = { app.catalog.words },
                    requestDrain = { AnswerDrainWorker.enqueue(app) },
                    audio = app.clipPlayer,
                    online = { app.isOnline() },
                    hints = app.onboarding,
                ).also { it.load(StudyMode.Review) }
            }
            ReviewScreen(
                vm = vm,
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
            val vm = remember {
                NewFlowViewModel(
                    queues = app.study,
                    writer = app.answerWriter,
                    direction = direction,
                    uiLang = uiLang,
                    pool = { app.catalog.words },
                    requestDrain = { AnswerDrainWorker.enqueue(app) },
                ).also { it.load() }
            }
            NewFlowScreen(
                vm = vm,
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
    // ratings has changed every number on that screen.
    LaunchedEffect(nav.current) {
        if (nav.current == AppRoute.Today) today.refresh()
        if (nav.current == AppRoute.Me) {
            account.refresh()
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

    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        // Never at a tab root, and never over a bleeding hero.
        //
        // A tab root had one because switching tabs *pushes* — so 圖鑑 opened
        // with 「← 圖鑑」 above it, offering to go back to a tab the bar below
        // already reaches in one tap. iOS gives each tab its own stack and
        // draws nothing at its root; Android's system back still walks out the
        // way the user came in, which is the convention that actually matters
        // here. The row was also the app telling its own name to somebody
        // already inside it.
        //
        // 主題頁 opts out for a different reason: its hero bleeds to the top
        // edge, and a row above it would mean the page no longer opens with
        // the picture. It draws its own back, floating.
        if (nav.canGoBack && nav.current !is AppRoute.Tab && nav.current !is AppRoute.Shelf) {
            TopBar(
                nav = nav,
                wordTitle = { id -> catalog.words.firstOrNull { it.id == id }?.word },
                onBack = { nav = nav.pop() },
                topPadding = insets.calculateTopPadding(),
            )
        } else {
            Spacer(Modifier.height(insets.calculateTopPadding()))
        }

        Box(Modifier.weight(1f)) {
            when (val route = nav.current) {
                AppRoute.Today -> TodayColumn(
                    inputs = todayInputs,
                    identity = identity,
                    onReview = { nav = nav.push(AppRoute.Review) },
                    onLearnNew = { nav = nav.push(AppRoute.LearnNew) },
                    onSearch = { nav = nav.select(AppRoute.Search) },
                    onCreateAccount = { app.auth.exitGuestMode() },
                    shelves = catalog.shelves,
                    uiLang = uiLang,
                    onOpenShelf = { id ->
                        val shelf = catalog.shelves.first { it.category.id == id }
                        nav = nav.select(AppRoute.Atlas)
                            .push(AppRoute.Shelf(id, CategoryShelf.title(shelf.category, uiLang)))
                    },
                    onOpenAtlas = { nav = nav.select(AppRoute.Atlas) },
                    onSpike = { showSpike = true },
                )

                AppRoute.Atlas -> AtlasCardsScreen(
                    words = catalog.words,
                    scores = scores,
                    loading = !catalog.loaded,
                    bottomPadding = 0.dp,
                    onOpenThemes = { nav = nav.push(AppRoute.Themes) },
                    onOpen = { nav = nav.push(AppRoute.Word(it)) },
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
                    feed = communityFeed,
                    bottomPadding = 0.dp,
                    slotsLeft = accountState.entitlement?.let {
                        it.atlasSlotsLimit - it.usage.atlasSlots
                    },
                    onCapture = { nav = nav.push(AppRoute.Capture) },
                    onOpenItem = { nav = nav.push(AppRoute.PublicItem(it)) },
                    onOpenCollection = { nav = nav.push(AppRoute.Collection(it)) },
                    onOpenAuthor = { nav = nav.push(AppRoute.Author(it)) },
                )

                is AppRoute.PublicItem -> {
                    LaunchedEffect(route.slug) { community.openItem(route.slug) }
                    PublicItemScreen(
                        state = communityItem,
                        bottomPadding = 0.dp,
                        onSave = community::save,
                        onReport = { target, reason -> community.report(target, reason) },
                        onOpenAuthor = { nav = nav.push(AppRoute.Author(it)) },
                    )
                }

                is AppRoute.Collection -> {
                    LaunchedEffect(route.slug) { community.openCollection(route.slug) }
                    CollectionScreen(
                        detail = communityCollection,
                        bottomPadding = 0.dp,
                        onOpenItem = { nav = nav.push(AppRoute.PublicItem(it)) },
                        onOpenAuthor = { nav = nav.push(AppRoute.Author(it)) },
                        onReport = { target, reason -> community.report(target, reason) },
                    )
                }

                is AppRoute.Author -> {
                    LaunchedEffect(route.handle) { community.openAuthor(route.handle) }
                    AuthorScreen(
                        page = communityAuthor,
                        bottomPadding = 0.dp,
                        onOpenItem = { nav = nav.push(AppRoute.PublicItem(it)) },
                        onReport = { target, reason -> community.report(target, reason) },
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
                    direction = direction,
                    // The same counts 今日 prints, from the same store: two
                    // screens showing different 完成度 for one account is the
                    // kind of bug that reads as a server problem for a week.
                    stats = todayInputs.stats,
                    progress = progress,
                    spread = remember(scores) { MasteryDistribution.of(scores.byId) },
                    masteryLoaded = scores.loaded,
                    categories = catalog.categories,
                    bottomPadding = 0.dp,
                    onOpenPaywall = { /* M4：商店設好之前不會走到這裡 */ },
                    onSignOut = { scope.launch { app.auth.signOut() } },
                )

                AppRoute.Search -> AtlasSearchScreen(
                    words = catalog.words,
                    bottomPadding = 0.dp,
                    onOpen = { nav = nav.push(AppRoute.Word(it)) },
                )

                is AppRoute.Shelf -> AtlasThemeScreen(
                    category = catalog.categories.firstOrNull { it.id == route.categoryId },
                    words = CategoryShelf.words(route.categoryId, catalog.words),
                    scores = scores,
                    uiLang = uiLang,
                    topPadding = insets.calculateTopPadding(),
                    bottomPadding = 0.dp,
                    onBack = { nav = nav.pop() },
                    onOpen = { nav = nav.push(AppRoute.Word(it)) },
                )

                is AppRoute.Word -> {
                    val vm = remember(route.wordId) {
                        WordDetailViewModel(
                            catalog = app.catalogReading,
                            audio = app.clipPlayer,
                            direction = direction,
                            uiLang = uiLang,
                        ).also { it.load(route.wordId) }
                    }
                    WordDetailScreen(
                        vm = vm,
                        bottomPadding = 0.dp,
                        resolve = { id -> catalog.words.firstOrNull { it.id == id } },
                        scores = scores,
                        onOpenRelated = { nav = nav.push(AppRoute.Word(it)) },
                    )
                }

                else -> Unit
            }
        }

        TabBar(
            selected = nav.tab,
            bottomPadding = insets.calculateBottomPadding(),
            onSelect = { nav = nav.select(it) },
        )
    }
}

/**
 * 返回 and the account line.
 *
 * The title is the stack's, so a shelf says which shelf: a screen of 64 nouns
 * with no header is indistinguishable from a different screen of 64 nouns.
 */
@Composable
private fun TopBar(
    nav: NavStack,
    wordTitle: (String) -> String?,
    onBack: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "← " + title(nav.current, wordTitle),
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink2,
            modifier = Modifier.tujiClickable(onClick = onBack).padding(TujiSpace.S1),
        )
    }
}

/** The current screen's own name, so 64 nouns are distinguishable from 64 others. */
@Composable
private fun title(route: AppRoute, wordTitle: (String) -> String?): String = when (route) {
    is AppRoute.Word -> wordTitle(route.wordId) ?: stringResource(R.string.atlas_title)
    is AppRoute.PublicItem -> stringResource(R.string.nav_community)
    is AppRoute.Author -> stringResource(R.string.nav_community)
    is AppRoute.Collection -> stringResource(R.string.community_collections)
    AppRoute.Themes -> stringResource(R.string.atlas_themes_title)
    AppRoute.Capture -> stringResource(R.string.capture_title)
    else -> stringResource(R.string.atlas_back)
}

@Composable
private fun TabBar(
    selected: AppRoute.Tab?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSelect: (AppRoute.Tab) -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TujiColor.Rule))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
        ) {
            listOf(
                AppRoute.Today to R.string.nav_today,
                AppRoute.Atlas to R.string.nav_atlas,
                AppRoute.Community to R.string.nav_community,
                AppRoute.Search to R.string.nav_search,
                AppRoute.Me to R.string.nav_me,
            ).forEach { (tab, label) ->
                val active = selected == tab
                Box(
                    Modifier
                        .weight(1f)
                        .tujiClickable { onSelect(tab) }
                        .padding(vertical = TujiSpace.S3),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(label),
                        style = if (active) TujiType.bodySmStrong else TujiType.bodySm,
                        color = if (active) TujiColor.Ink else TujiColor.Ink3,
                    )
                }
            }
        }
    }
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
    shelves: List<CategoryShelf.Shelf>,
    uiLang: String,
    onOpenShelf: (String) -> Unit,
    onOpenAtlas: () -> Unit,
    onSpike: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TodayScreen(
            inputs = inputs,
            name = identity,
            bottomPadding = 0.dp,
            onReview = onReview,
            onLearnNew = onLearnNew,
            onSearch = onSearch,
            onCreateAccount = onCreateAccount,
            shelves = shelves,
            uiLang = uiLang,
            onOpenShelf = onOpenShelf,
            onOpenAtlas = onOpenAtlas,
        )
        if (BuildConfig.DEBUG) {
            // `docs/SPIKE-FURIGANA.md` promises anyone who touches the fonts
            // can re-run it, so the door stays — off the release build.
            Text(
                stringResource(R.string.debug_font_spike),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
                modifier = Modifier.padding(TujiSpace.S4).tujiClickable(onClick = onSpike),
            )
        }
    }
}
