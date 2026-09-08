package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cases the iOS suite pins, because this type decides what a session does
 * with an answer — the rating, whether a sheet opens, what reaches SRS. A
 * divergence is two apps grading the same tap differently.
 */
class ReviewQuestionTest {

    private val t0 = 1_000_000L

    private fun item(id: String = "w", term: String = "cat", mastery: Int? = null) = StudyQueueItem(
        card = StudyCard(id = "card-$id"),
        word = StudyQueueWord(
            id = id, word = term, chinese = "貓",
            imageUrl = "https://img.test/$id.webp", pronunciation = "/kæt/",
            category = "animals",
        ),
        mastery = mastery,
    )

    private fun question(
        mastery: Int? = null,
        isRetest: Boolean = false,
        term: String = "cat",
    ) = ReviewQuestion(item = item(term = term, mastery = mastery), isRetest = isRetest, startedAtMs = t0)
        .present(ReviewQuestionKind.PickWord)

    private val sentence = StudyExample(sentence = "The cat sat.", cefrLevel = "A2")

    private fun listening() = ReviewQuestion(item = item(), isRetest = false, startedAtMs = t0)
        .present(
            kind = ReviewQuestionKind.HearSentence,
            example = sentence,
            imageOptions = listOf(
                ImageChoiceOption("w", "cat", "https://img.test/w.webp"),
                ImageChoiceOption("other", "dog", "https://img.test/other.webp"),
            ),
            awaitsAudio = true,
        )

    // The clock

    @Test
    fun `the answer is timed once and the payload carries that reading`() {
        val (q, _) = question().pick("cat", nowMs = t0 + 4_200)
        assertEquals(4_200L, q.measuredElapsedMs)
        assertEquals(4_200, q.payload(SRSRating.Good, listeningOptedOut = false).responseMs)
    }

    @Test
    fun `an answer given before the audio ended reports no duration`() {
        // Genuine recognition mid-sentence and a rush are indistinguishable, so
        // the suggestion falls back to correctness instead of claiming a speed.
        val q = listening()
        val (after, _) = q.pickImage(q.imageOptions!!.first(), nowMs = t0 + 500)
        assertNull(after.measuredElapsedMs)
        assertNull(after.payload(SRSRating.Good, listeningOptedOut = false).responseMs)
        assertEquals(SRSRating.Good, after.suggested)
    }

    @Test
    fun `only the first playback starts the clock`() {
        // A replay must not reset it, or the button becomes a way to buy time.
        val started = listening().playbackEnded(finished = true, isReplay = false, nowMs = t0 + 5_000)
        assertFalse(started.awaitingAudio)
        assertEquals(t0 + 5_000, started.startedAtMs)

        val replayed = started.playbackEnded(finished = true, isReplay = true, nowMs = t0 + 9_000)
        assertEquals(t0 + 5_000, replayed.startedAtMs)
    }

    @Test
    fun `a fallback playback is recorded as audio failed`() {
        val q = listening().playbackEnded(finished = false, isReplay = false, nowMs = t0)
        assertTrue(q.audioFailed)
        assertEquals(true, q.payload(SRSRating.Good, listeningOptedOut = false).audioFailed)
    }

    // Settling

    @Test
    fun `a retest settles on resolve either way`() {
        // It never requeues and is never rated, so there is nothing to wait for.
        val right = question(isRetest = true).pick("cat", nowMs = t0 + 1_000)
        assertTrue(right.question.settled)
        assertEquals(ReviewResolution.FlashRetestPassed, (right.tap as ReviewTap.Resolved).resolution)

        // On 選字 there is no one-tap wrong answer: the option is ruled out and
        // the question stays open. "Wrong" is the correct pick landing after a
        // rule-out, which is what `wasCorrect` means here.
        val wrong = question(isRetest = true)
            .pick("dog", nowMs = t0 + 500).question
            .pick("cat", nowMs = t0 + 1_000)
        assertFalse(wrong.question.wasCorrect)
        assertTrue(wrong.question.settled)
        assertEquals(
            ReviewResolution.Reveal(ReviewRevealMode.ContinueOnly),
            (wrong.tap as ReviewTap.Resolved).resolution,
        )
    }

    @Test
    fun `a rated answer settles only when it was correct`() {
        // A wrong one goes back on the tail, so the bar must not count it.
        val wrong = question().pick("dog", nowMs = t0 + 1_000).question
            .applyRating(SRSRating.Again)!!
        assertFalse(wrong.settled)

        val slowRight = question().pick("cat", nowMs = t0 + 9_000).question
            .applyRating(SRSRating.Hard)!!
        assertTrue(slowRight.settled)
    }

    @Test
    fun `an auto-rated answer settles as soon as its rating lands`() {
        val (q, tap) = question().pick("cat", nowMs = t0 + 1_000)
        assertEquals(
            ReviewResolution.AutoRated(SRSRating.Good),
            (tap as ReviewTap.Resolved).resolution,
        )
        assertFalse("nothing is rated yet", q.settled)
        assertTrue(q.applyRating(SRSRating.Good)!!.settled)
    }

    @Test
    fun `a rating is recorded only once`() {
        val q = question().pick("cat", nowMs = t0 + 1_000).question.applyRating(SRSRating.Good)!!
        assertNull(q.applyRating(SRSRating.Easy))
        assertEquals(SRSRating.Good, q.rated)
    }

    @Test
    fun `counting is idempotent`() {
        val q = question().markCounted()
        assertTrue(q.markCounted().counted)
    }

    // Picking

    @Test
    fun `a ruled-out option leaves the question open and still counts as a miss`() {
        val first = question().pick("dog", nowMs = t0 + 500)
        assertEquals(ReviewTap.RuledOut, first.tap)
        assertEquals(ReviewPhase.Answer, first.question.phase)

        // Repeating a ruled-out option is ignored, not re-marked.
        assertEquals(ReviewTap.Ignored, first.question.pick("dog", nowMs = t0 + 700).tap)

        // Finding it afterwards is still graded as a miss: everything
        // downstream reads wasCorrect, and none of it counts taps.
        val landed = first.question.pick("cat", nowMs = t0 + 1_000)
        assertFalse(landed.question.wasCorrect)
        assertEquals(SRSRating.Again, landed.question.suggested)
    }

    @Test
    fun `the reported selection survives an unfinished question`() {
        val open = question().pick("dog", nowMs = t0).question.pick("bird", nowMs = t0).question
        assertEquals("bird / dog", open.reportedSelection)
        assertEquals("cat", open.pick("cat", nowMs = t0).question.reportedSelection)
    }

    @Test
    fun `a pick carries an id only when the option had one`() {
        val word = question().pick("cat", nowMs = t0 + 1_000).question
        assertNull("選字's labels have no id", word.picked?.id)

        val q = listening()
        val image = q.pickImage(q.imageOptions!!.first(), nowMs = t0).question
        assertEquals("w", image.picked?.id)
    }

    @Test
    fun `a wrong picture resolves immediately`() {
        // Ruling out one of two pictures is the same act as answering.
        val q = listening()
        val tapped = q.pickImage(q.imageOptions!!.last(), nowMs = t0 + 1_000)
        assertEquals(ReviewPhase.Review, tapped.question.phase)
        assertFalse(tapped.question.wasCorrect)
        assertTrue(tapped.question.wrongPicks.isEmpty())
    }

    @Test
    fun `a picture is judged by id, not by its label`() {
        // Two catalogue words can print the same string; they cannot share an id.
        val q = listening()
        val impostor = ImageChoiceOption(id = "other", word = "cat", imageUrl = "x")
        assertFalse(q.pickImage(impostor, nowMs = t0).question.wasCorrect)
    }

    // The hint

    @Test
    fun `the hint is sticky and takes the wrong-answer table`() {
        val flipped = question().toggleHint()
        assertTrue(flipped.hinted)
        assertTrue(flipped.hintFaceUp)

        val flippedBack = flipped.toggleHint()
        assertFalse("the face turns back", flippedBack.hintFaceUp)
        assertTrue("but it cannot be un-seen", flippedBack.hinted)

        // Correct, but the user said they could not retrieve it.
        val answered = flippedBack.pick("cat", nowMs = t0 + 1_000).question
        assertEquals(SRSRating.Hard, answered.suggested)
        assertEquals(listOf(SRSRating.Again, SRSRating.Hard), answered.availableRatings)
    }

    @Test
    fun `a hinted answer never auto-rates`() {
        // The 困難 cap is what switches the auto-rate path off (ADR-0007).
        val (_, tap) = question().toggleHint().pick("cat", nowMs = t0 + 1_000)
        assertEquals(
            ReviewResolution.Reveal(ReviewRevealMode.Rate),
            (tap as ReviewTap.Resolved).resolution,
        )
    }

    @Test
    fun `lifting the blur costs what the flip costs`() {
        val q = listening().revealSentence()
        assertTrue(q.sentenceRevealed)
        assertTrue("the sentence spells the answer out", q.hinted)
    }

    @Test
    fun `the blur is free once the answer is in`() {
        val q = listening()
        val answered = q.pickImage(q.imageOptions!!.first(), nowMs = t0).question
        assertFalse(answered.revealSentence().sentenceRevealed)
    }

    @Test
    fun `the nudge is only for an unanswered pick-word card`() {
        assertTrue(question().canNudge)
        assertFalse("nothing left to teach", question().toggleHint().canNudge)
        assertFalse("a retest already knows", question(isRetest = true).canNudge)
        assertFalse("聽句's eye is on screen from the first frame", listening().canNudge)
    }

    // Opting out of listening

    @Test
    fun `opting out converts the card and restarts the clock`() {
        val q = listening().optOutOfListening(nowMs = t0 + 8_000)!!
        assertEquals(ReviewQuestionKind.PickWord, q.kind)
        assertNull(q.example)
        assertEquals("a different question starts now", t0 + 8_000, q.startedAtMs)
        assertFalse("no answer was revealed", q.hinted)
        assertEquals(true, q.payload(SRSRating.Good, listeningOptedOut = true).convertedFromListening)
    }

    @Test
    fun `an ordinary session says nothing about listening`() {
        val p = question().pick("cat", nowMs = t0 + 1_000).question
            .payload(SRSRating.Good, listeningOptedOut = false)
        assertNull("not zero replays of audio never played", p.replayCount)
        assertNull(p.audioFailed)
        assertNull(p.listeningOptedOut)
        assertNull(p.convertedFromListening)
        assertEquals("mcq", p.activity)
    }

    @Test
    fun `the opt-out rides on every activity in the session`() {
        // A session with listening off answers the rest as 選字; without the
        // flag those rows are indistinguishable from a session that never met
        // a listening question, and that is what makes an aggregate lie.
        val p = question().pick("cat", nowMs = t0 + 1_000).question
            .payload(SRSRating.Good, listeningOptedOut = true)
        assertEquals("mcq", p.activity)
        assertEquals(true, p.listeningOptedOut)
    }

    @Test
    fun `opting out is refused once answered or on a pick-word card`() {
        assertNull("nothing to opt out of", question().optOutOfListening(nowMs = t0))
        val q = listening()
        val answered = q.pickImage(q.imageOptions!!.first(), nowMs = t0).question
        assertNull(answered.optOutOfListening(nowMs = t0))
    }

    @Test
    fun `presenting without a sentence leaves a pick-word card`() {
        // A sentence is what makes it a listening question. Keeping the
        // invariant here lets everything else read `kind` alone.
        val q = ReviewQuestion(item = item(), isRetest = false, startedAtMs = t0)
            .present(kind = ReviewQuestionKind.HearSentence, example = null)
        assertEquals(ReviewQuestionKind.PickWord, q.kind)
        assertTrue(q.ready)
    }

    @Test
    fun `a question is not ready before it has been presented`() {
        // 選字's hero is the answer's own picture, so drawing the default for
        // one frame would show the answer to a question that turns out to be 聽句.
        assertFalse(ReviewQuestion(item = item(), isRetest = false, startedAtMs = t0).ready)
    }

    // The suggestion table

    @Test
    fun `the suggestion caps easy for low mastery`() {
        // A 2-second hit on a barely-known word is normal recall, not 熟練.
        assertEquals(
            SRSRating.Good,
            ReviewQuestion.suggestion(correct = true, elapsedMs = 2_000, mastery = 10),
        )
        assertEquals(
            SRSRating.Easy,
            ReviewQuestion.suggestion(correct = true, elapsedMs = 2_000, mastery = 50),
        )
    }

    @Test
    fun `the suggestion table's other arms`() {
        assertEquals(SRSRating.Again, ReviewQuestion.suggestion(false, 1_000, 90))
        assertEquals(SRSRating.Hard, ReviewQuestion.suggestion(true, 1_000, 90, hinted = true))
        assertEquals(SRSRating.Good, ReviewQuestion.suggestion(true, null, 90))
        assertEquals(SRSRating.Good, ReviewQuestion.suggestion(true, 5_000, 90))
        assertEquals(SRSRating.Hard, ReviewQuestion.suggestion(true, 9_000, 90))
    }

    @Test
    fun `a fast correct answer auto-rates and a slow one asks`() {
        val fast = question(mastery = 90).pick("cat", nowMs = t0 + 1_000)
        assertEquals(
            ReviewResolution.AutoRated(SRSRating.Easy),
            (fast.tap as ReviewTap.Resolved).resolution,
        )
        val slow = question(mastery = 90).pick("cat", nowMs = t0 + 9_000)
        assertEquals(
            ReviewResolution.Reveal(ReviewRevealMode.Rate),
            (slow.tap as ReviewTap.Resolved).resolution,
        )
    }

    @Test
    fun `a correct listening answer still asks, because two pictures is a coin flip`() {
        val q = listening().playbackEnded(finished = true, isReplay = false, nowMs = t0)
        val tapped = q.pickImage(q.imageOptions!!.first(), nowMs = t0 + 500)
        assertTrue(tapped.question.wasCorrect)
        assertEquals(
            ReviewResolution.Reveal(ReviewRevealMode.Rate),
            (tapped.tap as ReviewTap.Resolved).resolution,
        )
    }
}
