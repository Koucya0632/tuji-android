package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating

/**
 * What 學新字 actually writes for a word, once it has cleared every stage.
 *
 * The user rates themselves at 認識 — before being asked to retrieve anything.
 * **That self-rating alone says nothing about whether they can find the word
 * again**, which is the only thing the SRS interval is about, so the quiz
 * stages get a vote: one wrong 選字/拼字 answer drops a level, two or more post
 * 重來 regardless of how confident the tap was.
 *
 * Held rather than written at 認識 for that reason — the write cannot be made
 * until the stages that contradict it have run. [StudyLadder.Completion]'s
 * `finishedWord` is the signal that they have.
 */
object LearnedRating {

    /**
     * @param selfRated what the user said at 認識.
     * @param mistakes wrong answers across this word's 選字 and 拼字 stages.
     */
    fun effective(selfRated: SRSRating, mistakes: Int): SRSRating = when {
        mistakes <= 0 -> selfRated
        mistakes == 1 -> selfRated.downgraded
        else -> SRSRating.Again
    }

    /** `study_logs.activity` for the one row a learned word writes. */
    const val ACTIVITY = "new_recognize"
}
