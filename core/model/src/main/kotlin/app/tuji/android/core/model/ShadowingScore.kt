package app.tuji.android.core.model

/**
 * 跟讀 — how much of a line the speech recogniser heard. iOS's `ShadowingScore`.
 *
 * This is not a pronunciation grade. Recognition only says what it thinks was
 * said, so the score answers "would a listener have caught these words", not
 * "was the accent right".
 *
 * - English is compared word by word, lowercased, punctuation dropped.
 * - Japanese comes back from the recogniser in whatever mix of kanji and kana
 *   it likes (傘 / かさ / カサ). iOS turns both sides into kana with a system
 *   tokenizer Android does not have; here the line's own furigana does that for
 *   the line, and each unit counts as heard by whichever of its spellings — as
 *   written, or as read — the transcript holds more of. Katakana is folded to
 *   hiragana on both sides.
 *
 * A unit of the line counts as heard when most of its characters appear, in
 * order, in what was heard. Punctuation is not scored either way.
 */
data class ShadowingScore(val words: List<Word>, val ratio: Double) {
    data class Word(val surface: String, val heard: Boolean)

    companion object {
        /** Below this share of its characters a unit is not counted as heard. */
        private const val WORD_THRESHOLD = 0.6

        fun of(target: String, heard: String, language: TargetLanguage, furigana: List<FuriganaSegment>? = null): ShadowingScore =
            when (language) {
                TargetLanguage.EN -> english(target, heard)
                TargetLanguage.JA -> japanese(target, heard, furigana)
            }

        private fun english(target: String, heard: String): ShadowingScore {
            val line = englishUnits(target)
            val spoken = englishUnits(heard)
            val flags = matchedFlags(line.map { it.second }, spoken.map { it.second })
            val words = line.zip(flags) { unit, hit -> Word(unit.first, hit) }
            return ShadowingScore(words, if (line.isEmpty()) 0.0 else flags.count { it }.toDouble() / line.size)
        }

        /** (surface, key) per word; a word with nothing to compare is dropped. */
        private fun englishUnits(text: String): List<Pair<String, String>> =
            text.split(Regex("\\s+")).mapNotNull { piece ->
                val key = piece.lowercase().filter { it.isLetterOrDigit() || it == '\'' }.trim('\'')
                if (key.isEmpty()) null else piece.trim { !it.isLetterOrDigit() && it != '\'' } to key
            }

        private fun japanese(target: String, heard: String, furigana: List<FuriganaSegment>?): ShadowingScore {
            // A unit is a furigana segment when there are segments (a kanji run
            // with its reading, or a stretch of kana), else one character.
            val units = furigana?.takeIf { it.isNotEmpty() && it.joinToString("") { s -> s.text } == target }
                ?.map { it.text to it.ruby }
                ?: target.map { it.toString() to null }
            val written = units.map { (text, _) -> normalize(text) }
            val read = units.map { (text, ruby) -> normalize(ruby ?: text) }
            val spoken = normalize(heard).toList()
            val byWriting = perUnit(written, spoken)
            val byReading = perUnit(read, spoken)
            var heardCount = 0
            var total = 0
            val words = mutableListOf<Word>()
            units.forEachIndexed { index, (surface, _) ->
                val (hitW, countW) = byWriting[index]
                val (hitR, countR) = byReading[index]
                // Whichever spelling the transcript holds more of — 傘 or かさ.
                val writingShare = if (countW == 0) 0.0 else hitW.toDouble() / countW
                val readingShare = if (countR == 0) 0.0 else hitR.toDouble() / countR
                val (hit, count) = if (readingShare > writingShare) hitR to countR else hitW to countW
                if (count == 0) return@forEachIndexed
                heardCount += hit
                total += count
                words += Word(surface, hit >= count * WORD_THRESHOLD)
            }
            return ShadowingScore(words, if (total == 0) 0.0 else heardCount.toDouble() / total)
        }

        /** (matched, total) characters per unit, aligned across the whole line at once. */
        private fun perUnit(units: List<String>, spoken: List<Char>): List<Pair<Int, Int>> {
            val flags = matchedFlags(units.joinToString("").toList(), spoken)
            var offset = 0
            return units.map { unit ->
                val hit = (offset until offset + unit.length).count { flags[it] }
                offset += unit.length
                hit to unit.length
            }
        }

        /** Hiragana, kanji, the long-vowel mark, letters and digits; katakana folded to hiragana. */
        private fun normalize(text: String): String = buildString {
            for (c in text) {
                val folded = if (c in 'ァ'..'ヶ') (c - 0x60) else c
                if (folded in 'ぁ'..'ゖ' || folded == 'ー' || folded == '々' || isKanji(folded) || folded.isLetterOrDigit()) {
                    append(folded.lowercaseChar())
                }
            }
        }

        private fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'

        /** Which elements of [target] take part in a longest common subsequence with [heard]. */
        fun <T> matchedFlags(target: List<T>, heard: List<T>): List<Boolean> {
            val n = target.size
            val m = heard.size
            if (n == 0) return emptyList()
            if (m == 0) return List(n) { false }
            val table = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) {
                for (j in m - 1 downTo 0) {
                    table[i][j] = if (target[i] == heard[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
                }
            }
            val flags = BooleanArray(n)
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    target[i] == heard[j] -> {
                        flags[i] = true
                        i++
                        j++
                    }
                    table[i + 1][j] >= table[i][j + 1] -> i++
                    else -> j++
                }
            }
            return flags.toList()
        }
    }
}
