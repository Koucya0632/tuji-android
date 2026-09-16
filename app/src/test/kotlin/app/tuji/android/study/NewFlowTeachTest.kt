package app.tuji.android.study

import app.tuji.android.core.model.CategoriesResponse
import app.tuji.android.core.model.GlossSpan
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordExample
import app.tuji.android.core.model.WordsListResponse
import app.tuji.android.core.network.CatalogReading
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.study.ActiveAccount
import app.tuji.android.core.study.AnswerSubmitting
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.StudyAnswerOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 認識's example sentence, and where it comes from.
 *
 * The queue carries the sentence bare — no translation, no 詞塊 — on purpose:
 * shipping the annotation for a hundred cards to serve the one the user opens
 * is a hundred copies of it. So the entry is fetched per word, and the rules
 * worth pinning are about a fetch that is *late*, *absent* or *impossible*,
 * because all three are the normal case at some point in a session and none of
 * them may take the lesson down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewFlowTeachTest {

    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(id: String, sentence: String? = "窓を開けます。") = StudyQueueItem(
        card = StudyCard(id = "card-$id"),
        word = StudyQueueWord(
            id = id, word = "窓", chinese = "窗戶",
            imageUrl = "https://img.test/$id.webp",
            pronunciation = "", category = "bedroom",
            targetLanguage = TargetLanguage.JA,
        ),
        examples = sentence?.let { listOf(StudyExample(sentence = it)) },
    )

    private fun detail(id: String, sentence: String, spanned: Boolean = true) = WordDetail(
        id = id,
        word = "窓",
        examples = listOf(
            WordExample(
                en = "I open the window.",
                target = sentence,
                zh = "我打開窗戶。",
                spans = if (!spanned) null else listOf(
                    GlossSpan(text = "窓", gloss = "窗戶"),
                    GlossSpan(text = "を"),
                    GlossSpan(text = "開けます", gloss = "打開"),
                    GlossSpan(text = "。"),
                ),
            ),
        ),
    )

    /** Records what was asked for, and answers from a fixed table. */
    private class FakeCatalog(private val details: Map<String, WordDetail>) : CatalogReading {
        val asked = mutableListOf<String>()
        override suspend fun words(lang: String, learning: LearningDirection) = WordsListResponse(words = emptyList())
        override suspend fun categories(lang: String) = CategoriesResponse(categories = emptyList())
        override suspend fun word(id: String, lang: String, learning: LearningDirection): WordDetail {
            asked += id
            return details[id] ?: error("no detail for $id")
        }
    }

    private fun vm(queue: List<StudyQueueItem>, catalog: CatalogReading?): NewFlowViewModel {
        val queues = object : StudyQueueReading {
            override suspend fun queue(
                mode: StudyMode, limit: Int, new: Int,
                categories: List<String>, lang: String, learning: LearningDirection,
            ) = StudyQueueResponse(queue = queue)
        }
        val outbox = StudyAnswerOutbox(File(temp.root, "o.json"), ActiveAccount { "u1" })
        return NewFlowViewModel(
            queues = queues,
            writer = DurableAnswerWriter(
                AnswerSubmitting { StudyAnswerResponse(ok = true) }, outbox, backoff = {},
            ),
            direction = LearningDirection.ZH_JA,
            uiLang = "zh-Hant",
            pool = { emptyList<Word>() },
            nowMs = { 0L },
            scope = TestScope(dispatcher),
            catalog = catalog,
        )
    }

    private fun NewFlowViewModel.studying() = state.value as NewFlowViewModel.State.Studying

    @Test fun `the card on screen gains its entry when the fetch lands`() = runTest(dispatcher) {
        val catalog = FakeCatalog(mapOf("w1" to detail("w1", "窓を開けます。")))
        val vm = vm(listOf(item("w1")), catalog)
        vm.load()
        advanceUntilIdle()

        val spans = vm.studying().teach?.examples?.first()?.spans
        assertEquals("the 詞塊 arrive with the entry", 4, spans?.size)
        assertEquals(listOf("w1"), catalog.asked)
    }

    @Test fun `a 自製 card is never asked for — the catalogue has not heard of it`() =
        runTest(dispatcher) {
            val catalog = FakeCatalog(emptyMap())
            val vm = vm(listOf(item("atlas:abc")), catalog)
            vm.load()
            advanceUntilIdle()

            assertEquals(emptyList<String>(), catalog.asked)
            assertNull(vm.studying().teach)
        }

    @Test fun `a fetch that fails leaves the lesson exactly as it was`() = runTest(dispatcher) {
        // No spinner, no error, no blocked card: the sentence the queue sent is
        // still on screen and the ladder has not moved.
        val catalog = object : CatalogReading {
            override suspend fun words(lang: String, learning: LearningDirection) = WordsListResponse(words = emptyList())
            override suspend fun categories(lang: String) = CategoriesResponse(categories = emptyList())
            override suspend fun word(id: String, lang: String, learning: LearningDirection): WordDetail =
                throw IllegalStateException("offline")
        }
        val vm = vm(listOf(item("w1")), catalog)
        vm.load()
        advanceUntilIdle()

        assertNull(vm.studying().teach)
        assertEquals(
            "窓を開けます。",
            (vm.studying().stage as NewFlowViewModel.Stage.Recognize).item.examples?.first()?.sentence,
        )
    }

    @Test fun `entries are asked for in queue order, so the first card is first`() =
        runTest(dispatcher) {
            val catalog = FakeCatalog(
                mapOf(
                    "w1" to detail("w1", "窓を開けます。"),
                    "w2" to detail("w2", "窓を閉めます。"),
                    "w3" to detail("w3", "窓を拭きます。"),
                ),
            )
            val vm = vm(listOf(item("w1"), item("w2"), item("w3")), catalog)
            vm.load()
            advanceUntilIdle()
            assertEquals(listOf("w1", "w2", "w3"), catalog.asked)
        }

    @Test fun `no catalogue at all is a session with no teaching pass`() = runTest(dispatcher) {
        val vm = vm(listOf(item("w1")), catalog = null)
        vm.load()
        advanceUntilIdle()
        assertNull(vm.studying().teach)
    }
}
