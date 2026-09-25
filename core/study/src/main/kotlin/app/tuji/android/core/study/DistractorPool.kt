package app.tuji.android.core.study

import app.tuji.android.core.model.StudyChoiceCandidate
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
    Synonym,
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
        if (choiceKey(of) == choiceKey(answer)) return DistractorFairness.SameTerm

        if (choiceAliasesConflict(of, answer)) return DistractorFairness.Synonym
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
            val glosses = glossIndex[choiceKey(of)]
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
                index.getOrPut(choiceKey(word.word)) { mutableSetOf() }
                    .addAll(chineseGlosses(word.chinese))
            }
            return index
        }
    }
}

/** Four labels from server candidates, falling back to same-language local data.
 * StudyChoiceSession owns the fresh round seed, stable snapshots and retry history. */
fun studyChoices(
    item: StudyQueueItem,
    pool: List<Word>,
    session: TargetLanguage,
    variant: Int = 0,
    seed: Long? = null,
    previous: List<String> = emptyList(),
): List<String> {
    val language = item.word.language(session)
    val target = StudyChoiceCandidate(wordId = item.id, label = item.word.word, language = language,
        gloss = item.word.chinese, category = item.word.category, exclusions = item.choiceExclusions.orEmpty(), tier = 0, weight = 1.0)
    val local = pool.filter { it.asHeadworded().language(session) == language }.map { word ->
        StudyChoiceCandidate(wordId = word.id, label = word.word, language = language, gloss = word.chinese.orEmpty(),
            category = word.category, tier = if (word.category == target.category) 2 else 3,
            weight = 1.0)
    }
    val known = local + StudyChoiceData.reserve.filter { it.language == language }
    val legacy = item.choices.orEmpty().mapNotNull { label -> known.firstOrNull { choiceKey(it.label) == choiceKey(label) } }
    val server = item.choiceCandidates.orEmpty()
    val candidates = if (prepareChoiceCandidates(target, server).size >= 3) server else server + local + legacy
    return assembleStudyChoices(target, candidates,
        seed ?: choiceHash("${language.name.lowercase(java.util.Locale.ROOT)}:${item.id}:$variant"), previous)
}
