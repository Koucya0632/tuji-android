package app.tuji.android.study

import app.tuji.android.core.design.TujiHaptics
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * When the phone buzzes during a study session, and which of the four taps it
 * is.
 *
 * This is the half of `TujiHaptics` that can be wrong without a screenshot
 * showing it — the four effects are indistinguishable in a capture, and the
 * thing that actually breaks is *placement*: a warning that arrives 800ms after
 * the answer it is about, a wrong pick that buzzes again every time the row
 * recomposes, a rating whose tap is felt twice because both the button and the
 * view model answered it.
 *
 * Asserted as sequences rather than counts, because the order is the claim:
 * every tile lands softly and only the last one is a verdict.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyHapticsPlacementTest {

    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private enum class Tap { Soft, Firm, Success, Warning }

    private class Recorder : TujiHaptics {
        val taps = mutableListOf<Tap>()
        override fun soft() { taps += Tap.Soft }
        override fun firm() { taps += Tap.Firm }
        override fun success() { taps += Tap.Success }
        override fun warning() { taps += Tap.Warning }
    }

    private val haptics = Recorder()

    private fun item(id: String, word: String, choices: List<String>? = null) = StudyQueueItem(
        card = StudyCard(id = "card-$id"),
        word = StudyQueueWord(
            id = id, word = word, chinese = "詞-$id",
            imageUrl = "https://img.test/$id.webp",
            pronunciation = "", category = "kitchen",
            targetLanguage = TargetLanguage.EN,
        ),
        // Unrelated words on purpose: a distractor that shares a token with
        // the answer is rejected as unfair before it ever reaches a screen, so
        // "kettle-x1" would leave the question with nothing wrong to tap.
        choices = choices ?: listOf("saucepan", "ladle", "whisk"),
    )

    private fun outbox() = StudyAnswerOutbox(File(temp.root, "o.json"), ActiveAccount { "u1" })

    private fun writer() = DurableAnswerWriter(
        AnswerSubmitting { StudyAnswerResponse(ok = true) }, outbox(), backoff = {},
    )

    private fun queues(queue: List<StudyQueueItem>) = object : StudyQueueReading {
        override suspend fun queue(
            mode: StudyMode, limit: Int, new: Int,
            categories: List<String>, lang: String, learning: LearningDirection,
        ) = StudyQueueResponse(queue = queue)
    }

    private fun newFlow(queue: List<StudyQueueItem>) = NewFlowViewModel(
        queues = queues(queue),
        writer = writer(),
        direction = LearningDirection.ZH_EN,
        uiLang = "zh-Hant",
        pool = { emptyList<Word>() },
        nowMs = { 0L },
        scope = TestScope(dispatcher),
        haptics = haptics,
    )

    private fun review(queue: List<StudyQueueItem>) = ReviewViewModel(
        queues = queues(queue),
        writer = writer(),
        direction = LearningDirection.ZH_EN,
        uiLang = "zh-Hant",
        pool = { emptyList<Word>() },
        online = { false },
        nowMs = { 0L },
        scope = TestScope(dispatcher),
        haptics = haptics,
    )

    private fun NewFlowViewModel.studying() = state.value as NewFlowViewModel.State.Studying
    private fun ReviewViewModel.studying() = state.value as ReviewViewModel.State.Studying

    /** Walk 認識 so the next stage is 選字, and forget the taps it cost. */
    private fun NewFlowViewModel.toIdentify() {
        rateRecognize(SRSRating.Hard)
        haptics.taps.clear()
    }

    // 學新字

    @Test fun `rating 認識 is felt at the tap, not after the beat`() = runTest(dispatcher) {
        val vm = newFlow(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()

        vm.rateRecognize(SRSRating.Hard)
        // Before any beat is allowed to run: the card is still on screen and
        // the tap is what is being answered.
        assertEquals(listOf(Tap.Success), haptics.taps)
    }

    @Test fun `a right 選字 clears the stage, and says so when the stage clears`() =
        runTest(dispatcher) {
            val vm = newFlow(listOf(item("kettle", "kettle")))
            vm.load(); advanceUntilIdle()
            vm.toIdentify(); advanceUntilIdle()

            vm.pickIdentify("kettle")
            assertEquals("the beat has not run yet", emptyList<Tap>(), haptics.taps)
            advanceUntilIdle()
            assertEquals(listOf(Tap.Success), haptics.taps)
        }

    @Test fun `a wrong 選字 warns now, because the answer is already on screen`() =
        runTest(dispatcher) {
            val vm = newFlow(listOf(item("kettle", "kettle")))
            vm.load(); advanceUntilIdle()
            vm.toIdentify(); advanceUntilIdle()

            val stage = vm.studying().stage as NewFlowViewModel.Stage.Identify
            vm.pickIdentify(stage.choices.first { it != "kettle" })
            assertEquals(listOf(Tap.Warning), haptics.taps)

            // 下一題 requeues the task. It is not a second verdict on the
            // answer the user has just finished reading.
            advanceUntilIdle()
            vm.continueFromWrong(); advanceUntilIdle()
            assertEquals(listOf(Tap.Warning), haptics.taps)
        }

    @Test fun `every tile lands softly and only the last one is a verdict`() = runTest(dispatcher) {
        val vm = newFlow(listOf(item("ox", "ox")))
        vm.load(); advanceUntilIdle()
        vm.toIdentify(); advanceUntilIdle()
        vm.pickIdentify("ox"); advanceUntilIdle()
        haptics.taps.clear()

        val spell = vm.studying().stage as NewFlowViewModel.Stage.Spell
        val order = spell.board!!.orderedUnits.map { unit -> spell.pool.indexOf(unit) }
        order.forEach { vm.pickSpell(it) }

        assertEquals(
            "one per tile, and the success waits for the beat",
            List(order.size) { Tap.Soft },
            haptics.taps,
        )
        advanceUntilIdle()
        assertEquals(List(order.size) { Tap.Soft } + Tap.Success, haptics.taps)
    }

    @Test fun `退一格 is a tile moving, so it lands like one`() = runTest(dispatcher) {
        val vm = newFlow(listOf(item("ox", "ox")))
        vm.load(); advanceUntilIdle()
        vm.toIdentify(); advanceUntilIdle()
        vm.pickIdentify("ox"); advanceUntilIdle()
        haptics.taps.clear()

        vm.pickSpell(0)
        vm.undoSpell()
        assertEquals(listOf(Tap.Soft, Tap.Soft), haptics.taps)
    }

    // 複習

    @Test fun `ruling an option out is firm, and a repeat of it is nothing`() = runTest(dispatcher) {
        val vm = review(listOf(item("kettle", "kettle")))
        vm.load(StudyMode.Review); advanceUntilIdle()
        assertEquals(ReviewQuestionKind.PickWord, vm.studying().session.question!!.kind)

        val wrong = vm.studying().choices.first { it != "kettle" }
        vm.pick(wrong)
        assertEquals(listOf(Tap.Firm), haptics.taps)

        // The row is disabled once it is out; a tap that gets through anyway
        // must not report a mistake that was already made.
        vm.pick(wrong)
        assertEquals(listOf(Tap.Firm), haptics.taps)
    }

    @Test fun `a question that resolves wrong is firm, and the rating after it is soft`() =
        runTest(dispatcher) {
            val vm = review(listOf(item("kettle", "kettle")))
            vm.load(StudyMode.Review); advanceUntilIdle()

            vm.pick(vm.studying().choices.first { it != "kettle" })
            haptics.taps.clear()

            // Right in the end, but not on the first attempt — the verdict the
            // hand should feel is the question's, not this tap's.
            vm.pick("kettle")
            assertEquals(listOf(Tap.Firm), haptics.taps)

            advanceUntilIdle()
            assertEquals(ReviewRevealMode.Rate, vm.studying().revealMode)
            vm.rate(SRSRating.Again)
            assertEquals(listOf(Tap.Firm, Tap.Soft), haptics.taps)
        }

    @Test fun `a question that resolves right is soft`() = runTest(dispatcher) {
        val vm = review(listOf(item("kettle", "kettle")))
        vm.load(StudyMode.Review); advanceUntilIdle()

        vm.pick("kettle")
        assertEquals(listOf(Tap.Soft), haptics.taps)
    }
}
