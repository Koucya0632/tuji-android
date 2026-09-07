package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two invariants the server guarantees about a split, checked against the
 * exact bytes `/api/words?lang=zh-Hant&learning=zh-ja` returned on 2026-09-08:
 *
 *   `text` re-spells the headword, and `ruby ?: text` re-spells the reading.
 *
 * They are what let a renderer draw either from the segments alone. iOS checks
 * the same pair; if one side ever stops holding, both apps are wrong together
 * rather than differently.
 */
class FuriganaSegmentTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val teoke = """
        {"id":"bath-ladle","word":"手おけ","chinese":"浴室水勺",
         "pronunciation":"ておけ","reading":"ておけ",
         "readingSegments":[{"ruby":"て","text":"手"},{"ruby":null,"text":"おけ"}],
         "targetLanguage":"ja"}
    """.trimIndent()

    private val furoisu = """
        {"id":"bath-stool","word":"風呂いす","chinese":"浴室凳",
         "pronunciation":"ふろいす","reading":"ふろいす",
         "readingSegments":[{"ruby":"ふろ","text":"風呂"},{"ruby":null,"text":"いす"}],
         "targetLanguage":"ja"}
    """.trimIndent()

    @Test
    fun `segments re-spell the headword`() {
        val w = json.decodeFromString<Word>(teoke)
        assertEquals(w.word, w.readingSegments!!.joinToString("") { it.text })
    }

    @Test
    fun `segments re-spell the reading`() {
        val w = json.decodeFromString<Word>(teoke)
        assertEquals(w.reading, w.readingSegments!!.joinToString("") { it.ruby ?: it.text })
    }

    @Test
    fun `a ruby may span more than one character`() {
        // 熟字訓 have no per-character reading: ふろ reads 風呂 as a unit, which
        // is why the split is a range and not a character (ADR-0006).
        val w = json.decodeFromString<Word>(furoisu)
        val first = w.readingSegments!!.first()
        assertEquals("風呂", first.text)
        assertEquals("ふろ", first.ruby)
        assertEquals(2, first.text.length)
    }

    @Test
    fun `bare kana runs carry no ruby`() {
        val w = json.decodeFromString<Word>(teoke)
        assertNull(w.readingSegments!!.last().ruby)
    }

    @Test
    fun `a missing split decodes as null rather than failing`() {
        // "readingSegments may be null, and that is normal" — ADR-0006.
        val w = json.decodeFromString<Word>("""{"id":"x","word":"cat"}""")
        assertNull(w.readingSegments)
        assertNull(w.targetLanguage)
    }
}
