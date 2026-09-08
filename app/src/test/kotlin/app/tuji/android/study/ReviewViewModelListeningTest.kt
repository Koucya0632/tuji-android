package app.tuji.android.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyExample
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
import app.tuji.android.core.study.ListeningQuestion
import app.tuji.android.core.study.SentencePlayback
import app.tuji.android.core.study.SentencePlaying
import app.tuji.android.core.study.StudyAnswerOutbox
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Where the two pure decisions meet the performing half.
 *
 * [ListeningQuestion] and [app.tuji.android.core.study.ImageChoicePair] are
 * tested on their own; what only shows up here is whether the view model
 * actually *asks* them — the shape of bug that had `AnswerDrainWorker` enqueued
 * at launch and nowhere else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelListeningTest {

    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** A word id that the hash puts on a 聽句 slot, and one that it does not. */
    private val slotted = (0 until 500).map { "w$it" }.first { ListeningQuestion.fallsOnSlot(it) }
    private val offSlot = (0 until 500).map { "w$it" }.first { !ListeningQuestion.fallsOnSlot(it) }

    private fun example(level: String) = StudyExample(
        sentence = "窓を開けます。",
        cefrLevel = level,
        audioUrls = mapOf("ja-JP" to "https://clips.test/$level.mp3"),
        mentionedWordIds = listOf("answer"),
    )

    private fun item(id: String, examples: List<StudyExample>? = listOf(example("A2"), example("B1"))) =
        StudyQueueItem(
            card = StudyCard(id = "c-$id"),
            word = StudyQueueWord(
                id = id, word = "窓", chinese = "窗戶",
                imageUrl = "https://img.test/$id.webp",
                pronunciation = "", category = "bedroom",
                targetLanguage = TargetLanguage.JA,
            ),
            examples = examples,
        )

    /** A catalogue rich enough to yield a fair image distractor. */
    private val pool = (0 until 10).map {
        Word(
            id = "pool$it", word = "語$it", chinese = "詞$it",
            imageUrl = "https://img.test/pool$it.webp",
            category = "bedroom", targetLanguage = TargetLanguage.JA,
        )
    }

    private class FakePlayer(
        val playable: Boolean = true,
        val outcome: SentencePlayback = SentencePlayback.Finished,
    ) : SentencePlaying {
        var plays = 0
        var stops = 0
        var lastRate: Float? = null
        /** Held open so a test can assert on the state *while* it plays. */
        var gate: CompletableDeferred<Unit>? = null

        override fun canPlay(url: String?, online: Boolean) = playable && !url.isNullOrBlank()
        override suspend fun play(url: String?, rate: Float): SentencePlayback {
            plays += 1
            lastRate = rate
            gate?.await()
            return outcome
        }
        override fun stop() { stops += 1 }
    }

    private fun vm(
        queue: List<StudyQueueItem>,
        audio: SentencePlaying = FakePlayer(),
        online: Boolean = true,
        pool: List<Word> = this.pool,
    ): ReviewViewModel {
        val queues = object : StudyQueueReading {
            override suspend fun queue(
                mode: StudyMode, limit: Int, new: Int,
                categories: List<String>, lang: String, learning: LearningDirection,
            ) = StudyQueueResponse(queue = queue)
        }
        val outbox = StudyAnswerOutbox(File(temp.root, "o.json"), ActiveAccount { "u1" })
        return ReviewViewModel(
            queues = queues,
            writer = DurableAnswerWriter(
                AnswerSubmitting { StudyAnswerResponse(ok = true) }, outbox, backoff = {},
            ),
            direction = LearningDirection.ZH_JA,
            uiLang = "zh-Hant",
            pool = { pool },
            audio = audio,
            online = { online },
            nowMs = { 0L },
            scope = TestScope(dispatcher),
        )
    }

    private fun ReviewViewModel.studying() = state.value as ReviewViewModel.State.Studying

    @Test fun `a slotted card with a clip is asked as 聽句`() = runTest(dispatcher) {
        val vm = vm(listOf(item(slotted)))
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        val q = vm.studying().session.question!!
        assertEquals(ReviewQuestionKind.HearSentence, q.kind)
        assertEquals("窓を開けます。", q.example?.sentence)
        assertEquals("two pictures or it is not this question", 2, q.imageOptions?.size)
    }

    @Test fun `an off-slot card stays 選字`() = runTest(dispatcher) {
        val vm = vm(listOf(item(offSlot)))
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        assertEquals(ReviewQuestionKind.PickWord, vm.studying().session.question!!.kind)
    }

    @Test fun `offline with nothing cached asks 選字, not an unanswerable question`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item(slotted)), audio = FakePlayer(playable = false))
            vm.load(StudyMode.Review)
            advanceUntilIdle()
            val q = vm.studying().session.question!!
            assertEquals(ReviewQuestionKind.PickWord, q.kind)
            assertNull(q.example)
        }

    @Test fun `a card with no sentence at all falls back`() = runTest(dispatcher) {
        val vm = vm(listOf(item(slotted, examples = null)))
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        assertEquals(ReviewQuestionKind.PickWord, vm.studying().session.question!!.kind)
    }

    @Test fun `a pool with no fair distractor falls back rather than showing one picture`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item(slotted)), pool = emptyList())
            vm.load(StudyMode.Review)
            advanceUntilIdle()
            val q = vm.studying().session.question!!
            assertEquals(ReviewQuestionKind.PickWord, q.kind)
            assertNull(q.imageOptions)
        }

    @Test fun `the clip plays itself, without being asked`() = runTest(dispatcher) {
        val player = FakePlayer()
        val vm = vm(listOf(item(slotted)), audio = player)
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        assertEquals(1, player.plays)
    }

    @Test fun `the card is answerable while the sentence is still playing`() = runTest(dispatcher) {
        val player = FakePlayer().apply { gate = CompletableDeferred() }
        val vm = vm(listOf(item(slotted)), audio = player)
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        val q = vm.studying().session.question!!
        assertTrue("the card is drawn before the audio finishes", q.ready)
        assertTrue(q.isPlayingSentence)
        assertTrue("only the clock waits", q.awaitingAudio)

        player.gate!!.complete(Unit)
        advanceUntilIdle()
        val after = vm.studying().session.question!!
        assertFalse(after.isPlayingSentence)
        assertFalse("the clock starts when the sentence stops", after.awaitingAudio)
    }

    @Test fun `慢讀 asks for 0_8 and counts as a replay`() = runTest(dispatcher) {
        val player = FakePlayer()
        val vm = vm(listOf(item(slotted)), audio = player)
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        vm.replaySentence(rate = 0.8f)
        advanceUntilIdle()
        assertEquals(2, player.plays)
        assertEquals(0.8f, player.lastRate!!, 0.001f)
        assertEquals(1, vm.studying().session.question!!.replayCount)
    }

    @Test fun `a replay does not restart the clock`() = runTest(dispatcher) {
        val player = FakePlayer()
        val vm = vm(listOf(item(slotted)), audio = player)
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        val started = vm.studying().session.question!!.startedAtMs

        vm.replaySentence()
        advanceUntilIdle()
        assertEquals(
            "a replay must not be a way to buy time",
            started, vm.studying().session.question!!.startedAtMs,
        )
    }

    @Test fun `這輪不做聽句題 converts the card and silences the clip`() = runTest(dispatcher) {
        val player = FakePlayer()
        val vm = vm(listOf(item(slotted)), audio = player)
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        vm.optOutOfListening()
        advanceUntilIdle()
        val q = vm.studying().session.question!!
        assertEquals(ReviewQuestionKind.PickWord, q.kind)
        assertNull(q.example)
        assertTrue("audio nobody asked to start must stop", player.stops >= 1)
        assertTrue(vm.studying().session.listeningOptedOut)
    }

    @Test fun `the eye lifts the blur and is recorded as a hint`() = runTest(dispatcher) {
        val vm = vm(listOf(item(slotted)))
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        vm.revealSentence()
        val q = vm.studying().session.question!!
        assertTrue(q.sentenceRevealed)
        assertTrue("it costs the wrong-answer rating table", q.hinted)
    }

    @Test fun `picking a picture resolves on the first tap and never auto-rates`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item(slotted)))
            vm.load(StudyMode.Review)
            advanceUntilIdle()

            val option = vm.studying().session.question!!.imageOptions!!.first { it.id == slotted }
            vm.pickImage(option)
            advanceUntilIdle()

            // Correct, instant — and still a sheet, because one of two pictures
            // is a coin flip and "unambiguous" is not true of it.
            assertEquals(
                app.tuji.android.core.study.ReviewRevealMode.Rate,
                vm.studying().revealMode,
            )
        }

    @Test fun `leaving stops the audio`() = runTest(dispatcher) {
        val player = FakePlayer()
        val vm = vm(listOf(item(slotted)), audio = player)
        vm.load(StudyMode.Review)
        advanceUntilIdle()

        vm.leave()
        assertTrue("must not narrate the screen the user went to", player.stops >= 1)
    }

    @Test fun `the payload records the listening activity`() = runTest(dispatcher) {
        val vm = vm(listOf(item(slotted)))
        vm.load(StudyMode.Review)
        advanceUntilIdle()
        val q = vm.studying().session.question!!
        assertNotNull(q.example)
        assertEquals("listening", q.kind.activity)
    }
}
