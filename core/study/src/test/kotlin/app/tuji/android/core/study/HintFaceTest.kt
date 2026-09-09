package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class HintFaceTest {

    private fun word(gloss: String = "水桶", definition: String? = null) = StudyQueueWord(
        id = "w-bucket", word = "バケツ", chinese = gloss,
        imageUrl = "https://img.test/bucket.webp",
        pronunciation = "", category = "bathroom",
        targetLanguage = TargetLanguage.JA,
        definition = definition,
    )

    @Test fun `a word with a 釋義 turns over to it`() {
        val face = HintFace.of(word(definition = "附提把、開口朝上的圓柱形容器"))
        assertEquals(HintFace.Definition("附提把、開口朝上的圓柱形容器"), face)
    }

    @Test fun `a word without one falls back to the gloss`() {
        assertEquals(HintFace.Gloss("水桶"), HintFace.of(word()))
    }

    /**
     * The card would otherwise turn over to a blank face — the ordinary state
     * for a catalogue entry with no 釋義 written yet, not a decoding failure.
     */
    @Test fun `a blank 釋義 is no 釋義`() {
        assertEquals(HintFace.Gloss("水桶"), HintFace.of(word(definition = "   ")))
    }

    /**
     * Monolingual study: the gloss *is* the explanatory definition, written in
     * the language being tested. Showing it as a hint would hand over the
     * answer, which is the one thing this face may not do.
     */
    @Test fun `a 釋義 that only repeats the gloss is not a hint`() {
        assertEquals(HintFace.Gloss("水桶"), HintFace.of(word(definition = "水桶")))
    }

    @Test fun `whitespace does not make a repeat look different`() {
        assertEquals(HintFace.Gloss("水桶"), HintFace.of(word(definition = " 水桶 ")))
    }

    /** The face and what a screen reader is told are the same string. */
    @Test fun `both faces report their own text`() {
        assertEquals("水桶", HintFace.of(word()).text)
        assertEquals("圓柱形容器", HintFace.of(word(definition = "圓柱形容器")).text)
    }
}
