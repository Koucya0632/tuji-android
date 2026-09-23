package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueItem

/**
 * Which board the 拼字 stage draws for this word.
 *
 * English replaced the from-scratch tile board with a gap-fill: re-assembling
 * every letter quizzes "do you remember each character", while English spelling
 * goes wrong in a handful of places worth cutting out. Japanese keeps the tiles
 * — its 拼字 asks for a kana reading, which has no orthographic confusables to
 * cut.
 *
 * The split is decided on the subject's *script*, not on `targetLanguage`: that
 * field is optional, and バスマット is a [SpellSubject.Term] too, so a
 * Latin-letter test leaves kana on the tile board without having to trust the
 * tag.
 *
 * This lives in `core:study` rather than beside the screen because
 * [StudyLadder] gates the whole stage on it — **one predicate, so the gate and
 * the board can never disagree about which words get a 拼字 at all.**
 */
sealed interface SpellForm {
    data class Gaps(val plan: SpellGaps) : SpellForm
    data class Tiles(val board: TileBoard) : SpellForm

    /**
     * How many slots the learner has to fill. The pool is longer than this on a
     * gap-fill (it carries distractors) and exactly this long on a tile board,
     * which is why "is the board full" has to ask the form and not the pool.
     */
    val slotCount: Int
        get() = when (this) {
            is Gaps -> plan.gaps.size
            is Tiles -> board.unitCount
        }

    companion object {
        /**
         * null when the word carries no 拼字 stage at all — a single-unit
         * subject, which is the rule the ladder has always gated on.
         */
        fun of(item: StudyQueueItem): SpellForm? {
            val subject = TileBoard.spellSubject(item)
            if (subject is SpellSubject.Term && isLatinScript(subject.text)) {
                SpellGaps.of(subject.text)?.let { return Gaps(it) }
            }
            val board = TileBoard.of(item)
            return if (board.unitCount >= 2) Tiles(board) else null
        }

        /**
         * ASCII letters plus the separators a headword may carry ("air
         * conditioner", "T-shirt", "children's"). Anything else — kana, kanji,
         * accented Latin — belongs on the tile board.
         */
        private fun isLatinScript(term: String): Boolean {
            val first = term.firstOrNull() ?: return false
            if (first.code > 127 || !first.isLetter()) return false
            return term.all { it.code <= 127 && (it.isLetter() || it == ' ' || it == '-' || it == '\'') }
        }
    }
}
