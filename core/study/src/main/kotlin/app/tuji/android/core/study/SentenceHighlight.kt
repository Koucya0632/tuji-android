package app.tuji.android.core.study

/**
 * Where the word being asked about sits inside its own example sentence — iOS's
 * `SentenceHighlight`, and for the reason iOS matches here rather than trusting
 * the server's 詞塊: measured against the live corpus, a substring match finds
 * the target more often (en 939/952, ja 945/952) than the spans' `word_id`
 * does (838 and 894), because spans only carry an id where the base form
 * resolved.
 *
 * The ~1% it misses are sentences that never spell the headword (`scanner` in
 * "Scan both sides"). Those get no highlight, which is the right failure: a
 * missing highlight is quieter than a wrong one.
 */
object SentenceHighlight {

    /**
     * The half-open `[first, last)` range of [sentence] that spells [word], or
     * null when it does not.
     *
     * Case-insensitive, and for a headword written in ASCII it demands a word
     * boundary — without one, `cup` would light up inside `cupboard`. The
     * boundary is **not** applied to Japanese: kana and kanji are letters, so
     * requiring a non-letter neighbour would reject every Japanese sentence.
     */
    fun range(word: String, sentence: String): IntRange? {
        val needle = word.trim()
        if (needle.isEmpty() || sentence.isEmpty()) return null
        val latin = needle.all { it.code < 128 }

        var from = 0
        while (true) {
            val at = sentence.indexOf(needle, startIndex = from, ignoreCase = true)
            if (at < 0) return null
            val end = at + needle.length
            if (!latin) return at until end
            if (startsAWord(at, sentence)) wordEnd(end, sentence)?.let { return at until it }
            from = end
        }
    }

    private fun startsAWord(index: Int, sentence: String): Boolean =
        index == 0 || !sentence[index - 1].isLetter()

    /**
     * The end of the match, having swallowed a plural `s` or `es` if one is
     * there — "Please open the curtains" otherwise leaves the last letter
     * outside the highlighter, which reads as a rendering bug. Only those two:
     * "any trailing letters" would stretch `grate` over `grater`.
     */
    private fun wordEnd(index: Int, sentence: String): Int? {
        for (suffix in listOf("es", "s")) {
            val after = index + suffix.length
            if (after > sentence.length) continue
            if (!sentence.substring(index, after).equals(suffix, ignoreCase = true)) continue
            if (after == sentence.length || !sentence[after].isLetter()) return after
        }
        return if (index == sentence.length || !sentence[index].isLetter()) index else null
    }
}
