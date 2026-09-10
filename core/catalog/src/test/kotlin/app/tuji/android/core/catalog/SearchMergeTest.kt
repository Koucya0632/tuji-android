package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchMergeTest {

    private fun word(id: String, term: String = id) = Word(id = id, word = term)

    @Test fun `local order is kept exactly`() {
        val local = listOf(word("a"), word("b"), word("c"))
        assertEquals(
            listOf("a", "b", "c"),
            SearchMerge.merge(local, emptyList()).map { it.id },
        )
    }

    @Test fun `remote-only matches come after every local one`() {
        val out = SearchMerge.merge(listOf(word("a")), listOf(word("z"), word("y")))
        assertEquals(listOf("a", "z", "y"), out.map { it.id })
    }

    /**
     * The overlap is the normal case, not the edge one: anything the local list
     * matched by headword the server matched too.
     */
    @Test fun `a word in both appears once, in its local position`() {
        val local = listOf(word("a"), word("b"))
        val remote = listOf(word("b"), word("c"))
        assertEquals(listOf("a", "b", "c"), SearchMerge.merge(local, remote).map { it.id })
    }

    /** Local wins the tie, so the row on screen is not replaced under the user. */
    @Test fun `the local copy is the one kept`() {
        val mine = word("a", term = "local")
        val theirs = word("a", term = "remote")
        assertEquals("local", SearchMerge.merge(listOf(mine), listOf(theirs)).single().word)
    }

    @Test fun `a repeated id inside one side is still deduped`() {
        val out = SearchMerge.merge(listOf(word("a"), word("a")), listOf(word("a")))
        assertEquals(1, out.size)
    }

    /** Nothing from the server means nothing to do — not a copied list. */
    @Test fun `an empty remote returns the local list itself`() {
        val local = listOf(word("a"))
        assertSame(local, SearchMerge.merge(local, emptyList()))
    }

    @Test fun `two empty sides are empty`() {
        assertEquals(emptyList<Word>(), SearchMerge.merge(emptyList(), emptyList()))
    }
}
