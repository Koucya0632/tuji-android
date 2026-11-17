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

    /** Words in the selection, from every source. Empty selection counts nothing. */
    fun countIn(words: List<Word>, selected: List<String>): Int {
        val picked = selected.toSet()
        return words.count { it.category in picked }
    }

    /**
     * The themes that actually get studied: the pick, narrowed to what the
     * server says this account may study (`membership.studyableCategories`).
     *
     * A hidden-region selection remains nonempty when no allowed theme remains,
     * so it cannot expand to all free themes. Visible-only locked selections
     * retain the existing fallback; unknown visibility stays conservative.
     */
    fun effective(selected: List<String>, studyable: List<String>?, visibleCategoryIds: Set<String>? = null): List<String> {
        if (studyable == null) return selected
        val allowed = studyable.toSet()
        val kept = selected.filter { it in allowed }
        if (kept.isNotEmpty()) return kept
        if (selected.isNotEmpty()) {
            if (visibleCategoryIds == null) return selected
            val hidden = selected.filterNot { it in visibleCategoryIds }
            if (hidden.isNotEmpty()) return hidden
        }
        return studyable.sorted()
    }

    /** A theme this account may browse but not study. Never locked without a gate. */
    fun isLocked(categoryId: String, studyable: List<String>?): Boolean =
        studyable != null && categoryId !in studyable
}
