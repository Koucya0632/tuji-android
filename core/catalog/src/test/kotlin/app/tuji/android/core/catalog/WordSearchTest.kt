package app.tuji.android.core.catalog

import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSearchTest {

    private fun w(
        id: String,
        word: String,
        chinese: String? = null,
        reading: String? = null,
        pronunciation: String? = null,
    ) = Word(
        id = id, word = word, chinese = chinese, reading = reading,
        pronunciation = pronunciation, category = "kitchen",
        targetLanguage = TargetLanguage.JA,
    )

    private val catalogue = listOf(
        w("chopsticks", "箸", "筷子", reading = "はし"),
        w("chopstick-rest", "箸置き", "筷架", reading = "はしおき"),
        w("bridge", "橋", "橋", reading = "はし"),
        w("kettle", "やかん", "水壺", reading = "やかん"),
        w("pan", "フライパン", "平底鍋", reading = "ふらいぱん"),
    )

    @Test fun `an exact term wins`() {
        assertEquals("箸", WordSearch.matches("箸", catalogue).first().word)
    }

    @Test fun `a prefix beats a mere containment`() {
        val hits = WordSearch.matches("箸", catalogue).map { it.id }
        assertEquals(listOf("chopsticks", "chopstick-rest"), hits)
    }

    @Test fun `the gloss is searched too`() {
        assertEquals("kettle", WordSearch.matches("水壺", catalogue).single().id)
    }

    @Test fun `the reading finds a word the writing does not`() {
        // Someone who knows how it sounds but not how it is written.
        val hits = WordSearch.matches("はしおき", catalogue).map { it.id }
        assertEquals(listOf("chopstick-rest"), hits)
    }

    @Test fun `a shorter term breaks the tie`() {
        // はし matches 箸(はし), 箸置き(はしおき) and 橋(はし) by reading; the
        // word that *is* the query is likelier than the one containing it.
        val hits = WordSearch.matches("はし", catalogue).map { it.id }
        assertEquals(listOf("chopsticks", "bridge", "chopstick-rest"), hits)
    }

    @Test fun `case does not matter`() {
        val latin = listOf(w("kettle", "Kettle", "水壺"))
        assertEquals(1, WordSearch.matches("kettle", latin).size)
        assertEquals(1, WordSearch.matches("KETTLE", latin).size)
    }

    @Test fun `an empty query asks for nothing, not everything`() {
        // A search field nobody has typed into has not requested 557 rows.
        assertTrue(WordSearch.matches("", catalogue).isEmpty())
        assertTrue(WordSearch.matches("   ", catalogue).isEmpty())
    }

    @Test fun `surrounding space is not part of the query`() {
        assertEquals(1, WordSearch.matches("  箸  ", catalogue).count { it.id == "chopsticks" })
    }

    @Test fun `nothing matching is an empty list, not everything`() {
        assertTrue(WordSearch.matches("zzzz", catalogue).isEmpty())
    }

    @Test fun `a word missing every optional field does not crash the ranking`() {
        val bare = listOf(Word(id = "x", word = "x"))
        assertEquals(1, WordSearch.matches("x", bare).size)
        assertTrue(WordSearch.matches("y", bare).isEmpty())
    }

    @Test fun `pronunciation is the last resort, after the reading`() {
        val pair = listOf(
            w("a", "アイス", "冰", reading = "あいす"),
            w("b", "ビール", "啤酒", pronunciation = "あいす"),
        )
        assertEquals(listOf("a", "b"), WordSearch.matches("あいす", pair).map { it.id })
    }
}
