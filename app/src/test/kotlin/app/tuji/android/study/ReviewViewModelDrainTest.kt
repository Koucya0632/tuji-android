package app.tuji.android.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The gap this pins: the drain worker's own doc said it was enqueued "at launch
 * **and whenever an answer is parked**", and only the first half was wired. A
 * rating parked mid-session therefore sat on disk until the next cold start —
 * the one moment the user is least likely to be watching for it.
 *
 * A comment that describes behaviour the code does not have is worse than no
 * comment, because it stops the next reader from checking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelDrainTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val item = StudyQueueItem(
        card = StudyCard(id = "c1"),
        word = StudyQueueWord(
            id = "w1", word = "cat", chinese = "貓",
            imageUrl = "https://img.test/w1.webp", pronunciation = "/kæt/", category = "animals",
        ),
    )

    private val queues = object : StudyQueueReading {
        override suspend fun queue(
            mode: StudyMode, limit: Int, new: Int,
            categories: List<String>, lang: String, learning: LearningDirection,
        ) = StudyQueueResponse(queue = listOf(item))
    }

    /** Always fails, the way a flat network does. */
    private val offline = AnswerSubmitting {
        throw java.io.IOException("offline")
    }

    private fun writer(): DurableAnswerWriter {
        val outbox = StudyAnswerOutbox(File(temp.root, "outbox.json"), ActiveAccount { "u1" })
        return DurableAnswerWriter(offline, outbox, backoff = {})
    }

    /**
     * A one-card queue lands in [Done], not [Studying], once the card is answered.
     * Both carry the count, and this test is about the count, not the shape.
     */
    private fun ReviewViewModel.unsyncedNow(): Int = when (val s = state.value) {
        is ReviewViewModel.State.Studying -> s.unsynced
        is ReviewViewModel.State.Done -> s.unsynced
        else -> error("expected a settled session, got $s")
    }

    @Test
    fun `parking an answer asks for a drain`() = runTest(dispatcher) {
        var drains = 0
        val vm = ReviewViewModel(
            queues = queues,
            writer = writer(),
            direction = LearningDirection.ZH_EN,
            uiLang = "zh-Hant",
            pool = { emptyList() },
            nowMs = { 0L },
            scope = TestScope(dispatcher),
            requestDrain = { drains += 1 },
        )
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        vm.pick("cat")           // correct and instant → auto-rated → one write
        advanceUntilIdle()

        assertEquals("a parked answer must ask to be drained", 1, drains)
        assertTrue("and it must be visible, not swallowed", vm.unsyncedNow() >= 1)
    }

    @Test
    fun `a write that lands asks for nothing`() = runTest(dispatcher) {
        var drains = 0
        val online = AnswerSubmitting { StudyAnswerResponse(ok = true) }
        val outbox = StudyAnswerOutbox(File(temp.root, "ok.json"), ActiveAccount { "u1" })
        val vm = ReviewViewModel(
            queues = queues,
            writer = DurableAnswerWriter(online, outbox, backoff = {}),
            direction = LearningDirection.ZH_EN,
            uiLang = "zh-Hant",
            pool = { emptyList() },
            nowMs = { 0L },
            scope = TestScope(dispatcher),
            requestDrain = { drains += 1 },
        )
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        vm.pick("cat")
        advanceUntilIdle()

        assertEquals(0, drains)
        assertEquals(0, vm.unsyncedNow())
    }
}
