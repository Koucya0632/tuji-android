package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.model.StudyQueueItem

/**
 * Which sentence 聽句 asks, and which cards get asked at all.
 *
 * Two pure decisions, kept out of [app.tuji.android.study.ReviewViewModel]
 * because both are total functions over data a test can hand them, and neither
 * needs a beat, a lock, or a network.
 */
object ListeningQuestion {

    /**
     * Above this mastery the word gets the harder (B1) sentence, below it the
     * simpler (A2) one.
     *
     * Deliberately the same 50 the rating suggestion already uses to decide
     * whether a two-second answer earns 熟練. That number is answering the same
     * question — is this word actually established, or merely recalled — and a
     * second threshold would be a second constant nobody could explain.
     */
    const val MASTERY_TIER = 50

    /**
     * CEFR level of the simpler / harder sentence of an authored pair. Every
     * published word has exactly one of each, enforced on every production
     * migrate, so these are the whole ladder.
     */
    const val SIMPLE_LEVEL = "A2"
    const val COMPLEX_LEVEL = "B1"

    /**
     * One in this many eligible cards is asked as 聽句.
     *
     * A constant rather than a dice roll: random clusters, and three listening
     * questions in a row turns a review into a listening test the user never
     * asked for — each one costs an extra tap (the reveal sheet is mandatory)
     * plus the clip. A constant is also the only version the tests can assert;
     * a roll would need a seed injected purely so the assertion could exist.
     */
    const val EVERY_N = 4

    /**
     * The sentence to ask about, or null when the card carries none.
     *
     * [presentation] is how many times this word has already been *left* — the
     * same counter that reshuffles MCQ options. Presentation 0 takes the tier
     * its mastery earns; a re-test (presentation ≥ 1) takes the other sentence,
     * because replaying the recording the user just failed is not practice.
     * With more presentations than sentences it clamps rather than wrapping: a
     * third look at a two-sentence word has nothing new to offer, and
     * pretending otherwise would just alternate.
     */
    fun example(item: StudyQueueItem, mastery: Int?, presentation: Int): StudyExample? {
        val examples = item.examples.orEmpty()
        if (examples.isEmpty()) return null
        val wanted = if ((mastery ?: 0) >= MASTERY_TIER) COMPLEX_LEVEL else SIMPLE_LEVEL
        // A stable partition, not a sort: within a tier the authored order is
        // the only order there is, so anything that could reorder a tier
        // between two identical calls would move the question under the user.
        val ordered = examples.filter { it.cefrLevel == wanted } +
            examples.filter { it.cefrLevel != wanted }
        return ordered[presentation.coerceIn(0, ordered.size - 1)]
    }

    /**
     * Whether this word's turn falls on a 聽句 slot. Hashed rather than counted
     * so the answer does not move when the queue is re-ordered, and
     * [studyStableHash] rather than the platform hash so it does not move
     * between launches either.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun fallsOnSlot(wordId: String): Boolean =
        // Unsigned, because the hash is an FNV bit pattern and Kotlin's Long is
        // signed: a signed `%` would fold the negative half of the space onto
        // the wrong slots. `and (EVERY_N - 1)` would agree today and stop
        // agreeing the moment someone makes EVERY_N a non-power-of-two.
        studyStableHash(wordId).toULong() % EVERY_N.toULong() == 0uL

    /**
     * The question to ask, given what the card can support and what the last
     * one was.
     *
     * @param canHear this presentation has a sentence *and* a clip that will
     *   actually play right now. Offline with nothing cached is a no: a
     *   Japanese sentence read by an on-device voice guessing at kanji is not a
     *   worse question, it is an unanswerable one.
     * @param previous the kind the *previous presentation* used. Two 聽句 in a
     *   row is the cluster [EVERY_N] exists to avoid, so the second demotes.
     * @param alreadyHeard this word has already been asked as 聽句 this session,
     *   i.e. this is its re-test. A re-test keeps its question — it is practice
     *   on what was missed, and it writes no SRS — so the spacing rule does not
     *   apply to it.
     */
    fun kind(
        wordId: String,
        canHear: Boolean,
        previous: ReviewQuestionKind?,
        alreadyHeard: Boolean,
    ): ReviewQuestionKind {
        if (!canHear) return ReviewQuestionKind.PickWord
        if (alreadyHeard) return ReviewQuestionKind.HearSentence
        if (previous == ReviewQuestionKind.HearSentence) return ReviewQuestionKind.PickWord
        return if (fallsOnSlot(wordId)) {
            ReviewQuestionKind.HearSentence
        } else {
            ReviewQuestionKind.PickWord
        }
    }
}
