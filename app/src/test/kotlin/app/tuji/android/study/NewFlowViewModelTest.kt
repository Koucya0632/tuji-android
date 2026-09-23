package app.tuji.android.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.Milestone
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
import app.tuji.android.core.study.NewStageStep
import app.tuji.android.core.study.NewTaskKind
import app.tuji.android.core.study.SpellForm
import app.tuji.android.core.study.StudyQuotas
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

    /** What the last queue request asked for: (limit, new, categories). */
    private var asked: Triple<Int, Int, List<String>>? = null

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

    /** What the server attaches to the next answer. */
    private var respondWith = StudyAnswerResponse(ok = true)

    private fun vm(queue: List<StudyQueueItem>): NewFlowViewModel {
        val queues = object : StudyQueueReading {
            override suspend fun queue(
                mode: StudyMode, limit: Int, new: Int,
                categories: List<String>, lang: String, learning: LearningDirection,
            ): StudyQueueResponse {
                assertEquals("學新字 must ask for the new queue", StudyMode.New, mode)
                asked = Triple(limit, new, categories)
                return StudyQueueResponse(queue = queue)
            }
        }
        val outbox = StudyAnswerOutbox(File(temp.root, "o.json"), ActiveAccount { "u1" })
        val submit = AnswerSubmitting { posted += it; respondWith }
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

    /**
     * What the board wants, slot by slot — the tiles in the board's own order,
     * or the chunks the gap-fill cut out. One helper for both, because which
     * board a word takes is [SpellForm]'s call and the tests should not have to
     * know which one they got.
     */
    private fun NewFlowViewModel.Stage.Spell.wanted(): List<String> = when (val f = form) {
        is SpellForm.Gaps -> f.plan.answers
        is SpellForm.Tiles -> f.board.orderedUnits
    }

    private fun NewFlowViewModel.solveSpell() {
        (stage() as NewFlowViewModel.Stage.Spell).wanted().forEach { unit ->
            val s = stage() as NewFlowViewModel.Stage.Spell
            pickSpell(s.pool.indices.first { it !in s.picks && s.pool[it] == unit })
        }
    }

    /** Fill every slot with something that does not belong in it. */
    private fun NewFlowViewModel.failSpell() {
        (stage() as NewFlowViewModel.Stage.Spell).wanted().forEach { unit ->
            val s = stage() as NewFlowViewModel.Stage.Spell
            pickSpell(
                s.pool.indices.firstOrNull { it !in s.picks && s.pool[it] != unit }
                    ?: s.pool.indices.first { it !in s.picks },
            )
        }
    }

    /** Android asked for a fixed 5 with no themes, whatever 設定 said. */
    @Test fun `the queue is asked for the size and themes it was given`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(StudyQuotas.NewQueue(limit = 10, categories = listOf("kitchen"))); advanceUntilIdle()
        assertEquals(Triple(10, 10, listOf("kitchen")), asked)
    }

    @Test fun `the dots follow the word through its stages`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        assertEquals(NewStageStep.State.Active, vm.studying().steps.first { it.kind == NewTaskKind.Recognize }.state)

        vm.rateRecognize(SRSRating.Hard); advanceUntilIdle()
        val steps = vm.studying().steps.associate { it.kind to it.state }
        assertEquals(NewStageStep.State.Done, steps[NewTaskKind.Recognize])
        assertEquals(NewStageStep.State.Active, steps[NewTaskKind.Identify])
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
        assertTrue("but the tiles still gate the write", ladder.tasks.any { it.kind == NewTaskKind.Spell })
    }

    /** The server marks the answer that crosses a streak threshold; the finished screen is drawn from it. */
    @Test fun `a milestone on an answer is kept for the finished screen`() = runTest(dispatcher) {
        respondWith = StudyAnswerResponse(ok = true, milestone = Milestone(streak = 30))
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        assertEquals(null, vm.milestone.value)
        vm.rateRecognize(SRSRating.Good); advanceUntilIdle()
        vm.solveSpell(); advanceUntilIdle()
        assertTrue(vm.state.value is NewFlowViewModel.State.Done)
        assertEquals(30, vm.milestone.value?.streak)
    }

    @Test fun `a clean run posts the self-rating once, when the word is done`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item("kettle", "kettle")))
            vm.load(); advanceUntilIdle()
            vm.rateRecognize(SRSRating.Good); advanceUntilIdle()
            vm.solveSpell(); advanceUntilIdle()

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
                is NewFlowViewModel.Stage.Spell -> vm.solveSpell()
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
                is NewFlowViewModel.Stage.Spell -> vm.solveSpell()
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

        val first = (vm.stage() as NewFlowViewModel.Stage.Spell).pool
        vm.failSpell()
        advanceUntilIdle()
        assertEquals(false, (vm.stage() as NewFlowViewModel.Stage.Spell).correct)

        vm.continueFromWrong(); advanceUntilIdle()
        val second = (vm.stage() as NewFlowViewModel.Stage.Spell).pool
        assertTrue("staring at the same layout again is not a retry", first != second)
    }

    @Test fun `undo takes the last one back and only while assembling`() = runTest(dispatcher) {
        // refrigerator is long enough for three slots, so two picks leave the
        // board unfinished — on a two-slot board the second pick locks it and
        // there is nothing left to undo.
        val vm = vm(listOf(item("fridge", "refrigerator")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

        vm.pickSpell(0); vm.pickSpell(1)
        assertEquals(2, (vm.stage() as NewFlowViewModel.Stage.Spell).picks.size)
        vm.undoSpell()
        assertEquals(1, (vm.stage() as NewFlowViewModel.Stage.Spell).picks.size)
    }

    @Test fun `tapping a filled slot takes that one out and the rest shift up`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item("fridge", "refrigerator")))
            vm.load(); advanceUntilIdle()
            vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

            vm.pickSpell(0); vm.pickSpell(1)
            // iOS's `unpickSpell(atSlot:)`: the picks *are* the slots, so
            // removing the first moves the second into slot 0.
            vm.unpickSpell(0)
            assertEquals(listOf(1), (vm.stage() as NewFlowViewModel.Stage.Spell).picks)
        }

    @Test fun `the same option cannot be spent twice`() = runTest(dispatcher) {
        val vm = vm(listOf(item("kettle", "kettle")))
        vm.load(); advanceUntilIdle()
        vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

        vm.pickSpell(0); vm.pickSpell(0)
        assertEquals(1, (vm.stage() as NewFlowViewModel.Stage.Spell).picks.size)
    }

    @Test fun `an English word gets the gap-fill and a kana reading gets the tiles`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item("kettle", "kettle")))
            vm.load(); advanceUntilIdle()
            vm.rateRecognize(SRSRating.Good); advanceUntilIdle()
            val english = vm.stage() as NewFlowViewModel.Stage.Spell
            assertTrue("English spells into holes", english.form is SpellForm.Gaps)
            // The pool carries distractors, so it is longer than the slots —
            // which is exactly why `isFull` has to ask the form and not the pool.
            assertTrue(english.pool.size > english.form.slotCount)

            val ja = vm(listOf(item("neko", "ねこ")))
            ja.load(); advanceUntilIdle()
            ja.rateRecognize(SRSRating.Good); advanceUntilIdle()
            val kana = ja.stage() as NewFlowViewModel.Stage.Spell
            assertTrue("kana has no confusables to cut", kana.form is SpellForm.Tiles)
            assertEquals(kana.pool.size, kana.form.slotCount)
        }

    @Test fun `a gap-fill wants each chunk in its own slot, not merely the right set`() =
        runTest(dispatcher) {
            val vm = vm(listOf(item("fridge", "refrigerator")))
            vm.load(); advanceUntilIdle()
            vm.rateRecognize(SRSRating.Good); advanceUntilIdle()

            val spell = vm.stage() as NewFlowViewModel.Stage.Spell
            val answers = (spell.form as SpellForm.Gaps).plan.answers
            // The right chunks, deliberately in the wrong order. Joining the
            // picks — which is how a tile board decides — would accept this.
            answers.reversed().forEach { unit ->
                val s = vm.stage() as NewFlowViewModel.Stage.Spell
                vm.pickSpell(s.pool.indices.first { it !in s.picks && s.pool[it] == unit })
            }
            advanceUntilIdle()
            assertEquals(false, (vm.stage() as NewFlowViewModel.Stage.Spell).correct)
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
                is NewFlowViewModel.Stage.Spell -> vm.solveSpell()
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
                is NewFlowViewModel.Stage.Spell -> vm.solveSpell()
                is NewFlowViewModel.Stage.Recognize -> vm.rateRecognize(SRSRating.Good)
            }
            advanceUntilIdle()
        }
        assertEquals("a word writes exactly one row", 1, posted.size)
        assertEquals("and three contradictions is 重來", SRSRating.Again, posted[0].rating)
    }
}
