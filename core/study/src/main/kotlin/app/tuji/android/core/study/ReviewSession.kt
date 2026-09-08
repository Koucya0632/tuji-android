package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyQueueItem

/** Feedback shown while auto-advancing without the sheet. */
sealed interface ReviewFlash {
    data class AutoRated(val rating: SRSRating) : ReviewFlash
    data object RetestPassed : ReviewFlash
}

/** One SRS row the caller still has to send. */
data class PendingWrite(val payload: StudyAnswerPayload, val wordId: String)

/**
 * What a settled answer asks the screen to do. The session decides; the caller
 * performs — the haptic, the beat, the sheet, the network.
 */
sealed interface ReviewOutcome {
    /** Nothing landed (a guard refused, or the question is still open). */
    data object Nothing : ReviewOutcome

    /** 看圖選字 marked an option and the question stays open. */
    data object RuledOut : ReviewOutcome

    /** Show the capsule, then advance. */
    data class Flash(val flash: ReviewFlash) : ReviewOutcome

    /** Raise the sheet a beat after the options have shown their result. */
    data class Reveal(val mode: ReviewRevealMode) : ReviewOutcome
}

/**
 * One 複習 session: the queue, where it is, and what has happened to it.
 *
 * A value, like [ReviewQuestion] and [StudyLadder]. Everything with a latency
 * in it — the 600 ms before the sheet, the 700 ms flash, the haptics, the audio
 * and the actual POST — stays with the caller, so the whole answering path can
 * be walked synchronously in a test.
 *
 * The one thing this owns that a question cannot is **what outlives a card**:
 * the cursor, which words have already been given their one extra re-test, how
 * many times each has been presented, and the session's 聽句 opt-out.
 */
data class ReviewSession(
    val queue: List<StudyQueueItem>,
    /**
     * Distinct word count at the start — the stable progress denominator, so a
     * requeued re-test never pushes the bar backwards.
     */
    val originalCount: Int,
    val index: Int = 0,
    val question: ReviewQuestion? = null,
    /** One row per word on the completion screen, even when re-tested twice. */
    val answered: List<StudyQueueItem> = emptyList(),
    /**
     * Words already requeued once — this enforces "one extra re-test per word",
     * and is also the completion screen's 答錯過 marker.
     */
    val retriedIds: Set<String> = emptySet(),
    /** Distinct words fully done. Drives the progress bar. */
    val passedCount: Int = 0,
    /**
     * Times each word has been presented **and left**. Folds into the option
     * seed so a re-test reshuffles instead of letting "the answer was C" stand
     * in for the word, and picks 聽句's sentence so a re-test hears the *other*
     * one rather than the recording it just failed.
     */
    val presentedCounts: Map<String, Int> = emptyMap(),
    /**
     * The user asked for no more listening questions this session.
     *
     * An "I cannot hear right now" escape, not a "this is too hard" one — 聽句
     * is the only question in the app that cannot be answered without audio,
     * and no headphones on a train is not a difficulty problem. That is also
     * why it carries no rating cost.
     *
     * Session-scoped on purpose, matching what the button says (這輪). 再來一輪
     * builds a fresh session, so the next round starts asking again rather than
     * silently inheriting a decision made about a different sitting.
     */
    val listeningOptedOut: Boolean = false,
    /** The kind the previous presentation used — "no two 聽句 in a row". */
    val previousKind: ReviewQuestionKind? = null,
    /**
     * Words already asked as 聽句 this session, so their re-test keeps the
     * question instead of being demoted by the spacing rule.
     */
    val heardWordIds: Set<String> = emptySet(),
    val finished: Boolean = false,
) {
    val current: StudyQueueItem? get() = question?.item

    /**
     * Distinct words completed, with a half-step while revealing so the bar
     * still feels responsive during the pause.
     */
    val progress: Double
        get() {
            if (originalCount <= 0) return 0.0
            val boost = if (question?.phase == ReviewPhase.Review) 0.5 else 0.0
            return minOf(1.0, (passedCount + boost) / originalCount)
        }

    /** Bumps each time the word leaves the screen, so a re-test reshuffles. */
    fun choicesVariant(item: StudyQueueItem): Int = presentedCounts[item.word.id] ?: 0

    /**
     * Word ids still to be asked this session, the current one included.
     *
     * An image distractor drawn from them would be a free look at a question
     * the user has not reached yet.
     */
    val upcomingWordIds: Set<String>
        get() = if (index >= queue.size) emptySet() else queue.drop(index).map { it.word.id }.toSet()

    /** A transition plus what the caller still has to do about it. */
    data class Step(
        val session: ReviewSession,
        val outcome: ReviewOutcome = ReviewOutcome.Nothing,
        /** Non-null when an SRS row is owed. */
        val write: PendingWrite? = null,
    )

    // Answering

    /** One of 選字's four labels. */
    fun pick(choice: String, nowMs: Long): Step {
        val q = question ?: return Step(this)
        val tapped = q.pick(choice, nowMs)
        return afterTap(tapped)
    }

    /** One of 聽句's two pictures. */
    fun pickImage(option: ImageChoiceOption, nowMs: Long): Step {
        val q = question ?: return Step(this)
        return afterTap(q.pickImage(option, nowMs))
    }

    private fun afterTap(tapped: ReviewQuestion.Tapped): Step {
        val next = copy(question = tapped.question)
        return when (val tap = tapped.tap) {
            is ReviewTap.Ignored -> Step(next)
            is ReviewTap.RuledOut -> Step(next, ReviewOutcome.RuledOut)
            is ReviewTap.Resolved -> next.settle(tap.resolution)
        }
    }

    private fun settle(resolution: ReviewResolution): Step {
        val q = question ?: return Step(this)
        var next = recordAnswered(q.item)
        return when (resolution) {
            is ReviewResolution.FlashRetestPassed -> {
                next = next.countSettledWord()
                Step(next, ReviewOutcome.Flash(ReviewFlash.RetestPassed))
            }
            is ReviewResolution.AutoRated -> {
                val rated = next.applyRating(resolution.rating)
                Step(
                    rated.session.countSettledWord(),
                    ReviewOutcome.Flash(ReviewFlash.AutoRated(resolution.rating)),
                    rated.write,
                )
            }
            is ReviewResolution.Reveal -> {
                // A retest is done either way — nothing rates it, so this is
                // the only moment it can be counted. Everything else waits for
                // the rating, because a wrong one puts the word back on the tail.
                Step(next.countSettledWord(), ReviewOutcome.Reveal(resolution.mode))
            }
        }
    }

    /**
     * A manual rating from the reveal sheet.
     *
     * **A wrong first attempt requeues the word once**, appended to the tail
     * for an in-session re-test. The re-test itself never requeues again — it
     * cannot reach here, because a wrong retest resolves to `ContinueOnly` and
     * this path is only open for `Rate`.
     */
    fun rate(rating: SRSRating): Step {
        val q = question ?: return Step(this)
        if (q.phase != ReviewPhase.Review || q.rated != null) return Step(this)

        var next = this
        if (!q.wasCorrect) {
            next = next.copy(
                retriedIds = retriedIds + q.item.word.id,
                queue = queue + q.item,
            )
        }
        val rated = next.applyRating(rating)
        return Step(rated.session.countSettledWord(), ReviewOutcome.Nothing, rated.write)
    }

    private fun applyRating(rating: SRSRating): Step {
        val q = question ?: return Step(this)
        val rated = q.applyRating(rating) ?: return Step(this)
        return Step(
            copy(question = rated),
            write = PendingWrite(
                payload = rated.payload(rating, listeningOptedOut),
                wordId = rated.item.word.id,
            ),
        )
    }

    /**
     * Move the bar on, if this presentation has finished with its word.
     *
     * On iOS this was three increments under three different conditions. The
     * condition is one — [ReviewQuestion.settled] — asked at the two moments a
     * presentation can end, and the `counted` flag is what keeps one rule from
     * becoming two counters.
     */
    private fun countSettledWord(): ReviewSession {
        val q = question ?: return this
        if (!q.settled || q.counted) return this
        return copy(question = q.markCounted(), passedCount = passedCount + 1)
    }

    private fun recordAnswered(item: StudyQueueItem): ReviewSession =
        if (answered.any { it.word.id == item.word.id }) this
        else copy(answered = answered + item)

    // Moving on

    /**
     * Leave the current card and present the next, or finish.
     *
     * [previousKind] is remembered across the rebuild: "no two 聽句 in a row" is
     * the one piece of per-item state whose whole job is to outlive its item.
     */
    fun advance(nowMs: Long): ReviewSession {
        var next = this
        question?.let { leaving ->
            val id = leaving.item.word.id
            next = next.copy(
                presentedCounts = presentedCounts + (id to (presentedCounts[id] ?: 0) + 1),
                previousKind = leaving.kind,
                heardWordIds = if (leaving.kind == ReviewQuestionKind.HearSentence) {
                    heardWordIds + id
                } else {
                    heardWordIds
                },
            )
        }
        if (index + 1 >= next.queue.size) return next.copy(finished = true)

        val nextIndex = index + 1
        val item = next.queue[nextIndex]
        return next.copy(
            index = nextIndex,
            question = ReviewQuestion(
                item = item,
                // The whole per-item reset. A new value starts at its start
                // values, so there is no list of fields to keep in step with
                // the next question kind someone adds.
                isRetest = next.retriedIds.contains(item.word.id),
                startedAtMs = nowMs,
            ),
        )
    }

    /** 這輪不做聽句題, applied to the card in front of the user. */
    fun optOutOfListening(nowMs: Long): ReviewSession {
        val converted = question?.optOutOfListening(nowMs) ?: return this
        return copy(question = converted, listeningOptedOut = true)
    }

    /** Record what the current card turned out to be asking. */
    fun present(
        kind: ReviewQuestionKind,
        example: app.tuji.android.core.model.StudyExample? = null,
        imageOptions: List<ImageChoiceOption>? = null,
        awaitsAudio: Boolean = false,
    ): ReviewSession {
        val q = question ?: return this
        return copy(question = q.present(kind, example, imageOptions, awaitsAudio))
    }

    /** Replace the current question, for the controls that return a new one. */
    fun withQuestion(next: ReviewQuestion?): ReviewSession = copy(question = next)

    companion object {
        operator fun invoke(queue: List<StudyQueueItem>, nowMs: Long): ReviewSession {
            val first = queue.firstOrNull()
            return ReviewSession(
                queue = queue,
                originalCount = queue.size,
                question = first?.let {
                    ReviewQuestion(item = it, isRetest = false, startedAtMs = nowMs)
                },
                finished = queue.isEmpty(),
            )
        }
    }
}
