package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsListPagingTest {

    private fun words(n: Int) = (1..n).map { Word(id = "w$it", word = "word$it") }

    @Test fun `the window is the visible count`() {
        val page = CardsListPaging.page(words(100), visibleCount = 60)
        assertEquals(60, page.words.size)
        assertEquals(100, page.matchCount)
    }

    /** The button has to go away on the page that shows the last word. */
    @Test fun `顯示更多 disappears once everything is on screen`() {
        assertFalse(CardsListPaging.page(words(60), visibleCount = 60).canShowMore)
        assertTrue(CardsListPaging.page(words(61), visibleCount = 60).canShowMore)
    }

    @Test fun `asking for more than there is shows what there is`() {
        val page = CardsListPaging.page(words(7), visibleCount = 60)
        assertEquals(7, page.words.size)
        assertFalse(page.canShowMore)
    }

    /**
     * The count row reads `matchCount`, not the window — it says how many words
     * the shelf holds, not how many are drawn right now.
     */
    @Test fun `the count is the whole list, not the page`() {
        assertEquals(100, CardsListPaging.page(words(100), visibleCount = 20).matchCount)
    }

    @Test fun `an empty catalogue is a page with nothing to show more of`() {
        val page = CardsListPaging.page(emptyList(), visibleCount = 60)
        assertEquals(0, page.matchCount)
        assertFalse(page.canShowMore)
    }
}
