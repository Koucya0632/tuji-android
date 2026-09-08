package app.tuji.android.core.study

/**
 * What one option is once the answer is out. Before the reveal every option is
 * [Idle]; a caller never builds these itself.
 *
 * The decision lives here and the colours live in `core:design`, because this
 * half is the part that can be wrong in a way a screenshot will not show.
 */
enum class StudyOptionState {
    Idle,

    /** Picked, and right. */
    Right,

    /** Picked, and wrong. */
    Wrong,

    /**
     * The answer, when something else was picked. Looks identical to [Right] —
     * the two are separate only so a screen reader can tell them apart.
     */
    Answer,

    /** Neither picked nor the answer, once the answer is out. */
    Dim,
    ;

    companion object {
        /**
         * The reveal decision both MCQ surfaces used to duplicate.
         *
         * [wrongPicks] is 複習's 看圖選字: options ruled out while the question
         * is still open. They keep the mark **through** the reveal too — one of
         * them is still the option the user got wrong, and letting it fall to
         * [Dim] beside the answer would erase the only trace of the attempt.
         * Empty for 學新字, whose first pick ends the question either way.
         */
        fun forOption(
            label: String,
            answer: String,
            picked: String?,
            revealed: Boolean,
            wrongPicks: Set<String> = emptySet(),
        ): StudyOptionState {
            if (label in wrongPicks) return Wrong
            if (!revealed || picked == null) return Idle
            return verdict(isAnswer = label == answer, isPicked = label == picked)
        }

        /**
         * The same decision for 聽句's two pictures.
         *
         * Separate because the two surfaces identify an option differently, and
         * that difference is the whole point: 選字's options are labels, which
         * the distractor pool has already made unique, while a picture carries
         * the catalogue id — **two catalogue words can print the same string,
         * they cannot share an id.** iOS's picture card compared its picked
         * option by label, four lines under a doc comment saying why that is
         * wrong, in a private function with no test.
         *
         * There is no `wrongPicks` here: ruling out one of *two* pictures is
         * the same act as answering, so a picture is never marked while the
         * question is still open (ADR-0014).
         */
        fun forPicture(
            optionId: String,
            answerId: String,
            pickedId: String?,
            revealed: Boolean,
        ): StudyOptionState {
            if (!revealed || pickedId == null) return Idle
            return verdict(isAnswer = optionId == answerId, isPicked = optionId == pickedId)
        }

        private fun verdict(isAnswer: Boolean, isPicked: Boolean): StudyOptionState = when {
            isPicked && isAnswer -> Right
            isPicked -> Wrong
            isAnswer -> Answer
            else -> Dim
        }
    }
}
