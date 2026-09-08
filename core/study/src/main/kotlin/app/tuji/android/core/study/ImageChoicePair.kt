package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind

/**
 * The two pictures 聽句 offers, and the four reasons a picture may not stand
 * beside the answer.
 *
 * [DistractorPool] answers the same question for *labels* and this leans on it
 * for two of the four rules, because "pan" and "frying pan" are no fairer as
 * two photographs of a pan than as two words. The other two only exist once the
 * options are pictures:
 *
 *  - **Not a word the sentence also names.** Draw one and *both* pictures are
 *    correct; see `StudyExample.mentionedWordIds` for what that costs the user.
 *  - **Same [WordImageKind].** One cut-out and one photograph and the odd one
 *    out is visible without listening to anything.
 *
 * And one rule about where the pool comes from rather than what is in it: never
 * draw from the cards still queued this session. That word is going to be asked
 * about later, and showing its picture now is a free look.
 */
object ImageChoicePair {

    /**
     * The answer and one fair distractor, in a stable random order, or null
     * when the pool cannot produce a distractor.
     *
     * **Null is a real answer, not an error**: the caller falls back to 選字,
     * the same fallback every other ineligible card takes. A brand new account
     * whose catalogue has not loaded, or a language with a thin pool, lands
     * here.
     *
     * The order is seeded from the item id (and [variant], which the caller
     * bumps per presentation) rather than shuffled freshly, for the reason
     * [studyChoices] spells out: a composable's body is re-run on every
     * recomposition, so an unseeded shuffle makes the options jump under the
     * user's thumb. [variant] is what makes a re-test re-draw instead of
     * letting "the answer was on the left" stand in for the word.
     */
    fun options(
        item: StudyQueueItem,
        pool: List<Word>,
        session: TargetLanguage,
        mentionedWordIds: Set<String>,
        queuedWordIds: Set<String>,
        variant: Int = 0,
    ): List<ImageChoiceOption>? {
        val imageUrl = item.word.imageUrl
        if (imageUrl.isBlank()) return null

        val answer = ImageChoiceOption(
            id = item.word.id,
            word = item.word.word,
            imageUrl = imageUrl,
            imageKind = WordImageKind.of(item.word.category),
        )

        val random = SeededRandom(
            studyStableHash("listen:" + item.id) + variant.toLong() * -0x61c8864680b583ebL,
        )
        val fairness = DistractorPool(
            answer = answer.word,
            gloss = item.word.chinese,
            pool = pool,
        )

        fun admits(candidate: Word): Boolean {
            if (candidate.id == answer.id) return false
            if (candidate.imageUrl.isNullOrBlank()) return false
            if (candidate.id in mentionedWordIds) return false
            if (candidate.id in queuedWordIds) return false
            if (WordImageKind.of(candidate.category) != answer.imageKind) return false
            if ((candidate.targetLanguage ?: session) != session) return false
            return fairness.fairness(candidate.word) == DistractorFairness.Fair
        }

        val distractor = pool.filter(::admits).shuffled(random).firstOrNull() ?: return null

        return listOf(
            answer,
            ImageChoiceOption(
                id = distractor.id,
                word = distractor.word,
                imageUrl = distractor.imageUrl.orEmpty(),
                imageKind = WordImageKind.of(distractor.category),
            ),
        ).shuffled(random)
    }
}
