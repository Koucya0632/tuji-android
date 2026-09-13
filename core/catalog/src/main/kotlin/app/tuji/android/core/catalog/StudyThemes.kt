package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word

/**
 * The 學習主題 selection, applied to words.
 *
 * Two themes in the selection are not in the catalogue at all: 自定義
 * (`custom`, the user's own cards) and 物見 (`community`, what they took in).
 * Their words arrive from two other endpoints, so anything that counts or
 * draws "the words in my themes" has to see all three sources — otherwise
 * 自定義 is a ticked theme that 今日 never shows and 完成度 never counts.
 */
object StudyThemes {

    /** How many catalogue shelves a guest is shown: they have picked nothing, so this is a preview. */
    const val GUEST_PREVIEW = 4

    /**
     * The catalogue plus the user's own and taken-in cards, as iOS's
     * `WordsStore.merge` builds it: public < custom < saved, a later source
     * winning a duplicate id. Catalogue order is kept and new rows follow.
     */
    fun words(catalogue: List<Word>, mine: List<Word>, taken: List<Word>): List<Word> {
        val byId = LinkedHashMap<String, Word>()
        for (word in catalogue) byId[word.id] = word
        for (word in mine) byId[word.id] = word
        for (word in taken) byId[word.id] = word
        return byId.values.toList()
    }

    /**
     * The shelves 今日's strip shows.
     *
     * A signed-in user sees exactly their picked themes, in the server's
     * category order, minus the empty ones; nothing picked is nothing shown
     * (今日 asks them to pick instead). A guest has no selection to honour and
     * gets the first few shelves as a taste.
     */
    fun todayShelves(
        shelves: List<CategoryShelf.Shelf>,
        selected: List<String>,
        isGuest: Boolean,
    ): List<CategoryShelf.Shelf> {
        if (isGuest) return shelves.take(GUEST_PREVIEW)
        val picked = selected.toSet()
        return shelves.filter { it.category.id in picked }
    }

    /** Words in the selection, from every source. Empty selection counts nothing. */
    fun countIn(words: List<Word>, selected: List<String>): Int {
        val picked = selected.toSet()
        return words.count { it.category in picked }
    }
}
