package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.asHeadworded
import app.tuji.android.core.model.language

/**
 * Why a label may not stand beside the answer — or that it may.
 *
 * A returned **value**, not a private boolean. On iOS these four rules were the
 * reason the module existed and every one of them was unreachable: they lived
 * in file-private functions assertable only through a seeded shuffle, by
 * absence. The most valuable logic there sat behind the least testable door.
 */
enum class DistractorFairness {
    Fair,

    /** The label *is* the answer, modulo case. */
    SameTerm,

    /** One term's word tokens contain the other's: knife / kitchen knife. */
    TokenSubset,

    /** CJK has no token boundaries, so substring stands in: 時計 / 腕時計. */
    CjkSubstring,

    /** The dictionary translates both identically: pan / frying pan → 平底鍋. */
    SharedGloss,
}

/**
 * The fairness question for one question's answer, against one dictionary.
 *
 * Built once per call rather than per candidate: the gloss index is a full pass
 * over the pool.
 *
 * The rules exist because the server's distractor draw is category-scoped, so a
 * 平底鍋 question could offer both "pan" (the answer) and "frying pan" (a
 * distractor) — two words the dictionary translates identically. **A learner
 * who knows the word can still be marked wrong**, and that is the failure being
 * prevented.
 */
class DistractorPool(
    private val answer: String,
    gloss: String?,
    pool: List<Word>,
) {
    private val answerGlosses: Set<String> = chineseGlosses(gloss)
    private val glossIndex: Map<String, Set<String>> = buildGlossIndex(pool)

    /**
     * A distractor is unfair when a learner who knows the answer could
     * legitimately pick it.
     */
    fun fairness(of: String): DistractorFairness {
        if (of.equals(answer, ignoreCase = true)) return DistractorFairness.SameTerm

        val answerTokens = wordTokens(answer)
        val labelTokens = wordTokens(of)
        if (answerTokens.isNotEmpty() && labelTokens.isNotEmpty() &&
            (labelTokens.containsAll(answerTokens) || answerTokens.containsAll(labelTokens))
        ) {
            return DistractorFairness.TokenSubset
        }

        if (containsCjk(of) || containsCjk(answer)) {
            val a = answer.lowercase()
            val l = of.lowercase()
            if (a.contains(l) || l.contains(a)) return DistractorFairness.CjkSubstring
        }

        if (answerGlosses.isNotEmpty()) {
            val glosses = glossIndex[of.lowercase()]
            if (glosses != null && glosses.any { it in answerGlosses }) {
                return DistractorFairness.SharedGloss
            }
        }
        return DistractorFairness.Fair
    }

    companion object {
        /** Lowercased word tokens. CJK terms come back as one token; the
         *  substring rule covers those instead. */
        fun wordTokens(term: String): Set<String> =
            term.lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotEmpty() }
                .toSet()

        /**
         * Individual Chinese glosses from a dictionary `chinese` field, which
         * packs synonyms as 「鍋子 / 湯鍋」 or 「爐子／瓦斯爐」 style lists.
         */
        fun chineseGlosses(chinese: String?): Set<String> =
            chinese.orEmpty()
                .split(*"/／、,，;；".toCharArray())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

        fun containsCjk(s: String): Boolean = s.any { ch ->
            val c = ch.code
            c in 0x4E00..0x9FFF || c in 0x3040..0x30FF
        }

        /** Term → union of glosses across the dictionary (a label can exist in
         *  both the EN and JA decks). */
        private fun buildGlossIndex(pool: List<Word>): Map<String, Set<String>> {
            val index = mutableMapOf<String, MutableSet<String>>()
            for (word in pool) {
                index.getOrPut(word.word.lowercase()) { mutableSetOf() }
                    .addAll(chineseGlosses(word.chinese))
            }
            return index
        }
    }
}

/**
 * Up to four MCQ option labels for [item]: the correct answer plus fair
 * distractors.
 *
 * Server-provided `choices` are preferred — they are difficulty-curated, same
 * category — but **scrubbed** first, then the set is topped up from [pool].
 * 自制圖鑑 cards have no server choices and build the whole set from the pool.
 *
 * [variant] folds into the seed: the coordinator bumps it per wrong attempt so
 * a requeued question re-shuffles. Otherwise remembering "the answer was C"
 * stands in for knowing the word.
 *
 * [session] is 當前圖鑑語言, used to place words the server did not tag. iOS's
 * top-up used to skip its same-language filter entirely for an untagged
 * question, drawing English distractors under a Japanese answer.
 */
fun studyChoices(
    item: StudyQueueItem,
    pool: List<Word>,
    session: TargetLanguage,
    variant: Int = 0,
): List<String> {
    val answer = item.word.word
    val random = SeededRandom(studyStableHash(item.id) + variant * -0x61c8864680b583ebL)
    val fairness = DistractorPool(answer = answer, gloss = item.word.chinese, pool = pool)

    val seen = mutableSetOf(answer.lowercase())
    val distractors = mutableListOf<String>()

    fun admit(label: String) {
        if (distractors.size >= 3) return
        if (label.isEmpty()) return
        if (fairness.fairness(label) != DistractorFairness.Fair) return
        if (!seen.add(label.lowercase())) return
        distractors.add(label)
    }

    // Server distractors first.
    item.choices?.forEach(::admit)

    if (distractors.size < 3) {
        val lang = item.word.language(session)
        // Same language first, so an untagged custom word still lands in the
        // right half of the pool.
        pool.filter { it.asHeadworded().language(session) == lang }
            .shuffled(random)
            .forEach { admit(it.word) }
        if (distractors.size < 3) {
            // The same-language pool was thin (a brand-new account) — widen so
            // the quiz still has plausible-ish options.
            pool.shuffled(random).forEach { admit(it.word) }
        }
    }

    return (listOf(answer) + distractors).shuffled(random)
}
