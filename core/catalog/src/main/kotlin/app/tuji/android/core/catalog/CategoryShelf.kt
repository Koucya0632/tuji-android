package app.tuji.android.core.catalog

import app.tuji.android.core.model.Category
import app.tuji.android.core.model.Word

/**
 * Which shelves the 圖鑑 shows, and how many words are on each.
 *
 * The counts come from the catalogue the app already holds rather than a second
 * endpoint: they have to agree with what tapping the shelf actually opens, and
 * two sources that can disagree eventually will.
 */
object CategoryShelf {

    data class Shelf(val category: Category, val count: Int)

    /**
     * Shelves in the order the server sent them, minus the empty ones.
     *
     * **Emptiness is the rule, not a list of ids to hide.** `custom` (自定義)
     * and `community` (物見) come back from the server with zero words until
     * the milestones that fill them land, and a shelf that opens onto nothing
     * is a dead end wearing a door. Written as "hide what is empty" rather than
     * "hide these two", the two reappear on their own the day they have
     * contents — no code change, and nothing to forget.
     */
    fun shelves(categories: List<Category>, words: List<Word>): List<Shelf> {
        val counts = words.groupingBy { it.category.orEmpty() }.eachCount()
        return categories.mapNotNull { category ->
            val count = counts[category.id] ?: 0
            if (count > 0) Shelf(category, count) else null
        }
    }

    /** The words on one shelf, in catalogue order. */
    fun words(categoryId: String, words: List<Word>): List<Word> =
        words.filter { it.category == categoryId }

    /**
     * What a shelf is called in the UI language.
     *
     * `nameZh` for a zh UI and `name` otherwise, falling back the other way
     * when the field is missing rather than printing an id — 「bathroom」 in a
     * Chinese UI is a bug the user can see, and an empty header is worse.
     */
    fun title(category: Category, uiLang: String): String =
        if (uiLang.startsWith("zh")) {
            category.nameZh?.takeIf { it.isNotBlank() } ?: category.name
        } else {
            category.name.takeIf { it.isNotBlank() } ?: category.nameZh.orEmpty()
        }
}
