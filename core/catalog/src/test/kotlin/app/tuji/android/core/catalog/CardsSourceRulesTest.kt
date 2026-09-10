package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsSourceRulesTest {

    private fun word(id: String) = Word(id = id, word = id)

    private val official = listOf(word("a"), word("b"), word("c"))
    private val taken = listOf(word("saved:one"), word("saved:two"))

    @Test fun `the dictionary is unfiltered`() {
        assertEquals(
            official,
            CardsSourceRules.words(CardsSource.Official, official, taken, setOf("a")),
        )
    }

    @Test fun `taken is what the server sent, in its order`() {
        assertEquals(
            taken,
            CardsSourceRules.words(CardsSource.Taken, official, taken, emptySet()),
        )
    }

    /** The grid is the same grid, with rows taken out. */
    @Test fun `bookmarks keep catalogue order, not marking order`() {
        val out = CardsSourceRules.words(
            CardsSource.Bookmarked, official, taken, setOf("c", "a"),
        )
        assertEquals(listOf("a", "c"), out.map { it.id })
    }

    /**
     * The rule worth pinning: 書籤 spans both decks, so an id from 中文→英文
     * arrives while the 中文→日文 catalogue is loaded. Leaving it out is the
     * only honest answer — a blank tile says the entry is broken.
     */
    @Test fun `a bookmark this deck has no word for is left out`() {
        val out = CardsSourceRules.words(
            CardsSource.Bookmarked, official, taken, setOf("a", "from-the-other-deck"),
        )
        assertEquals(listOf("a"), out.map { it.id })
    }

    @Test fun `no bookmarks is an empty shelf, not the whole dictionary`() {
        assertEquals(
            emptyList<Word>(),
            CardsSourceRules.words(CardsSource.Bookmarked, official, taken, emptySet()),
        )
    }

    @Test fun `a saved id is recognised and unwrapped`() {
        assertTrue(CardsSourceRules.isSaved("saved:kettle"))
        assertEquals("kettle", CardsSourceRules.savedSlug("saved:kettle"))
    }

    @Test fun `a dictionary id is not a saved one`() {
        assertFalse(CardsSourceRules.isSaved("kettle"))
        assertNull(CardsSourceRules.savedSlug("kettle"))
    }

    /** `atlas:` is 自製圖鑑, which routes somewhere else again. */
    @Test fun `a custom id is not a saved one`() {
        assertFalse(CardsSourceRules.isSaved("atlas:abc123"))
        assertNull(CardsSourceRules.savedSlug("atlas:abc123"))
    }

    @Test fun `a bare prefix has no slug`() {
        assertNull(CardsSourceRules.savedSlug("saved:"))
    }

    /** 圖鑑 is the dictionary's tab; it opens on the dictionary. */
    @Test fun `the first source is the dictionary`() {
        assertEquals(CardsSource.Official, CardsSource.entries.first())
    }
}
