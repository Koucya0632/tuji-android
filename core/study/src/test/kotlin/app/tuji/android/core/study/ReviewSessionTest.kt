package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A whole 複習 session, walked synchronously.
 *
 * That it *can* be walked synchronously is the point: on iOS the same rules
 * live in a coordinator with haptics, audio, beats and a network writer in it,
 * and its tests need an injected clock and an injected beat scheduler just to
 * reach the slow-answer branch.
 */
class ReviewSessionTest {

    private val t0 = 1_000_000L

    private fun item(id: String, term: String = id, mastery: Int? = null) = StudyQueueItem(
        card = StudyCard(id = "card-$id"),
        word = StudyQueueWord(
            id = id, word = term, chinese = "—",
            imageUrl = "https://img.test/$id.webp", pronunciation = "—",
            category = "misc",
        ),
        mastery = mastery,
    )

    private fun session(vararg ids: String) =
        ReviewSession(ids.map { item(it) }, nowMs = t0).present(ReviewQuestionKind.PickWord)

    // Requeue

    @Test
    fun `a wrong first answer requeues the word once, at the tail`() {
        var s = session("a", "b")
        // 選字: rule one out, then land on the answer — that is what "wrong" is.
        s = s.pick("wrong", nowMs = t0 + 500).session
        val settled = s.pick("a", nowMs = t0 + 1_000)
        assertTrue(settled.outcome is ReviewOutcome.Reveal)

        val rated = settled.session.rate(SRSRating.Again)
        assertEquals(listOf("a", "b", "a"), rated.session.queue.map { it.word.id })
        assertTrue("a" in rated.session.retriedIds)
        assertEquals("a wrong answer is not done with", 0, rated.session.passedCount)
    }

    @Test
    fun `a correct first answer passes straight through`() {
        val settled = session("a", "b").pick("a", nowMs = t0 + 1_000)
        assertTrue(settled.outcome is ReviewOutcome.Flash)
        assertEquals(2, settled.session.queue.size)
        assertTrue(settled.session.retriedIds.isEmpty())
    }

    @Test
    fun `a retest never requeues again`() {
        // Its wrong path resolves to ContinueOnly, and `rate` is only open for
        // Rate — so the second requeue is unreachable by construction.
        var s = session("a")
        s = s.pick("wrong", nowMs = t0).session.pick("a", nowMs = t0 + 1_000).session
        s = s.rate(SRSRating.Again).session
        s = s.advance(nowMs = t0 + 2_000)

        assertTrue("the tail copy is a retest", s.question!!.isRetest)
        val retestWrong = s.pick("wrong", nowMs = t0 + 2_500).session
            .pick("a", nowMs = t0 + 3_000)
        assertEquals(
            ReviewOutcome.Reveal(ReviewRevealMode.ContinueOnly),
            retestWrong.outcome,
        )
        assertNull("a retest never writes again", retestWrong.write)
        assertEquals(2, retestWrong.session.queue.size)
    }

    // Writes

    @Test
    fun `an auto-rated answer owes exactly one write`() {
        val step = session("a").pick("a", nowMs = t0 + 1_000)
        val write = step.write
        assertNotNull(write)
        assertEquals("a", write!!.wordId)
        assertEquals("card-a", write.payload.cardId)
        assertEquals(SRSRating.Good, write.payload.rating)
    }

    @Test
    fun `a manual rating owes one write and only one`() {
        val settled = session("a").pick("a", nowMs = t0 + 9_000) // slow → sheet
        assertNull("nothing is owed before the rating", settled.write)

        val first = settled.session.rate(SRSRating.Hard)
        assertNotNull(first.write)
        assertNull("a second rating is refused", first.session.rate(SRSRating.Easy).write)
    }

    @Test
    fun `a retest that passes owes nothing`() {
        // The first attempt's 重來 already rescheduled the word.
        var s = session("a")
        s = s.pick("wrong", nowMs = t0).session.pick("a", nowMs = t0 + 1_000).session
        s = s.rate(SRSRating.Again).session.advance(nowMs = t0 + 2_000)

        val passed = s.pick("a", nowMs = t0 + 2_500)
        assertEquals(ReviewOutcome.Flash(ReviewFlash.RetestPassed), passed.outcome)
        assertNull(passed.write)
    }

    @Test
    fun `the session's listening opt-out rides on every write after it`() {
        var s = ReviewSession(listOf(item("a")), nowMs = t0).present(
            kind = ReviewQuestionKind.HearSentence,
            example = StudyExample("A sentence."),
            imageOptions = listOf(ImageChoiceOption("a", "a", "u")),
            awaitsAudio = false,
        )
        s = s.optOutOfListening(nowMs = t0 + 1_000)
        assertTrue(s.listeningOptedOut)

        val step = s.pick("a", nowMs = t0 + 2_000)
        assertEquals(true, step.write!!.payload.listeningOptedOut)
        assertEquals("mcq", step.write.payload.activity)
    }

    // Progress

    @Test
    fun `the bar counts distinct words, so a retest cannot push it backwards`() {
        var s = session("a", "b")
        s = s.pick("wrong", nowMs = t0).session.pick("a", nowMs = t0 + 500).session
        s = s.rate(SRSRating.Again).session      // requeued, not passed
        assertEquals(0, s.passedCount)
        assertEquals(2, s.originalCount)

        s = s.advance(nowMs = t0 + 1_000)        // → b
        s = s.pick("b", nowMs = t0 + 1_200).session
        assertEquals(1, s.passedCount)

        s = s.advance(nowMs = t0 + 2_000)        // → a again, as a retest
        s = s.pick("a", nowMs = t0 + 2_200).session
        assertEquals(2, s.passedCount)
        assertEquals("never over 1", 1.0, s.progress, 0.0001)
    }

    @Test
    fun `a word is counted once even if it settles twice`() {
        val settled = session("a").pick("a", nowMs = t0 + 1_000).session
        assertEquals(1, settled.passedCount)
        // The rating arrives for an already-counted presentation.
        assertEquals(1, settled.rate(SRSRating.Good).session.passedCount)
    }

    @Test
    fun `revealing gives the bar a half step`() {
        val s = session("a", "b").pick("a", nowMs = t0 + 9_000).session
        assertEquals(ReviewPhase.Review, s.question!!.phase)
        assertEquals(0.5 / 2, s.progress, 0.0001)
    }

    // The completion list

    @Test
    fun `each word appears once on the completion list, even re-tested`() {
        var s = session("a")
        s = s.pick("wrong", nowMs = t0).session.pick("a", nowMs = t0 + 500).session
        s = s.rate(SRSRating.Again).session.advance(nowMs = t0 + 1_000)
        s = s.pick("a", nowMs = t0 + 1_500).session
        assertEquals(listOf("a"), s.answered.map { it.word.id })
    }

    // Advancing

    @Test
    fun `advancing resets everything the previous card was holding`() {
        // The whole per-item reset: a new value starts at its start values, so
        // there is no list of fields to keep in step.
        var s = session("a", "b")
        s = s.withQuestion(s.question!!.toggleHint())
        s = s.pick("wrong", nowMs = t0).session
        assertTrue(s.question!!.hinted)
        assertTrue(s.question!!.wrongPicks.isNotEmpty())

        s = s.pick("a", nowMs = t0 + 500).session.rate(SRSRating.Again).session
        s = s.advance(nowMs = t0 + 1_000)
        assertFalse(s.question!!.hinted)
        assertTrue(s.question!!.wrongPicks.isEmpty())
        assertEquals(ReviewPhase.Answer, s.question!!.phase)
        assertNull(s.question!!.rated)
    }

    @Test
    fun `the option variant bumps each time a word leaves the screen`() {
        // Otherwise remembering "the answer was C" stands in for the word.
        val subject = item("a")
        var s = session("a", "b")
        assertEquals(0, s.choicesVariant(subject))
        s = s.pick("a", nowMs = t0 + 500).session.advance(nowMs = t0 + 1_000)
        assertEquals(1, s.choicesVariant(subject))
    }

    @Test
    fun `the previous kind outlives its card`() {
        // "No two 聽句 in a row" is the one piece of per-item state whose whole
        // job is to survive the rebuild.
        var s = ReviewSession(listOf(item("a"), item("b")), nowMs = t0).present(
            kind = ReviewQuestionKind.HearSentence,
            example = StudyExample("A sentence."),
            imageOptions = listOf(ImageChoiceOption("a", "a", "u")),
        )
        s = s.advance(nowMs = t0 + 1_000)
        assertEquals(ReviewQuestionKind.HearSentence, s.previousKind)
        assertTrue("a" in s.heardWordIds)
    }

    @Test
    fun `the session finishes after the last card`() {
        var s = session("a")
        s = s.pick("a", nowMs = t0 + 500).session
        assertFalse(s.finished)
        s = s.advance(nowMs = t0 + 1_000)
        assertTrue(s.finished)
    }

    @Test
    fun `an empty queue is finished before it starts`() {
        val s = ReviewSession(emptyList(), nowMs = t0)
        assertTrue(s.finished)
        assertNull(s.question)
        assertEquals(0.0, s.progress, 0.0001)
        // And nothing it is asked to do throws.
        assertEquals(ReviewOutcome.Nothing, s.pick("x", nowMs = t0).outcome)
        assertNull(s.rate(SRSRating.Good).write)
    }

    @Test
    fun `a requeued word extends the queue but not the denominator`() {
        var s = session("a", "b")
        s = s.pick("wrong", nowMs = t0).session.pick("a", nowMs = t0 + 500).session
        s = s.rate(SRSRating.Again).session
        assertEquals(3, s.queue.size)
        assertEquals("the bar's denominator does not move", 2, s.originalCount)
    }

    @Test
    fun `ruling out an option leaves the question open and owes nothing`() {
        val step = session("a", "b").pick("wrong", nowMs = t0 + 500)
        assertEquals(ReviewOutcome.RuledOut, step.outcome)
        assertNull(step.write)
        assertEquals(ReviewPhase.Answer, step.session.question!!.phase)
        assertEquals(0, step.session.passedCount)
    }
}
