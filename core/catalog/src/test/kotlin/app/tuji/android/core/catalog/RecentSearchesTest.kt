package app.tuji.android.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {

    @Test fun `the newest query goes first`() {
        assertEquals(listOf("傘", "箸"), RecentSearches.push(listOf("箸"), "傘"))
    }

    @Test fun `searching again moves a query to the top instead of repeating it`() {
        assertEquals(listOf("箸", "傘"), RecentSearches.push(listOf("傘", "箸"), "箸"))
    }

    @Test fun `it keeps ten`() {
        val full = (1..10).map { "q$it" }
        val next = RecentSearches.push(full, "new")
        assertEquals(RecentSearches.MAX, next.size)
        assertEquals("new", next.first())
        assertEquals("q9", next.last())
    }

    @Test fun `blank and padded queries are not stored as typed`() {
        assertEquals(listOf("箸"), RecentSearches.push(listOf("箸"), "   "))
        assertEquals(listOf("傘", "箸"), RecentSearches.push(listOf("箸"), "  傘 "))
    }
}
