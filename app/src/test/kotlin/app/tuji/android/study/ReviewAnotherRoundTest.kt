package app.tuji.android.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyQueueWord
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 再來一輪 from the 複習 summary — iOS's `startAnotherRound`. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewAnotherRoundTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(id: String, word: String) = StudyQueueItem(
        card = StudyCard(id = "c$id"),
        word = StudyQueueWord(
            id = id, word = word, chinese = "字",
            imageUrl = "https://img.test/$id.webp", pronunciation = "/x/", category = "animals",
        ),
    )

    /** Hands out one queue per call, then nothing. */
    private class Queues(val rounds: MutableList<List<StudyQueueItem>>) : StudyQueueReading {
        var calls = 0
        override suspend fun queue(
            mode: StudyMode, limit: Int, new: Int,
            categories: List<String>, lang: String, learning: LearningDirection,
        ): StudyQueueResponse {
            calls++
            return StudyQueueResponse(queue = rounds.removeFirstOrNull().orEmpty())
        }
    }

    private fun vm(queues: StudyQueueReading): ReviewViewModel {
        val outbox = StudyAnswerOutbox(File(temp.root, "outbox.json"), ActiveAccount { "u1" })
        return ReviewViewModel(
            queues = queues,
            writer = DurableAnswerWriter(AnswerSubmitting { StudyAnswerResponse(ok = true) }, outbox, backoff = {}),
            direction = LearningDirection.ZH_EN,
            uiLang = "zh-Hant",
            pool = { emptyList() },
            nowMs = { 0L },
            scope = TestScope(dispatcher),
        )
    }

    @Test fun `another round starts a fresh session on the new queue`() = runTest(dispatcher) {
        val queues = Queues(mutableListOf(listOf(item("w1", "cat")), listOf(item("w2", "dog"))))
        val vm = vm(queues)
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        vm.pick("cat")
        advanceUntilIdle()
        assertTrue(vm.state.value is ReviewViewModel.State.Done)

        assertTrue(vm.anotherRound())
        val studying = vm.state.value as ReviewViewModel.State.Studying
        assertEquals("w2", studying.session.question?.item?.word?.id)
        assertEquals(0, studying.session.passedCount)
    }

    @Test fun `an empty queue leaves the summary standing`() = runTest(dispatcher) {
        val queues = Queues(mutableListOf(listOf(item("w1", "cat"))))
        val vm = vm(queues)
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        vm.pick("cat")
        advanceUntilIdle()
        val done = vm.state.value

        assertFalse(vm.anotherRound())
        assertEquals(2, queues.calls)
        assertTrue("no loading flash, no empty session", vm.state.value === done)
    }
}
