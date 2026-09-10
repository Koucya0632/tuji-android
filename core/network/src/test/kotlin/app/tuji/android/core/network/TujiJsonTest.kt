package app.tuji.android.core.network

import app.tuji.android.core.model.WordDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the decoder has to tolerate, pinned.
 *
 * Both shapes below are real: `/api/words/{id}` omits the key, and
 * `/api/atlas/items/{id}/detail` sends an explicit null. A default value covers
 * only the first, which is how the first card a user ever photographed failed
 * to open on a field the screen was not even drawing.
 */
class TujiJsonTest {

    private fun decode(json: String): WordDetail = TujiJson.decodeFromString(json)

    @Test fun `a missing collection is the default`() {
        val word = decode("""{"id":"a","word":"手おけ"}""")
        assertTrue(word.examples.isEmpty())
    }

    @Test fun `an explicit null collection is also the default`() {
        val word = decode("""{"id":"a","word":"手おけ","examples":null,"relations":null}""")
        assertTrue(word.examples.isEmpty())
    }

    @Test fun `a real collection still decodes`() {
        val word = decode(
            """{"id":"a","word":"手おけ",
               "examples":[{"text":"手おけで肩にお湯をかけます。"}]}""",
        )
        assertEquals(1, word.examples.size)
    }

    /** Tolerance is for absent values, not for the whole payload. */
    @Test fun `an unknown key is still ignored, and a known one still read`() {
        val word = decode("""{"id":"a","word":"手おけ","somethingNew":42,"chinese":"浴室水勺"}""")
        assertEquals("浴室水勺", word.chinese)
    }
}
