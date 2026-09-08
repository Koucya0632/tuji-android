package app.tuji.android.core.study

import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TileBoardTest {

    private fun item(term: String, reading: String? = null) = StudyQueueItem(
        card = StudyCard(id = "c"),
        word = StudyQueueWord(
            id = "w", word = term, chinese = "—",
            imageUrl = "https://img.test/w.webp", pronunciation = "—",
            reading = reading, category = "misc",
        ),
    )

    @Test
    fun `a kana reading different from the term is what gets spelled`() {
        val subject = TileBoard.spellSubject(item("手おけ", reading = "ておけ"))
        assertEquals(SpellSubject.Reading("ておけ"), subject)
        assertTrue(subject.isReading)
    }

    @Test
    fun `a reading identical to the term spells the term`() {
        // バスマット is Japanese and its 振假名 is itself, so the stage quizzes
        // the 詞形. This is the case a "is it Japanese?" predicate gets wrong.
        val subject = TileBoard.spellSubject(item("バスマット", reading = "バスマット"))
        assertEquals(SpellSubject.Term("バスマット"), subject)
        assertTrue(!subject.isReading)
    }

    @Test
    fun `an english word has no reading and spells the term`() {
        assertEquals(SpellSubject.Term("cat"), TileBoard.spellSubject(item("cat")))
    }

    @Test
    fun `whitespace splits rows and never becomes a tile`() {
        val board = TileBoard.of(item("cutting board"))
        assertEquals(2, board.tokenUnits.size)
        assertEquals("cuttingboard", board.target)
        assertTrue(board.orderedUnits.none { it.isBlank() })
    }

    @Test
    fun `a small kana glues onto the mora before it`() {
        // きょ is one tile; splitting it would be quizzing a spelling that
        // does not exist.
        val board = TileBoard.of(item("きょうしつ", reading = "きょうしつ"))
        assertEquals(listOf("きょ", "う", "し", "つ"), board.orderedUnits)
    }

    @Test
    fun `sokuon stays its own tile because it is a full mora`() {
        val board = TileBoard.of(item("きって", reading = "きって"))
        assertEquals(listOf("き", "っ", "て"), board.orderedUnits)
    }

    @Test
    fun `a long subject re-chunks so the pool stays a recall task`() {
        val long = "abcdefghijklmnopqrstuvwx"   // 24 characters
        val board = TileBoard.of(item(long))
        assertTrue("got ${board.unitCount} tiles", board.unitCount <= TileBoard.MAX_TILE_COUNT)
        assertEquals(long, board.target)
    }

    @Test
    fun `chunking never merges across a space`() {
        val board = TileBoard.of(item("abcdefghijkl mnopqrstuvwx"))
        assertEquals(2, board.tokenUnits.size)
        assertTrue(board.tokenUnits.none { row -> row.any { ' ' in it } })
        assertEquals("abcdefghijklmnopqrstuvwx", board.target)
    }

    @Test
    fun `the board is the same on every retry`() {
        val subject = item("あかさたなはまやらわをん", reading = "あかさたなはまやらわをん")
        assertEquals(TileBoard.of(subject), TileBoard.of(subject))
    }
}

class TileBoardScrambleTest {

    private fun item(id: String, word: String, reading: String? = null) = StudyQueueItem(
        card = StudyCard(id = "c-$id"),
        word = StudyQueueWord(
            id = id, word = word, chinese = id,
            imageUrl = "https://img.test/$id.webp",
            pronunciation = "", category = "kitchen", reading = reading,
        ),
    )

    @Test fun `the pool holds exactly the board's units`() {
        val it = item("cutting-board", "cutting board")
        val board = TileBoard.of(it)
        assertEquals(
            board.orderedUnits.sorted(),
            TileBoard.scrambled(it, attempt = 0).sorted(),
        )
    }

    @Test fun `it does not reshuffle between two identical asks`() {
        val it = item("kettle", "kettle")
        assertEquals(TileBoard.scrambled(it, 0), TileBoard.scrambled(it, 0))
    }

    @Test fun `a retry gets a new scramble`() {
        val it = item("refrigerator", "refrigerator")
        val draws = (0 until 5).map { a -> TileBoard.scrambled(it, a) }
        assertTrue("an attempt that repeats the layout is a free re-read", draws.toSet().size > 1)
    }

    @Test fun `the tiles are never already the answer`() {
        // A two-tile board lands in order half the time by chance, so this is
        // the case the swap exists for rather than a theoretical one.
        (0 until 60).forEach { a ->
            val it = item("w$a", "はし", reading = "はし")
            val pool = TileBoard.scrambled(it, a)
            assertNotEquals(
                "attempt $a handed over the answer",
                TileBoard.of(it).target, pool.joinToString(""),
            )
        }
    }
}
