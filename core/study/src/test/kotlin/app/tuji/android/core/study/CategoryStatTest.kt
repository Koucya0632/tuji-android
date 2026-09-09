package app.tuji.android.core.study

import app.tuji.android.core.model.Category
import app.tuji.android.core.model.CategoryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryStatTest {

    private val catalogue = listOf(
        Category(id = "kitchen", name = "Kitchen", nameZh = "廚房"),
        Category(id = "bathroom", name = "Bathroom", nameZh = "浴室"),
        Category(id = "bedroom", name = "Bedroom", nameZh = "臥室"),
    )

    private val rows = listOf(
        CategoryProgress("bedroom", total = 44, seen = 11),
        CategoryProgress("kitchen", total = 64, seen = 32),
        CategoryProgress("bathroom", total = 80, seen = 0),
    )

    /**
     * The endpoint answers in its own order. If the rows followed it, the list
     * would reshuffle itself between loads.
     */
    @Test fun `rows follow the catalogue's order, not the endpoint's`() {
        val out = CategoryStat.breakdown(rows, categoryOrder = catalogue)
        assertEquals(listOf("kitchen", "bathroom", "bedroom"), out.map { it.id })
    }

    @Test fun `an empty selection shows everything, not nothing`() {
        assertEquals(3, CategoryStat.breakdown(rows, emptyList(), catalogue).size)
    }

    @Test fun `a selection scopes the rows`() {
        val out = CategoryStat.breakdown(rows, listOf("kitchen"), catalogue)
        assertEquals(listOf("kitchen"), out.map { it.id })
    }

    /** A row reading 0 / 0 is not progress, it is noise. */
    @Test fun `a theme with no published cards is dropped`() {
        val out = CategoryStat.breakdown(
            rows + CategoryProgress("empty", total = 0, seen = 0),
            categoryOrder = catalogue + Category(id = "empty", name = "Empty"),
        )
        assertTrue(out.none { it.id == "empty" })
    }

    /** The cold open: the catalogue has not arrived, so ids stand in as names. */
    @Test fun `without the catalogue the ids stand in`() {
        val out = CategoryStat.breakdown(rows, categoryOrder = emptyList())
        assertEquals(setOf("bedroom", "kitchen", "bathroom"), out.map { it.name }.toSet())
    }

    /** A progress row for a theme the catalogue does not have is not invented. */
    @Test fun `a row with no catalogue entry is dropped`() {
        val out = CategoryStat.breakdown(
            rows + CategoryProgress("ghost", total = 5, seen = 1),
            categoryOrder = catalogue,
        )
        assertTrue(out.none { it.id == "ghost" })
    }

    @Test fun `ratio is seen over total, and zero total does not divide`() {
        val out = CategoryStat.breakdown(rows, categoryOrder = catalogue)
        assertEquals(0.5, out.first { it.id == "kitchen" }.ratio, 0.001)
        assertEquals(0.0, CategoryStat("x", "x", 0, 0).ratio, 0.001)
    }

    @Test fun `the four heatmap bands`() {
        assertEquals(HeatmapBand.None, HeatmapBand.of(0))
        assertEquals(HeatmapBand.Light, HeatmapBand.of(1))
        assertEquals(HeatmapBand.Light, HeatmapBand.of(4))
        assertEquals(HeatmapBand.Medium, HeatmapBand.of(5))
        assertEquals(HeatmapBand.Medium, HeatmapBand.of(12))
        assertEquals(HeatmapBand.Heavy, HeatmapBand.of(13))
    }
}
