package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasCandidate
import app.tuji.android.core.model.RecognitionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDraftTest {

    private fun candidate(
        id: String,
        label: String = id,
        zh: String? = null,
        gloss: String? = null,
        level: String = AtlasCandidate.LEVEL_PRIMARY,
        rank: Int = 0,
        confidence: Double = 0.9,
    ) = AtlasCandidate(
        id = id, label = label, zhHant = zh, gloss = gloss,
        level = level, rank = rank, confidence = confidence,
    )

    // The money rules

    @Test fun `a mode that has answered is never asked twice`() {
        // The key is shared with 補資料 and the web app; a user tapping between
        // the two modes would otherwise spend one AI call per tap.
        val draft = CaptureDraft().withCandidates(RecognitionMode.Primary, listOf(candidate("a")))
        assertFalse(draft.needsFetch(RecognitionMode.Primary))
        assertTrue(draft.needsFetch(RecognitionMode.Escalate))
    }

    @Test fun `finding nothing is still an answer`() {
        // Recognition soft-fails: an empty list is a result, not a gap to retry.
        val draft = CaptureDraft().withCandidates(RecognitionMode.Primary, emptyList())
        assertFalse(draft.needsFetch(RecognitionMode.Primary))
    }

    @Test fun `switching back to a fetched mode costs nothing`() {
        val draft = CaptureDraft()
            .withCandidates(RecognitionMode.Primary, listOf(candidate("a")))
            .withCandidates(RecognitionMode.Escalate, listOf(candidate("b")))
            .showing(RecognitionMode.Primary)
        assertEquals(listOf("a"), draft.candidates.map { it.id })
        assertFalse(draft.needsFetch(RecognitionMode.Primary))
    }

    // Picking

    @Test fun `the first candidate is the highest ranked, not the most confident`() {
        // The server already ordered them; re-sorting on a number it also sent
        // would be a second opinion about the same question.
        val list = listOf(
            candidate("b", rank = 1, confidence = 0.99),
            candidate("a", rank = 0, confidence = 0.40),
        )
        assertEquals("a", CaptureDraft.preselect(list)?.id)
    }

    @Test fun `no candidates means nothing preselected`() {
        assertNull(CaptureDraft.preselect(emptyList()))
    }

    @Test fun `applying a candidate fills the two editable names`() {
        val draft = CaptureDraft().apply(candidate("a", label = "マグカップ", zh = "馬克杯", gloss = "mug"))
        assertEquals("マグカップ", draft.lemma)
        assertEquals("馬克杯", draft.displayZhHant)
        assertEquals("mug", draft.displayGloss)
        assertEquals("a", draft.selectedCandidateId)
    }

    @Test fun `picking a different candidate overwrites the names`() {
        // Choosing again is the user saying the previous answer was wrong.
        val draft = CaptureDraft()
            .apply(candidate("a", label = "杯", zh = "杯子"))
            .apply(candidate("b", label = "マグカップ", zh = "馬克杯"))
        assertEquals("マグカップ", draft.lemma)
        assertEquals("馬克杯", draft.displayZhHant)
    }

    @Test fun `a fine candidate becomes the fine label and keeps the primary`() {
        val draft = CaptureDraft()
            .apply(candidate("a", label = "カップ"))
            .apply(candidate("b", label = "マグカップ", level = AtlasCandidate.LEVEL_FINE))
        assertEquals("カップ", draft.primaryLabel)
        assertEquals("マグカップ", draft.fineLabel)
    }

    // The payload

    @Test fun `a hand-typed word still has a primary label`() {
        // Nothing was recognised, so primaryLabel is empty — but the server
        // requires it, and the only honest value is the name itself.
        val payload = CaptureDraft(lemma = "やかん", displayZhHant = "水壺").payload()
        assertEquals("やかん", payload.primaryLabel)
        assertEquals("やかん", payload.lemma)
        assertNull(payload.selectedCandidateId)
    }

    @Test fun `blank optionals travel as null, not as empty strings`() {
        // `fine_label: ""` reads as "there is a fine label and it is nothing".
        val payload = CaptureDraft(lemma = "やかん", displayZhHant = "水壺").payload()
        assertNull(payload.fineLabel)
        assertNull(payload.displayGloss)
        assertNull(payload.partOfSpeech)
        assertNull(payload.category)
    }

    @Test fun `what the user typed wins over what was recognised`() {
        val draft = CaptureDraft()
            .apply(candidate("a", label = "カップ", zh = "杯子"))
            .copy(lemma = "マグカップ", displayZhHant = "馬克杯")
        val payload = draft.payload()
        assertEquals("マグカップ", payload.lemma)
        assertEquals("馬克杯", payload.displayZhHant)
        assertEquals("the candidate is still credited", "a", payload.selectedCandidateId)
    }

    @Test fun `a name is the one thing nothing else can supply`() {
        assertFalse(CaptureDraft().isComplete)
        assertFalse(CaptureDraft(lemma = "   ").isComplete)
        assertTrue(CaptureDraft(lemma = "やかん").isComplete)
    }
}
