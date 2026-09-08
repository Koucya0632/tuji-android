package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueItem

/**
 * 拼字題目 — what the spell stage is asking the learner to assemble.
 *
 * On iOS this was two predicates over `reading`, both documented as
 * "distinguishes JA from EN". They do not: バスマット is Japanese and its 振假名
 * is itself, so the stage quizzes the 詞形 and the answer is [Term]. The
 * language question and this one agree on most words and part company on
 * exactly the words that make 振假名 subtle — which is why they get separate
 * names.
 */
sealed interface SpellSubject {
    val text: String

    /** A kana reading distinct from the written term: 排出這個字的讀音. */
    data class Reading(override val text: String) : SpellSubject

    /** The term itself: 拼出這個字. Every English word, and kana-only Japanese. */
    data class Term(override val text: String) : SpellSubject

    val isReading: Boolean get() = this is Reading
}

/**
 * The tile puzzle for one word: correct-order units grouped per whitespace
 * token.
 *
 * Token boundaries drive the slot rows (a space is never a tile); correctness
 * compares the assembled picks against the whitespace-stripped [target], so
 * "cutting board" is solved as cutting+board on two rows.
 */
data class TileBoard(val tokenUnits: List<List<String>>) {
    val orderedUnits: List<String> get() = tokenUnits.flatten()
    val target: String get() = orderedUnits.joinToString("")
    val unitCount: Int get() = tokenUnits.sumOf { it.size }

    companion object {
        /** Board caps at 10 tiles; longer subjects re-chunk so the pool stays a
         *  recall task instead of a 13-tile hunt. */
        const val MAX_TILE_COUNT = 10

        /**
         * Small kana that merge into the preceding unit so a yōon like きょ is
         * one tile. Sokuon っ/ッ stays standalone — it is a full mora.
         */
        private val MERGING_SMALL_KANA = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮ".toSet()

        /**
         * `reading` is a JA-only backend field, so a non-empty one that differs
         * from the term is a kana reading worth quizzing on its own.
         */
        fun spellSubject(item: StudyQueueItem): SpellSubject {
            val reading = item.word.reading
            if (reading.isNullOrEmpty()) return SpellSubject.Term(item.word.word)
            return if (reading == item.word.word) {
                SpellSubject.Term(reading)
            } else {
                SpellSubject.Reading(reading)
            }
        }

        /**
         * Board layout for a word — deterministic per item and independent of
         * the retry attempt. Chunk boundaries must not move between retries;
         * only the pool shuffle re-seeds.
         */
        fun of(item: StudyQueueItem): TileBoard {
            var tokenUnits = spellSubject(item).text
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { baseUnits(it) }
            val total = tokenUnits.sumOf { it.size }
            if (total > MAX_TILE_COUNT) {
                val chunkLen = Math.ceil(total.toDouble() / MAX_TILE_COUNT).toInt()
                tokenUnits = tokenUnits.map { chunked(it, chunkLen) }
            }
            return TileBoard(tokenUnits)
        }

        /** One grapheme per unit, with small kana glued to their base kana. */
        private fun baseUnits(token: String): List<String> {
            val units = mutableListOf<String>()
            for (ch in token) {
                if (ch in MERGING_SMALL_KANA && units.isNotEmpty()) {
                    units[units.lastIndex] = units.last() + ch
                } else {
                    units.add(ch.toString())
                }
            }
            return units
        }

        /** Regroup consecutive units into chunks, never across tokens. */
        private fun chunked(units: List<String>, size: Int): List<String> {
            if (size <= 1) return units
            return units.chunked(size) { it.joinToString("") }
        }
    }
}
