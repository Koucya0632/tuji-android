package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyThemesTest {

    private fun word(id: String, category: String, text: String = id) = Word(id = id, word = text, category = category)

    private val catalogue = listOf(
        word("k1", "kitchen"), word("k2", "kitchen"), word("b1", "bathroom"),
        word("r1", "bedroom"), word("o1", "office"), word("g1", "garden"),
    )

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

    @Test
    fun `no gate studies the pick as it is`() {
        assertEquals(listOf("kitchen", "custom"), StudyThemes.effective(listOf("kitchen", "custom"), studyable = null))
        assertEquals(emptyList<String>(), StudyThemes.effective(emptyList(), studyable = null))
    }

    @Test
    fun `a gate keeps only the studyable part of the pick`() {
        val studyable = listOf("fruits", "bedroom")
        assertEquals(listOf("fruits"), StudyThemes.effective(listOf("kitchen", "fruits", "custom"), studyable))
    }

    @Test
    fun `a gated pick with nothing studyable falls back to the studyable list, never to empty`() {
        // Empty means "every theme" to the queue and to 完成度.
        val studyable = listOf("fruits", "bedroom")
        assertEquals(listOf("bedroom", "fruits"), StudyThemes.effective(listOf("kitchen"), studyable))
        assertEquals(listOf("bedroom", "fruits"), StudyThemes.effective(emptyList(), studyable))
    }

    @Test
    fun `only a gate locks a theme`() {
        assertEquals(false, StudyThemes.isLocked("kitchen", studyable = null))
        assertEquals(true, StudyThemes.isLocked("kitchen", listOf("fruits")))
        assertEquals(true, StudyThemes.isLocked("custom", listOf("fruits")))
        assertEquals(false, StudyThemes.isLocked("fruits", listOf("fruits")))
    }
}
