package app.tuji.android.core.catalog

import app.tuji.android.core.model.Category
import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryShelfTest {

    private fun cat(id: String, name: String, zh: String? = null) =
        Category(id = id, name = name, nameZh = zh)

    private fun word(id: String, category: String) =
        Word(id = id, word = id, category = category)

    private val categories = listOf(
        cat("custom", "Custom", "自定義"),
        cat("community", "Sightings", "物見"),
        cat("kitchen", "Kitchen", "廚房"),
        cat("bathroom", "Bathroom", "浴室"),
    )

    private val words = listOf(
        word("a", "kitchen"), word("b", "kitchen"), word("c", "bathroom"),
    )

    @Test fun `a shelf carries the count of what is actually on it`() {
        val shelves = CategoryShelf.shelves(categories, words)
        assertEquals(listOf("kitchen" to 2, "bathroom" to 1), shelves.map { it.category.id to it.count })
    }

    @Test fun `an empty shelf is not a door onto nothing`() {
        // 自定義 and 物見 come back from the server with zero words until the
        // milestones that fill them land.
        val ids = CategoryShelf.shelves(categories, words).map { it.category.id }
        assertTrue("custom" !in ids)
        assertTrue("community" !in ids)
    }

    @Test fun `a shelf reappears on its own once it has contents`() {
        // The rule is emptiness, not a hard-coded list of ids — so nothing has
        // to be remembered on the day 自製圖鑑 ships.
        val withCustom = words + word("mine", "custom")
        val ids = CategoryShelf.shelves(categories, withCustom).map { it.category.id }
        assertTrue("custom" in ids)
    }

    @Test fun `the server's order is kept`() {
        val shelves = CategoryShelf.shelves(categories, words)
        assertEquals(listOf("kitchen", "bathroom"), shelves.map { it.category.id })
    }

    @Test fun `opening a shelf gives exactly what its count promised`() {
        val shelf = CategoryShelf.shelves(categories, words).first { it.category.id == "kitchen" }
        assertEquals(shelf.count, CategoryShelf.words("kitchen", words).size)
    }

    @Test fun `a zh UI gets the zh name and an en UI the english one`() {
        val kitchen = cat("kitchen", "Kitchen", "廚房")
        assertEquals("廚房", CategoryShelf.title(kitchen, "zh-Hant"))
        assertEquals("Kitchen", CategoryShelf.title(kitchen, "en"))
    }

    @Test fun `a missing translation falls back rather than printing an id`() {
        assertEquals("Kitchen", CategoryShelf.title(cat("kitchen", "Kitchen"), "zh-Hant"))
        assertEquals("廚房", CategoryShelf.title(cat("kitchen", "", "廚房"), "en"))
    }

    @Test fun `a word whose category nothing names is simply not shelved`() {
        val orphan = words + word("ghost", "atlantis")
        val shelves = CategoryShelf.shelves(categories, orphan)
        assertEquals(3, shelves.sumOf { it.count })
    }
}
