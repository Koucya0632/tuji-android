package app.tuji.android.atlas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.AssistantRoute
import app.tuji.android.AtlasRoute
import app.tuji.android.ShellContext
import app.tuji.android.assistant.TopicChatEntry
import app.tuji.android.assistant.rememberChatTopics
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.WordInsights
import app.tuji.android.gloss.GlossBookmarks
import app.tuji.android.scenes.WordScenes
import app.tuji.android.scenes.themeScenes
import app.tuji.android.wordlists.WordListSlot

/** 圖鑑's pages: 搜尋, a theme, and a word. */
@Composable
fun AtlasHost(route: AtlasRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val learning = shell.learning
    val settings = shell.settings

    when (route) {
        AtlasRoute.Search -> AtlasSearchScreen(
            vm = shell.models.search,
            direction = shell.direction,
            showChinese = settings.showZh,
            recents = app.recentSearches,
            onCancel = page::pop,
            onOpen = { page.openWord(it) },
        )

        is AtlasRoute.SearchFor -> AtlasSearchScreen(
            vm = shell.models.search,
            direction = shell.direction,
            showChinese = settings.showZh,
            recents = app.recentSearches,
            onCancel = page::pop,
            onOpen = { page.openWord(it) },
            initialQuery = route.query,
        )

        is AtlasRoute.Shelf -> {
            val topics = rememberChatTopics(shell)
            AtlasThemeScreen(
                category = learning.catalog.categories.firstOrNull { it.id == route.categoryId },
                words = CategoryShelf.words(route.categoryId, learning.studyWords),
                scores = learning.scores,
                uiLang = shell.uiLang,
                bottomPadding = 0.dp,
                onBack = page::pop,
                onOpen = page::openCard,
                onChat = topics.firstOrNull { it.id == route.categoryId && it.usable }?.let { topic ->
                    { page.push(AssistantRoute.TopicChat(TopicChatEntry.Topic(topic.id))) }
                },
                sceneEntries = themeScenes(shell, route.categoryId, from = route),
            )
        }

        is AtlasRoute.Word -> WordPages(route, shell)
    }
}

/**
 * One word, and the ones beside it: the whole catalogue in store order — the
 * 官方 grid's own order — so the next word is the one that was beside it. A
 * word the catalogue has never heard of (a 自製 card, a 物見 item) is its own
 * single page rather than no page.
 */
@Composable
private fun WordPages(route: AtlasRoute.Word, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val settings = shell.settings
    val direction = shell.direction
    val uiLang = shell.uiLang
    val words = shell.learning.catalog.words
    val personal = shell.learning.personal
    val ids = remember(words, route.wordId) {
        val all = words.map { it.id }
        if (route.wordId in all) all else listOf(route.wordId)
    }
    val pager = rememberPagerState(
        initialPage = ids.indexOf(route.wordId).coerceAtLeast(0),
        pageCount = { ids.size },
    )
    // Deliberately **not** pushed into the stack, as on iOS: swiping is
    // reading on, not navigating, and a stack that grew a step per swipe would
    // make 返回 walk back through every word the reader passed.
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
        val wordId = ids[index]
        val vm = remember(wordId) {
            WordDetailViewModel(
                catalog = app.catalogReading,
                atlas = app.atlas,
                audio = app.clipPlayer,
                direction = direction,
                uiLang = uiLang,
                accent = settings.accent,
                speech = app.speech,
            ).also { it.load(wordId) }
        }
        WordDetailScreen(
            vm = vm,
            bottomPadding = 0.dp,
            session = direction.targetLanguage,
            uiLang = uiLang,
            showChinese = settings.showZh,
            onBack = page::pop,
            bookmarked = wordId in personal.bookmarked,
            // 書籤 filters the *catalogue* by marked id, so a mark on a card the
            // catalogue never had would go nowhere.
            onBookmark = if (CardsSourceRules.isCustom(wordId)) null else ({ app.cardsSourceStore.toggle(wordId) }),
            scores = shell.learning.scores,
            onOpenWord = { page.openWord(it) },
            speech = app.speech,
            accent = settings.accent,
            glossBookmarks = GlossBookmarks(
                isMarked = { it in personal.bookmarked },
                toggle = app.cardsSourceStore::toggle,
            ),
            insights = rememberWordInsights(app.wordInsightsStore, wordId, direction, uiLang, shell.entitlement),
            // A confusable whose id the catalogue does not know is not a link:
            // a row that opens onto a 404 is worse than plain text.
            canOpenWord = { id -> words.any { it.id == id } },
            onLocked = shell::openMembership,
            scenes = { modifier -> WordScenes(shell, wordId, route.fromScene, from = route, modifier) },
            wordListButton = { WordListSlot(shell, wordId) },
            note = { modifier ->
                // Official words only: the server refuses a note on a 自製 or 物見 id.
                if (!CardsSourceRules.isCustom(wordId) && !CardsSourceRules.isSaved(wordId)) {
                    WordNoteSection(
                        wordId = wordId,
                        store = app.wordNotesStore,
                        entitlement = shell.entitlement,
                        onLocked = shell::openMembership,
                        modifier = modifier,
                    )
                }
            },
        )
    }
}

/**
 * One word's 詞條延伸內容, asked for when the word is shown. The key carries the
 * tier, so a purchase made from the lock fetches the unlocked text on return.
 */
@Composable
fun rememberWordInsights(
    store: WordInsightsStore,
    wordId: String,
    direction: LearningDirection,
    uiLang: String,
    entitlement: Entitlement?,
): WordInsights? {
    val answers by store.answers.collectAsStateWithLifecycle()
    val key = WordInsightsStore.key(wordId, direction, uiLang, entitlement)
    LaunchedEffect(key) { store.load(wordId, direction, uiLang, entitlement) }
    return answers[key]
}
