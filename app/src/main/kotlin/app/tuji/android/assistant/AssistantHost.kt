package app.tuji.android.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.AssistantRoute
import app.tuji.android.AtlasRoute
import app.tuji.android.ShellContext
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.study.ThemeStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Moira's pages: 點數明細, AI 查詞, AI 語法, 情境故事, 主題對話 and 對話紀錄. */
@Composable
fun AssistantHost(route: AssistantRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val settings = shell.settings
    val direction = shell.direction
    // Read at call time, not captured: the interface language can change under an open page.
    val uiLanguageNow = { app.settingsStore.current.value.language.wire }

    when (route) {
        AssistantRoute.CreditStatement -> CreditStatementScreen(
            repository = app.creditLedger,
            locale = shell.uiLanguage.locale,
            onBack = page::pop,
            onOpenMembership = shell::openMembership,
        )

        AssistantRoute.Vocabulary -> {
            val owner = shell.ownerId
            val model = remember(owner, direction) {
                VocabularyModel(
                    repository = app.vocabulary,
                    offerCache = app.assistantCaches.vocabularyOffer,
                    historyCache = app.assistantCaches.vocabularyHistory,
                    journal = app.vocabularyJournal,
                    owner = owner,
                    uiLanguage = uiLanguageNow,
                    learning = direction.wire,
                    catalog = { app.catalog.words },
                )
            }
            val offerRevision by app.assistantCaches.vocabularyOffer.revision.collectAsStateWithLifecycle()
            VocabularyScreen(
                model = model,
                speech = app.speech,
                accent = settings.accent,
                offerRevision = offerRevision,
                uiLanguage = shell.uiLang,
                onBack = page::pop,
                onOpenStatement = { page.push(AssistantRoute.CreditStatement) },
                onOpenMembership = shell::openMembership,
                onOpenWord = { page.openWord(it) },
                onSearch = { page.push(AtlasRoute.SearchFor(it)) },
            )
        }

        AssistantRoute.Story -> {
            val learning = shell.learning
            val model = remember(direction) {
                StoryBuilderModel(
                    repository = app.story,
                    offerCache = app.assistantCaches.storyOffer,
                    // A lambda over the shell's latest, not this composition's: the
                    // candidates are read when the picker opens.
                    candidates = { app.catalog.words + app.cardsSourceStore.personal.value.let { it.mine + it.taken } },
                    uiLanguage = uiLanguageNow,
                    learning = direction.wire,
                    language = direction.targetLanguage,
                )
            }
            val offerRevision by app.assistantCaches.storyOffer.revision.collectAsStateWithLifecycle()
            StoryScreen(
                model = model,
                offerRevision = offerRevision,
                speech = app.speech,
                accent = settings.accent,
                shelves = learning.catalog.shelves,
                themeStatus = { id ->
                    ThemeStatus.of(
                        wordIds = CategoryShelf.words(id, learning.studyWords).map { it.id },
                        masteryScore = learning.scores::score,
                        seenAndTotal = learning.progress.seenAndTotal(id),
                    )
                },
                bookmarked = learning.personal.bookmarked,
                uiLang = shell.uiLang,
                onBack = page::pop,
                onOpenWord = page::openCard,
            )
        }

        AssistantRoute.Grammar -> {
            val owner = shell.ownerId
            val model = remember(owner, direction) {
                GrammarModel(
                    repository = app.grammar,
                    offerCache = app.assistantCaches.grammarOffer,
                    historyCache = app.assistantCaches.grammarHistory,
                    journal = app.grammarJournal,
                    progress = app.grammarProgress,
                    owner = owner,
                    uiLanguage = uiLanguageNow,
                    learning = direction.wire,
                )
            }
            val offerRevision by app.assistantCaches.grammarOffer.revision.collectAsStateWithLifecycle()
            GrammarScreen(
                model = model,
                speech = app.speech,
                accent = settings.accent,
                offerRevision = offerRevision,
                uiLanguage = shell.uiLang,
                onBack = page::pop,
                onOpenStatement = { page.push(AssistantRoute.CreditStatement) },
                onOpenMembership = shell::openMembership,
            )
        }

        is AssistantRoute.TopicChat -> {
            val chatScope = rememberCoroutineScope()
            val model = remember(route, direction) {
                TopicChatModel(
                    entry = route.entry,
                    repository = app.topicChat,
                    preferences = app.topicChatPreferences,
                    uiLanguage = uiLanguageNow,
                    direction = direction.wire,
                    scope = chatScope,
                )
            }
            TopicChatScreen(
                model = model,
                topics = rememberChatTopics(shell),
                speech = app.speech,
                accent = settings.accent,
                uiLanguage = shell.uiLanguage,
                onBack = page::pop,
                onOpenHistory = { page.push(AssistantRoute.TopicChatHistory) },
                onOpenStatement = { page.push(AssistantRoute.CreditStatement) },
                onOpenMembership = shell::openMembership,
            )
        }

        AssistantRoute.TopicChatHistory -> {
            val chatScope = rememberCoroutineScope()
            val model = remember(direction) {
                TopicChatModel(
                    entry = TopicChatEntry.Saved(""),
                    repository = app.topicChat,
                    preferences = app.topicChatPreferences,
                    uiLanguage = uiLanguageNow,
                    direction = direction.wire,
                    scope = chatScope,
                )
            }
            TopicChatHistoryScreen(
                model = model,
                onBack = page::pop,
                onOpen = { page.push(AssistantRoute.TopicChat(TopicChatEntry.Saved(it))) },
            )
        }
    }
}

/**
 * 主題對話's topics: the catalogue's themes (not 自製 or 物見), with how many
 * official words each has — a theme needs enough of them to talk about.
 */
@Composable
fun rememberChatTopics(shell: ShellContext): List<TopicChoice> {
    val catalog = shell.learning.catalog
    val uiLang = shell.uiLang
    return remember(catalog.shelves, catalog.words, uiLang) {
        catalog.shelves
            .filter { it.category.id != "custom" && it.category.id != "community" }
            .map { shelf ->
                TopicChoice(
                    id = shelf.category.id,
                    name = CategoryShelf.title(shelf.category, uiLang),
                    imageUrl = shelf.category.imageUrl,
                    wordCount = CategoryShelf.words(shelf.category.id, catalog.words).size,
                )
            }
    }
}

/**
 * So 情境故事 opens on its choices and AI 查詞／AI 語法 on their prices. Run
 * from 今天, where the ways in are.
 */
suspend fun prefetchAssistant(shell: ShellContext) {
    val app = shell.app
    val owner = shell.ownerId
    val uiLang = shell.uiLang
    coroutineScope {
        launch { app.assistantCaches.vocabularyOffer.prefetch(owner) { app.vocabulary.offer() } }
        launch { app.assistantCaches.grammarOffer.prefetch(owner) { app.grammar.offer() } }
        launch { app.assistantCaches.storyOffer.prefetch(uiLang) { app.story.storyOffer(uiLang) } }
    }
}
