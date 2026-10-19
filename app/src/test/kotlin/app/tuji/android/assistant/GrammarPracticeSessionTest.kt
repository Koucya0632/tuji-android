package app.tuji.android.assistant

import app.tuji.android.core.model.GrammarPractice
import app.tuji.android.core.model.GrammarPracticeProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarPracticeSessionTest {

    private class MemoryProgress : GrammarProgressStoring {
        val saved = mutableMapOf<String, GrammarPracticeProgress>()
        override fun progress(owner: String, learning: String, setId: String) = saved[setId] ?: GrammarPracticeProgress()
        override fun save(progress: GrammarPracticeProgress, owner: String, learning: String, setId: String) { saved[setId] = progress }
        override fun remove(owner: String, learning: String, setId: String) { saved.remove(setId) }
    }

    private fun question(id: String, answer: String) = GrammarPractice.Question(
        id = id,
        kind = GrammarPractice.Question.Kind.Choice,
        prompt = "Q$id",
        options = listOf(GrammarPractice.Question.Option("a", "A"), GrammarPractice.Question.Option("b", "B")),
        answer = answer,
    )

    private val practice = GrammarPractice(focus = "〜ている", questions = listOf(question("1", "a"), question("2", "b")))

    private fun answer(session: GrammarPracticeSession, option: String) {
        session.select(option)
        session.confirm()
        session.next()
    }

    @Test fun `the first run is the score, and only the wrong ones come back`() {
        val store = MemoryProgress()
        val session = GrammarPracticeSession(practice, "set", "u1", "zh-ja", store)
        session.start()
        answer(session, "a")
        answer(session, "a")
        assertEquals(GrammarPracticeSession.Screen.Summary, session.state.value.screen)
        assertEquals(GrammarPracticeSession.Score(correct = 1, answered = 2, total = 2), session.firstScore)

        session.redoWrong()
        assertEquals("2", session.state.value.questionId)
        answer(session, "b")
        // Redoing never moves the score.
        assertEquals(1, session.firstScore?.correct)
        assertEquals(2, store.saved["set"]?.attempts?.size)
    }

    @Test fun `a confirmed answer is fixed`() {
        val session = GrammarPracticeSession(practice, "set", "u1", "zh-ja", MemoryProgress())
        session.start()
        session.select("b")
        session.confirm()
        session.select("a")
        assertEquals("b", session.state.value.confirmed)
    }

    @Test fun `reading the answers first turns a run into practice`() {
        val session = GrammarPracticeSession(practice, "set", "u1", "zh-ja", MemoryProgress())
        session.start()
        answer(session, "a")
        session.review()
        assertTrue(session.state.value.progress.revealed)
        assertFalse(session.state.value.progress.attempts.single().isFirst)
        assertNull(session.firstScore)
    }

    @Test fun `a finished set reopens on its results`() {
        val store = MemoryProgress()
        GrammarPracticeSession(practice, "set", "u1", "zh-ja", store).apply {
            start()
            answer(this, "a")
            answer(this, "b")
        }
        val reopened = GrammarPracticeSession(practice, "set", "u1", "zh-ja", store)
        assertEquals(GrammarPracticeSession.Screen.Summary, reopened.state.value.screen)
        assertEquals(2, reopened.firstScore?.correct)
    }
}
