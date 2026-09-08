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
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.study.ActiveAccount
import app.tuji.android.core.study.AnswerSubmitting
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.NewTaskKind
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
 * Where the ladder's ordering meets the write it is holding back.
 *
 * [app.tuji.android.core.study.StudyLadder] and
 * [app.tuji.android.core.study.LearnedRating] are tested on their own; what
 * only appears here is *when* the write fires and *what* it carries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewFlowViewModelTest {

    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val posted = mutableListOf<StudyAnswerPayload>()

    private fun item(id: String, word: String, choices: List<String> = listOf("x1", "x2", "x3")) =
        StudyQueueItem(
            card = StudyCard(id = "card-$id"),
            word = StudyQueueWord(
                id = id, word = word, chinese = "詞-$id",
                imageUrl = "https://img.test/$id.webp",
                pronunciation = "", category = "kitchen",
                targetLanguage = TargetLanguage.EN,
            ),
            choices = choices,
        )

    private fun vm(queue: List<StudyQueueItem>): NewFlowViewModel {
        val queues = object : StudyQueueReading {
            override suspend fun queue(
                mode: StudyMode, limit: Int, new: Int,
                categories: List<String>, lang: String, learning: LearningDirection,
            ): StudyQueueResponse {
                assertEquals("學新字 must ask for the new queue", StudyMode.New, mode)
                return StudyQueueResponse(queue = queue)
            }
        }
        val outbox = StudyAnswerOutbox(File(temp.root, "o.json"), ActiveAccount { "u1" })
        val submit = AnswerSubmitting { posted += it; StudyAnswerResponse(ok = true) }
        return NewFlowViewModel(
            queues = queues,
            writer = DurableAnswerWriter(submit, outbox, backoff = {}),
            direction = LearningDirection.ZH_EN,
            uiLang = "zh-Hant",
            pool = { emptyList<Word>() },
            nowMs = { 0L },
            scope = TestScope(dispatcher),
        )
    }

    private fun NewFlowViewModel.studying() = state.value as NewFlowViewModel.State.Studying
    private fun NewFlowViewModel.stage() = studying().stage

    /** Solve the tiles in the board's own order. */
    private fun NewFlowViewModel.solveTiles() {
        val spell = stage() as NewFlowViewModel.Stage.Spell
        spell.board.orderedUnits.forEach { unit ->
            val s = stage() as NewFlowViewModel.Stage.Spell
            tapTile(s.tiles.indexOfFirst { it == unit && s.tiles.indexOf(it) !in s.picks }
                .let { if (it >= 0) it else s.tiles.indices.first { i -> i !in s.picks } })
        }
    }

    @Test fun `nothing is written at 認識 — the quiz still gets a vote`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()

        assertTrue(vm.stage() is NewFlowViewModel.Stage.Recognize)
        vm.rateRecognize(SRSRating.Hard)
        advanceUntilIdle()
        assertTrue("a bare 認識 tap is not a completion", posted.isEmpty())
    }

    @Test fun `已認識 drops the word's 選字`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Good)
        advanceUntilIdle()

        val ladder = vm.studying().ladder
        assertTrue("the fast path removes it", ladder.tasks.none { it.kind == NewTaskKind.Identify })
        assertTrue("but the tiles still gate the write", ladder.tasks.any { it.kind == NewTaskKind.SpellTiles })
    }

    @Test fun `a clean run posts the self-rating once, when the word is done`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item("kettle", "kettle")))
            vm.load(); advanceUntilIdle()
            vm.rateRecognize(SRSRating.Good); advanceUntilIdle()
            vm.solveTiles(); advanceUntilIdle()

            assertEquals(1, posted.size)
            assertEquals(SRSRating.Good, posted[0].rating)
            assertEquals("card-kettle", posted[0].cardId)
            assertEquals("new_recognize", posted[0].activity)
            assertTrue(vm.state.value is NewFlowViewModel.State.Done)
        }

    @Test fun `one wrong answer downgrades what the self-rating claimed`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Hard); advanceUntilIdle()   // keeps 選字

        val identify = vm.stage() as NewFlowViewModel.Stage.Identify
        vm.pickIdentify(identify.choices.first { it != "kettle" })
        advanceUntilIdle()
        assertTrue("the answer stays up until 下一題", (vm.stage() as NewFlowViewModel.Stage.Identify).revealed)

        vm.continueFromWrong(); advanceUntilIdle()
        // Walk the rest of the session.
        var guard = 0
        while (vm.state.value !is NewFlowViewModel.State.Done && guard++ < 30) {
            when (vm.stage()) {
                is NewFlowViewModel.Stage.Identify -> vm.pickIdentify("kettle")
                is NewFlowViewModel.Stage.Spell -> vm.solveTiles()
                is NewFlowViewModel.Stage.Recognize -> vm.rateRecognize(SRSRating.Good)
            }
            advanceUntilIdle()
        }
        assertEquals(1, posted.size)
        assertEquals("有印象 contradicted once drops to 重來", SRSRating.Again, posted[0].rating)
    }

    @Test fun `a wrong pick requeues the task rather than moving on`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle"), item("pan", "pan")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Hard); advanceUntilIdle()

        while (vm.stage() !is NewFlowViewModel.Stage.Identify) {
            when (val s = vm.stage()) {
                is NewFlowViewModel.Stage.Recognize -> vm.rateRecognize(SRSRating.Hard)
                is NewFlowViewModel.Stage.Spell -> vm.solveTiles()
                else -> Unit
            }
            advanceUntilIdle()
        }
        val before = vm.studying().ladder.tasks.size
        val identify = vm.stage() as NewFlowViewModel.Stage.Identify
        val answer = identify.item.word.word
        vm.pickIdentify(identify.choices.first { it != answer })
        advanceUntilIdle()
        vm.continueFromWrong(); advanceUntilIdle()

        assertEquals("a missed task is not consumed", before, vm.studying().ladder.tasks.size)
    }

    @Test fun `a retry gets a different scramble`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

        val first = (vm.stage() as NewFlowViewModel.Stage.Spell).tiles
        // Fill it wrong: reverse order is wrong for any board of 2+ distinct units.
        first.indices.reversed().forEach { vm.tapTile(it) }
        advanceUntilIdle()
        assertEquals(false, (vm.stage() as NewFlowViewModel.Stage.Spell).correct)

        vm.continueFromWrong(); advanceUntilIdle()
        val second = (vm.stage() as NewFlowViewModel.Stage.Spell).tiles
        assertTrue("staring at the same layout again is not a retry", first != second)
    }

    @Test fun `undo takes the last tile back and only while assembling`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

        vm.tapTile(0); vm.tapTile(1)
        assertEquals(2, (vm.stage() as NewFlowViewModel.Stage.Spell).picks.size)
        vm.undoTile()
        assertEquals(1, (vm.stage() as NewFlowViewModel.Stage.Spell).picks.size)
    }

    @Test fun `the same tile cannot be spent twice`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

        vm.tapTile(0); vm.tapTile(0)
        assertEquals(1, (vm.stage() as NewFlowViewModel.Stage.Spell).picks.size)
    }

    @Test fun `leaving mid-beat does not advance the session behind the user`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item("kettle", "kettle")))
            vm.load(); advanceUntilIdle()
            vm.rateRecognize(SRSRating.Good)
            vm.leave()
            advanceUntilIdle()

            assertTrue("still on 認識", vm.stage() is NewFlowViewModel.Stage.Recognize)
            assertTrue(posted.isEmpty())
        }

    @Test fun `an empty queue finishes instead of drawing nothing`() = runTest(dispatcher) {
        val vm = vm(emptyList())
        vm.load(); advanceUntilIdle()
        assertEquals(NewFlowViewModel.State.Done(0, 0), vm.state.value)
    }

    @Test fun `a load failure says so rather than spinning`() = runTest(dispatcher) {
        val queues = object : StudyQueueReading {
            override suspend fun queue(
                mode: StudyMode, limit: Int, new: Int,
                categories: List<String>, lang: String, learning: LearningDirection,
            ): StudyQueueResponse = throw java.io.IOException("no network")
        }
        val outbox = StudyAnswerOutbox(File(temp.root, "f.json"), ActiveAccount { "u1" })
        val vm = NewFlowViewModel(
            queues = queues,
            writer = DurableAnswerWriter(AnswerSubmitting { StudyAnswerResponse(ok = true) }, outbox, backoff = {}),
            direction = LearningDirection.ZH_EN, uiLang = "zh-Hant", pool = { emptyList() },
            nowMs = { 0L }, scope = TestScope(dispatcher),
        )
        vm.load(); advanceUntilIdle()
        assertTrue(vm.state.value is NewFlowViewModel.State.Failed)
    }

    @Test fun `the response time is the first attempt's, not the retry's`() = runTest(dispatcher) {
        var clock = 0L
        val queues = object : StudyQueueReading {
            override suspend fun queue(
                mode: StudyMode, limit: Int, new: Int,
                categories: List<String>, lang: String, learning: LearningDirection,
            ) = StudyQueueResponse(queue = listOf(item("kettle", "kettle")))
        }
        val outbox = StudyAnswerOutbox(File(temp.root, "t.json"), ActiveAccount { "u1" })
        val submit = AnswerSubmitting { posted += it; StudyAnswerResponse(ok = true) }
        val vm = NewFlowViewModel(
            queues = queues,
            writer = DurableAnswerWriter(submit, outbox, backoff = {}),
            direction = LearningDirection.ZH_EN, uiLang = "zh-Hant", pool = { emptyList() },
            nowMs = { clock }, scope = TestScope(dispatcher),
        )
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Hard); advanceUntilIdle()

        clock = 3_000
        val identify = vm.stage() as NewFlowViewModel.Stage.Identify
        vm.pickIdentify(identify.choices.first { it != "kettle" })
        advanceUntilIdle()
        vm.continueFromWrong(); advanceUntilIdle()

        clock = 99_000
        var guard = 0
        while (vm.state.value !is NewFlowViewModel.State.Done && guard++ < 30) {
            when (vm.stage()) {
                is NewFlowViewModel.Stage.Identify -> vm.pickIdentify("kettle")
                is NewFlowViewModel.Stage.Spell -> vm.solveTiles()
                is NewFlowViewModel.Stage.Recognize -> vm.rateRecognize(SRSRating.Good)
            }
            advanceUntilIdle()
        }
        assertEquals("a retry has already seen the answer", 3_000, posted[0].responseMs)
    }

    @Test fun `two words interleave rather than running as blocks`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle"), item("pan", "pan")))
        vm.load(); advanceUntilIdle()
        val order = vm.studying().ladder.tasks.map { "${it.item.word.id}:${it.kind}" }
        assertTrue(
            "a word's quiz must not echo the card just shown — got $order",
            order.take(3).map { it.substringBefore(':') }.toSet().size > 1,
        )
    }

    @Test fun `nothing is posted twice however often a stage is requeued`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Hard); advanceUntilIdle()
        repeat(3) {
            val s = vm.stage()
            if (s is NewFlowViewModel.Stage.Identify) {
                vm.pickIdentify(s.choices.first { c -> c != "kettle" }); advanceUntilIdle()
                vm.continueFromWrong(); advanceUntilIdle()
            }
        }
        var guard = 0
        while (vm.state.value !is NewFlowViewModel.State.Done && guard++ < 30) {
            when (vm.stage()) {
                is NewFlowViewModel.Stage.Identify -> vm.pickIdentify("kettle")
                is NewFlowViewModel.Stage.Spell -> vm.solveTiles()
                is NewFlowViewModel.Stage.Recognize -> vm.rateRecognize(SRSRating.Good)
            }
            advanceUntilIdle()
        }
        assertEquals("a word writes exactly one row", 1, posted.size)
        assertEquals("and three contradictions is 重來", SRSRating.Again, posted[0].rating)
    }
}
