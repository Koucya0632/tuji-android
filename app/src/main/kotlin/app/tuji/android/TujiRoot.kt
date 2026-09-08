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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import app.tuji.android.atlas.AtlasSearchScreen
import app.tuji.android.atlas.AtlasShelvesScreen
import app.tuji.android.atlas.AtlasWordsScreen
import app.tuji.android.atlas.WordDetailScreen
import app.tuji.android.atlas.WordDetailViewModel
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
    val uiLang = "zh-Hant"

    // Ahead of every screen that reads it: 圖鑑 draws it, 搜尋 filters it, and
    // 複習's 聽句 draws its second picture from it — a pool that arrives late
    // does not make that question worse, it makes it not happen.
    LaunchedEffect(direction) { app.catalog.load(direction) }
    val catalog by app.catalog.contents.collectAsStateWithLifecycle()

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
                ).also { it.load(StudyMode.Review) }
            }
            ReviewScreen(vm = vm, onClose = { nav = nav.pop() })
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
            NewFlowScreen(vm = vm, onClose = { nav = nav.pop() })
            return
        }
        else -> Unit
    }

    // Every return to 今日, not only at launch: a session that just wrote three
    // ratings has changed every number on that screen.
    LaunchedEffect(nav.current) { if (nav.current == AppRoute.Today) today.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        TopBar(
            nav = nav,
            identity = identity,
            wordTitle = { id -> catalog.words.firstOrNull { it.id == id }?.word },
            onBack = { nav = nav.pop() },
            topPadding = insets.calculateTopPadding(),
            onAccount = {
                if (identity == null) app.auth.exitGuestMode() else scope.launch { app.auth.signOut() }
            },
        )

        Box(Modifier.weight(1f)) {
            when (val route = nav.current) {
                AppRoute.Today -> TodayColumn(
                    inputs = todayInputs,
                    identity = identity,
                    onReview = { nav = nav.push(AppRoute.Review) },
                    onLearnNew = { nav = nav.push(AppRoute.LearnNew) },
                    onSpike = { showSpike = true },
                )

                AppRoute.Atlas -> AtlasShelvesScreen(
                    shelves = catalog.shelves,
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

                AppRoute.Search -> AtlasSearchScreen(
                    words = catalog.words,
                    bottomPadding = 0.dp,
                    onOpen = { nav = nav.push(AppRoute.Word(it)) },
                )

                is AppRoute.Shelf -> AtlasWordsScreen(
                    words = CategoryShelf.words(route.categoryId, catalog.words),
                    bottomPadding = 0.dp,
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
    identity: String?,
    wordTitle: (String) -> String?,
    onBack: () -> Unit,
    onAccount: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nav.canGoBack) {
            Text(
                "← " + title(nav.current, wordTitle),
                style = TujiType.bodySmStrong,
                color = TujiColor.Ink2,
                modifier = Modifier.tujiClickable(onClick = onBack).padding(TujiSpace.S1),
            )
        } else {
            Text(
                identity ?: stringResource(R.string.guest_mode),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
            )
        }
        Text(
            stringResource(if (identity == null) R.string.sign_in_or_up else R.string.sign_out),
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink2,
            modifier = Modifier.tujiClickable(onClick = onAccount).padding(TujiSpace.S1),
        )
    }
}

/** The current screen's own name, so 64 nouns are distinguishable from 64 others. */
@Composable
private fun title(route: AppRoute, wordTitle: (String) -> String?): String = when (route) {
    is AppRoute.Shelf -> route.title
    is AppRoute.Word -> wordTitle(route.wordId) ?: stringResource(R.string.atlas_title)
    is AppRoute.PublicItem -> stringResource(R.string.nav_community)
    is AppRoute.Author -> stringResource(R.string.nav_community)
    is AppRoute.Collection -> stringResource(R.string.community_collections)
    AppRoute.Community -> stringResource(R.string.nav_community)
    AppRoute.Search -> stringResource(R.string.nav_search)
    AppRoute.Atlas -> stringResource(R.string.nav_atlas)
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
    onSpike: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TodayScreen(
            inputs = inputs,
            name = identity,
            onReview = onReview,
            onLearnNew = onLearnNew,
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
