package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 報錯 rules both sessions share. The payload is pinned key by key against
 * iOS's `StudyReportSheet.submit`, because the server's triage reads one shape
 * from both apps.
 */
class StudyReportTest {

    private fun item(cardId: String = "42", term: String = "cat") = StudyQueueItem(
        card = StudyCard(id = cardId, cardType = "image", deckKey = "en"),
        word = StudyQueueWord(
            id = "w1", word = term, chinese = "貓",
            imageUrl = "https://img.test/w1.webp", pronunciation = "/kæt/",
            category = "animals",
        ),
        choices = listOf("cat", "dog", "cow", "pig"),
    )

    @Test
    fun `a custom card cannot be reported`() {
        assertFalse(StudyReports.accepts(item(cardId = "atlas:abc")))
        assertTrue(StudyReports.accepts(item(cardId = "42")))
        // A bare prefix names no item; iOS treats it as an ordinary id.
        assertTrue(StudyReports.accepts(item(cardId = "atlas:")))
    }

    @Test
    fun `both a kind and a description are required`() {
        assertFalse(StudyReports.canSubmit(null, "wrong picture", submitting = false))
        assertFalse(StudyReports.canSubmit(StudyReportIssue.Image, "   \n", submitting = false))
        assertFalse(StudyReports.canSubmit(StudyReportIssue.Image, "wrong picture", submitting = true))
        assertTrue(StudyReports.canSubmit(StudyReportIssue.Image, "wrong picture", submitting = false))
    }

    @Test
    fun `the payload carries the card as it was seen`() {
        val subject = StudyReportSubject(item(), phase = "answer", selectedAnswer = "dog")
        val p = StudyReports.payload(
            requestId = "r1",
            subject = subject,
            mode = StudyMode.Review,
            issue = StudyReportIssue.Content,
            detail = "  gloss is wrong \n",
            appVersion = "0.1.2 (3)",
            uiLang = "zh-TW",
        )
        assertEquals("r1", p.requestId)
        assertEquals("w1", p.wordId)
        assertEquals("42", p.cardId)
        assertEquals("content", p.issueType)
        assertEquals("gloss is wrong", p.description)
        assertEquals("review", p.mode)
        assertEquals("answer", p.phase)
        assertEquals("dog", p.selectedAnswer)
        assertEquals("android", p.platform)
        assertEquals("zh-TW", p.uiLang)
        assertEquals("cat", p.snapshot.word)
        assertEquals("image", p.snapshot.cardType)
        assertEquals(listOf("cat", "dog", "cow", "pig"), p.snapshot.choices)
        assertEquals(emptyList<String>(), p.snapshot.spellingChoices)
    }

    @Test
    fun `a review subject says whether the card was answered and what was ruled out`() {
        val open = ReviewQuestion(item = item(), isRetest = false, startedAtMs = 0)
            .present(ReviewQuestionKind.PickWord)
        val subject = StudyReports.review(open)!!
        assertEquals("answer", subject.phase)
        assertNull(subject.selectedAnswer)

        val (answered, _) = open.pick("dog", nowMs = 1_000)
        assertEquals("dog", StudyReports.review(answered)!!.selectedAnswer)
        assertNull(StudyReports.review(null))
    }

    @Test
    fun `a gap-fill attempt reads as the word with holes`() {
        val plan = SpellGaps(
            term = "kitchen",
            segments = listOf("k", "ch", "n"),
            gaps = listOf(SpellGaps.Gap("it", 1, 3), SpellGaps.Gap("e", 5, 6)),
            options = listOf("it", "e", "et"),
        )
        val form = SpellForm.Gaps(plan)
        assertNull(StudyReports.spellAttempt(form, emptyList()))
        assertEquals("kitch_n", StudyReports.spellAttempt(form, listOf("it")))
        assertEquals("kitchen", StudyReports.spellAttempt(form, listOf("it", "e")))
    }
}
