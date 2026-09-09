package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueWord

/**
 * What 求救提示 turns the picture over to.
 *
 * The hint used to be the gloss, always. For a zh reader that is the answer
 * translated — 水桶 — so the hint and the answer were one fact in two languages,
 * and the flip taught nothing it did not also give away. The 釋義 is the better
 * prompt: 附提把、開口朝上的圓柱形容器.
 *
 * Two cases rather than one string, because the two are *read* differently — a
 * 釋義 is prose and sets as body text, a gloss is a word and sets as a headline.
 * The typography stays in the view; which of the two this card has is decided
 * here, where a test can reach it. It was a private function in the view file,
 * which meant the rule was stated twice in the same composable — once to pick
 * the text and once to pick the font — and nothing checked they agreed.
 *
 * `reading` and `pronunciation` are on the same payload and neither may come
 * here (ADR-0007): a kana headword's 振假名 is itself, and an IPA line is the
 * word read aloud — either one turns the hint into a skip.
 */
sealed interface HintFace {

    /** The text on the face — and the text a screen reader is given. */
    val text: String

    /** One explanatory sentence in the reader's own language. */
    data class Definition(override val text: String) : HintFace

    /**
     * The one-line gloss: what the flip showed before there was a 釋義 to show,
     * and what it still shows for a word without one.
     */
    data class Gloss(override val text: String) : HintFace

    companion object {
        /**
         * The rule is one line, and the reason it can be one line is that the
         * server already refuses to send a 釋義 that repeats the gloss. That
         * equality is monolingual study (UI language == target language), where
         * the gloss *is* the explanatory definition — the one thing this face
         * may not carry. Re-deriving it here would mean asking the UI language
         * and the target language a question the server has already answered,
         * and a rule stated in two places is a rule that can disagree with
         * itself.
         */
        fun of(word: StudyQueueWord): HintFace {
            val definition = word.definition?.trim().orEmpty()
            return if (definition.isEmpty() || definition == word.chinese.trim()) {
                Gloss(word.chinese)
            } else {
                Definition(definition)
            }
        }
    }
}
