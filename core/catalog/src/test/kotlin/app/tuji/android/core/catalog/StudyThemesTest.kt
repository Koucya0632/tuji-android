package app.tuji.android.core.catalog

import app.tuji.android.core.model.Category
import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyThemesTest {

    private fun word(id: String, category: String, text: String = id) = Word(id = id, word = text, category = category)

    private val categories = listOf(
        Category(id = "custom", name = "Custom", nameZh = "自定義"),
        Category(id = "community", name = "Sightings", nameZh = "物見"),
        Category(id = "kitchen", name = "Kitchen", nameZh = "廚房"),
        Category(id = "bathroom", name = "Bathroom", nameZh = "浴室"),
        Category(id = "bedroom", name = "Bedroom", nameZh = "臥室"),
        Category(id = "office", name = "Office", nameZh = "辦公室"),
        Category(id = "garden", name = "Garden", nameZh = "花園"),
    )

    private val catalogue = listOf(
        word("k1", "kitchen"), word("k2", "kitchen"), word("b1", "bathroom"),
        word("r1", "bedroom"), word("o1", "office"), word("g1", "garden"),
    )

    /**
     * 自定義 and 物見 hold nothing in the catalogue, so a strip built from the
     * catalogue alone never shows them — even to someone who ticked both and
     * has cards in each.
     */
    @Test fun `a user's own and taken-in cards put 自定義 and 物見 on the strip`() {
        val words = StudyThemes.words(
            catalogue = catalogue,
            mine = listOf(word("m1", "custom"), word("m2", "custom")),
            taken = listOf(word("t1", "community")),
        )
        val shelves = StudyThemes.todayShelves(
            CategoryShelf.shelves(categories, words),
            selected = listOf("custom", "community", "kitchen"),
            isGuest = false,
        )
        assertEquals(listOf("custom" to 2, "community" to 1, "kitchen" to 2), shelves.map { it.category.id to it.count })
    }

    @Test fun `a signed-in user sees exactly their picks, in the server's order`() {
        val shelves = StudyThemes.todayShelves(
            CategoryShelf.shelves(categories, catalogue),
            selected = listOf("office", "kitchen"),
            isGuest = false,
        )
        assertEquals(listOf("kitchen", "office"), shelves.map { it.category.id })
    }

    /** Nothing picked is nothing shown: 今日 draws its 選擇主題 prompt instead. */
    @Test fun `no picks shows no shelves`() {
        val shelves = StudyThemes.todayShelves(CategoryShelf.shelves(categories, catalogue), emptyList(), isGuest = false)
        assertEquals(emptyList<String>(), shelves.map { it.category.id })
    }

    @Test fun `a guest gets the first few shelves as a preview`() {
        val shelves = StudyThemes.todayShelves(CategoryShelf.shelves(categories, catalogue), emptyList(), isGuest = true)
        assertEquals(listOf("kitchen", "bathroom", "bedroom", "office"), shelves.map { it.category.id })
    }

    /** iOS's merge order: public < custom < saved, the later source winning. */
    @Test fun `a duplicate id takes the later source's row`() {
        val words = StudyThemes.words(
            catalogue = listOf(word("x", "kitchen", "public")),
            mine = listOf(word("x", "custom", "mine")),
            taken = listOf(word("x", "community", "taken")),
        )
        assertEquals(listOf("taken"), words.map { it.word })
    }

    @Test fun `the count in a selection sees every source`() {
        val words = StudyThemes.words(catalogue, mine = listOf(word("m1", "custom")), taken = emptyList())
        assertEquals(3, StudyThemes.countIn(words, listOf("kitchen", "custom")))
        assertEquals(0, StudyThemes.countIn(words, emptyList()))
    }
}
