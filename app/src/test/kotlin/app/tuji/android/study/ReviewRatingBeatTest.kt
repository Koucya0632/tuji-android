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
import app.tuji.android.core.study.ReviewRevealMode
import app.tuji.android.core.study.StudyAnswerOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * Rating on the reveal sheet, as iOS does it: the sheet stays up through the
 * 300ms beat with the tapped level filled, a second tap does nothing, and the
 * advance is what takes the sheet away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRatingBeatTest {

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

    @Test fun `the sheet holds the rating through the beat and takes it once`() = runTest(dispatcher) {
        val sent = mutableListOf<StudyAnswerPayload>()
        val outbox = StudyAnswerOutbox(File(temp.root, "outbox.json"), ActiveAccount { "u1" })
        var now = 0L
        val vm = ReviewViewModel(
            queues = object : StudyQueueReading {
                override suspend fun queue(
                    mode: StudyMode, limit: Int, new: Int,
                    categories: List<String>, lang: String, learning: LearningDirection,
                ) = StudyQueueResponse(queue = listOf(item))
            },
            writer = DurableAnswerWriter(AnswerSubmitting { sent += it; StudyAnswerResponse(ok = true) }, outbox, backoff = {}),
            direction = LearningDirection.ZH_EN,
            uiLang = "zh-Hant",
            pool = { emptyList() },
            nowMs = { now },
            scope = TestScope(dispatcher),
        )
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        // Correct but slow: the answer needs the user, so the sheet comes up.
        now = 30_000L
        vm.pick("cat")
        advanceTimeBy(1_000L)
        val asked = vm.state.value as ReviewViewModel.State.Studying
        assertEquals(ReviewRevealMode.Rate, asked.revealMode)

        vm.rate(SRSRating.Good)
        val held = vm.state.value as ReviewViewModel.State.Studying
        assertEquals("the sheet stays up for the beat", ReviewRevealMode.Rate, held.revealMode)
        assertEquals(SRSRating.Good, held.session.question?.rated)

        vm.rate(SRSRating.Easy)
        val after = vm.state.value as ReviewViewModel.State.Studying
        assertEquals("a second tap changes nothing", SRSRating.Good, after.session.question?.rated)

        advanceUntilIdle()
        assertTrue(vm.state.value is ReviewViewModel.State.Done)
        assertEquals("one rating, one write", 1, sent.size)
    }
}
