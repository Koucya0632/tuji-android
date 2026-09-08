package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningQuestionTest {

    private fun item(id: String, examples: List<StudyExample>?) = StudyQueueItem(
        card = StudyCard(id = "c-$id"),
        word = StudyQueueWord(
            id = id, word = id, chinese = id,
            imageUrl = "https://img.test/$id.webp",
            pronunciation = "", category = "bedroom",
        ),
        examples = examples,
    )

    private fun example(level: String, text: String = level) =
        StudyExample(sentence = text, cefrLevel = level, audioUrls = mapOf("ja-JP" to "u"))

    private val pair = listOf(example("A2", "simple"), example("B1", "complex"))

    // ---- which sentence ----

    @Test fun `a new word gets the simpler sentence`() {
        val e = ListeningQuestion.example(item("w", pair), mastery = 0, presentation = 0)
        assertEquals("simple", e?.sentence)
    }

    @Test fun `an established word gets the harder one`() {
        val e = ListeningQuestion.example(item("w", pair), mastery = 50, presentation = 0)
        assertEquals("complex", e?.sentence)
    }

    @Test fun `the tier is the same 50 the rating suggestion uses`() {
        assertEquals(50, ListeningQuestion.MASTERY_TIER)
        val below = ListeningQuestion.example(item("w", pair), mastery = 49, presentation = 0)
        assertEquals("simple", below?.sentence)
    }

    @Test fun `a retest takes the other sentence, not a replay of the failed one`() {
        val first = ListeningQuestion.example(item("w", pair), mastery = 0, presentation = 0)
        val retest = ListeningQuestion.example(item("w", pair), mastery = 0, presentation = 1)
        assertEquals("simple", first?.sentence)
        assertEquals("complex", retest?.sentence)
    }

    @Test fun `a third look clamps rather than alternating back`() {
        val third = ListeningQuestion.example(item("w", pair), mastery = 0, presentation = 2)
        val tenth = ListeningQuestion.example(item("w", pair), mastery = 0, presentation = 9)
        assertEquals("a two-sentence word has nothing new on look three", "complex", third?.sentence)
        assertEquals("complex", tenth?.sentence)
    }

    @Test fun `a negative presentation is not an index crash`() {
        assertEquals("simple", ListeningQuestion.example(item("w", pair), 0, -1)?.sentence)
    }

    @Test fun `no sentences means no listening question`() {
        assertNull(ListeningQuestion.example(item("w", null), mastery = 0, presentation = 0))
        assertNull(ListeningQuestion.example(item("w", emptyList()), mastery = 0, presentation = 0))
    }

    @Test fun `an unlabelled sentence is still offered rather than dropped`() {
        val only = listOf(example(level = "", text = "unlabelled"))
        assertEquals("unlabelled", ListeningQuestion.example(item("w", only), 0, 0)?.sentence)
    }

    // ---- which cards ----

    @Test fun `roughly one card in four falls on a slot`() {
        val ids = (0 until 4000).map { "word-$it" }
        val hits = ids.count { ListeningQuestion.fallsOnSlot(it) }
        // The hash is fixed, so this is a pinned fact about the real
        // distribution, not a probabilistic hope. A band, because the point is
        // "not clustered and not everything", not an exact count.
        assertTrue("$hits of 4000 fell on a slot", hits in 850..1150)
    }

    @Test fun `the slot does not move between runs`() {
        val once = (0 until 50).map { ListeningQuestion.fallsOnSlot("word-$it") }
        val twice = (0 until 50).map { ListeningQuestion.fallsOnSlot("word-$it") }
        assertEquals(once, twice)
    }

    @Test fun `no clip means no listening question, whatever the slot says`() {
        val slotted = (0 until 200).map { "w$it" }.first { ListeningQuestion.fallsOnSlot(it) }
        assertEquals(
            ReviewQuestionKind.PickWord,
            ListeningQuestion.kind(slotted, canHear = false, previous = null, alreadyHeard = false),
        )
    }

    @Test fun `two listening questions in a row demotes the second`() {
        val slotted = (0 until 200).map { "w$it" }.first { ListeningQuestion.fallsOnSlot(it) }
        assertEquals(
            ReviewQuestionKind.HearSentence,
            ListeningQuestion.kind(slotted, true, previous = null, alreadyHeard = false),
        )
        assertEquals(
            ReviewQuestionKind.PickWord,
            ListeningQuestion.kind(slotted, true, ReviewQuestionKind.HearSentence, false),
        )
    }

    @Test fun `a retest keeps its question even right after another one`() {
        val notSlotted = (0 until 200).map { "w$it" }.first { !ListeningQuestion.fallsOnSlot(it) }
        assertEquals(
            "a retest is practice on what was missed and writes no SRS",
            ReviewQuestionKind.HearSentence,
            ListeningQuestion.kind(
                notSlotted, canHear = true,
                previous = ReviewQuestionKind.HearSentence, alreadyHeard = true,
            ),
        )
    }

    @Test fun `a retest with nothing to play still demotes`() {
        assertEquals(
            ReviewQuestionKind.PickWord,
            ListeningQuestion.kind("w", canHear = false, previous = null, alreadyHeard = true),
        )
    }

    @Test fun `an off-slot card stays 選字`() {
        val notSlotted = (0 until 200).map { "w$it" }.first { !ListeningQuestion.fallsOnSlot(it) }
        assertEquals(
            ReviewQuestionKind.PickWord,
            ListeningQuestion.kind(notSlotted, true, previous = null, alreadyHeard = false),
        )
    }
}
