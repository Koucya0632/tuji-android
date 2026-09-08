package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word

/**
 * Ranked, case-insensitive matching over the catalogue held in memory.
 *
 * Every keystroke filters the 557 rows the app already has rather than asking
 * the server, so results appear instantly and keep appearing on a plane. A
 * remote search exists on iOS for the matches a local list cannot see —
 * synonyms, fuzzy spellings — and it merges *after* these; this module is the
 * half that has to be right offline.
 */
object WordSearch {

    /**
     * How well a row answers the query. Lower sorts first, and the order is the
     * point: someone typing 「か」 wants the words that *start* with it before
     * the ones that merely contain it somewhere.
     */
    private const val RANK_EXACT = 0
    private const val RANK_WORD_PREFIX = 1
    private const val RANK_GLOSS_PREFIX = 2
    private const val RANK_WORD_CONTAINS = 3
    private const val RANK_GLOSS_CONTAINS = 4
    private const val RANK_READING = 5
    private const val RANK_PRONUNCIATION = 6

    /**
     * Rows matching [query], best first.
     *
     * Ties break on the shorter term: with 「はし」 matching both 箸 and 箸置き,
     * the word that *is* the query is more likely what was meant than the word
     * that contains it.
     *
     * An empty or blank query matches nothing rather than everything — a search
     * field with nothing typed into it has not asked for the whole dictionary,
     * and returning it would flash 557 rows between keystrokes.
     */
    fun matches(query: String, words: List<Word>): List<Word> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()

        return words
            .mapNotNull { word -> rank(needle, word)?.let { word to it } }
            .sortedWith(compareBy({ it.second }, { it.first.word.length }))
            .map { it.first }
    }

    private fun rank(needle: String, w: Word): Int? {
        val term = w.word.lowercase()
        val gloss = w.chinese?.lowercase().orEmpty()
        val reading = w.reading?.lowercase().orEmpty()
        val pronunciation = w.pronunciation?.lowercase().orEmpty()

        return when {
            term == needle -> RANK_EXACT
            term.startsWith(needle) -> RANK_WORD_PREFIX
            gloss.startsWith(needle) -> RANK_GLOSS_PREFIX
            term.contains(needle) -> RANK_WORD_CONTAINS
            gloss.contains(needle) -> RANK_GLOSS_CONTAINS
            reading.contains(needle) -> RANK_READING
            pronunciation.contains(needle) -> RANK_PRONUNCIATION
            else -> null
        }
    }
}
