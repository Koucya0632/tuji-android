package app.tuji.android.core.study

import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.model.StudyQueueItem

/** Where a card is in its own life: being answered, or being looked back at. */
enum class ReviewPhase { Answer, Review }

/** What the reveal sheet is for. */
enum class ReviewRevealMode {
    /** Manual SRS rating buttons. */
    Rate,

    /** Retest wrong: study material and a single 下一題, with no write. */
    ContinueOnly,
}

/** What resolving an answer asks the session to do next. */
sealed interface ReviewResolution {
    /**
     * A correct re-test: flash and move on. No SRS write — the first attempt's
     * 重來 already rescheduled the word.
     */
    data object FlashRetestPassed : ReviewResolution

    /** Fast, correct, unambiguous: apply this rating without asking. */
    data class AutoRated(val rating: SRSRating) : ReviewResolution

    /** Put the question to the user. */
    data class Reveal(val mode: ReviewRevealMode) : ReviewResolution
}

/**
 * The option the user landed on.
 *
 * **A label is not an identity.** 選字's four options are labels — the
 * distractor pool guarantees no two print the same string — but 聽句's two
 * pictures carry catalogue ids, and two catalogue words *can* print the same
 * string. iOS said so in a doc comment and then passed the label on alone,
 * which is how the picture card came to draw its「你點了這個」frame by comparing
 * text.
 */
data class ReviewChoice(
    /** The catalogue word id, when the option had one. 選字's labels do not. */
    val id: String?,
    /** What the option said. What 報錯 quotes, and what the MCQ rows match on. */
    val label: String,
)

/** What one tap on an option did. */
sealed interface ReviewTap {
    /** Guard refused it — wrong phase, or an option already ruled out. */
    data object Ignored : ReviewTap

    /** 看圖選字: marked and taken out of play, question still open. */
    data object RuledOut : ReviewTap

    /** The pick that landed. */
    data class Resolved(val resolution: ReviewResolution) : ReviewTap
}

/**
 * One of the two pictures in a 聽句 question.
 *
 * Carries the id so a pick can be compared against the answer without
 * comparing labels — two catalogue words can share a label, they cannot share
 * an id.
 */
data class ImageChoiceOption(
    val id: String,
    val word: String,
    val imageUrl: String,
    val imageKind: WordImageKind = WordImageKind.Cutout,
)

/**
 * One presentation of one card: what it asks, what the user has done to it, and
 * what that adds up to.
 *
 * On iOS this was 32 stored properties on the coordinator, and `advance()` was
 * a 16-assignment reset of them — three of which any test had ever asserted.
 * Adding a question kind meant adding state and remembering to clear it in a
 * list nothing checked. So the reset became a construction: advancing builds
 * the *next* question and every field is back at its start value because the
 * value is new. There is no list to forget.
 *
 * **A value with no beats, locks, audio or writes in it.** The coordinator
 * keeps everything that outlives one card and everything with a latency in it:
 * this decides, the coordinator performs. That split is what lets the whole
 * answering path be exercised synchronously, with no clock to poll.
 *
 * Times are epoch milliseconds rather than a clock type, so a test states an
 * instant instead of controlling one.
 */
data class ReviewQuestion(
    val item: StudyQueueItem,
    /**
     * A re-test of a word missed earlier this session. Re-tests never write SRS
     * and never requeue again, which is why so many rules read it.
     */
    val isRetest: Boolean,

    // What is being asked

    /**
     * Decided when the card becomes current, not at session start — the network
     * can drop mid-session and take 聽句's eligibility with it (ADR-0014).
     */
    val kind: ReviewQuestionKind = ReviewQuestionKind.PickWord,
    val example: StudyExample? = null,
    val imageOptions: List<ImageChoiceOption>? = null,
    /**
     * Whether the question has been decided.
     *
     * The view must draw a skeleton until it has. [kind] defaults to PickWord,
     * and 選字's hero *is the answer's own picture* — so rendering the default
     * for the one frame before the decision lands would show the answer to a
     * question that turns out to be 聽句. Defaulting the other way does not work
     * either: a listening card has no sentence to draw yet. The honest third
     * state is "not decided".
     */
    val ready: Boolean = false,

    // Answering

    val phase: ReviewPhase = ReviewPhase.Answer,
    val picked: ReviewChoice? = null,
    /**
     * Options ruled out on this presentation. 看圖選字 marks a wrong pick and
     * leaves the question open instead of ending it, so the user finds the word
     * themselves. Only the pick that lands resolves, and it is graded as a miss.
     */
    val wrongPicks: Set<String> = emptySet(),
    /**
     * The user asked to see the gloss. Sticky — flipping back does not un-see
     * it — and it makes the item take the wrong-answer rating table either way
     * (ADR-0007).
     */
    val hinted: Boolean = false,
    /** Which face the card shows now. [hinted] only ever turns on; this flips. */
    val hintFaceUp: Boolean = false,
    val wasCorrect: Boolean = false,
    val suggested: SRSRating = SRSRating.Good,
    val rated: SRSRating? = null,

    // The clock

    /** 聽句 starts it when the audio *ends*; everything else at construction. */
    val startedAtMs: Long,
    /** The clock has not started. Until it does an answer cannot be timed. */
    val awaitingAudio: Boolean = false,
    /**
     * How long the answer took, measured **once**, when it landed.
     *
     * iOS spelled this twice and the two disagreed: the suggestion said nothing
     * was measured while the row written to SRS claimed a duration that
     * included the download, the clip, the reveal beat, and however long the
     * user spent choosing among three rating buttons.
     */
    val measuredElapsedMs: Long? = null,

    // 聽句

    /**
     * The eye was pressed and the sentence is legible. One-way within the
     * presentation, like [hinted] — which it also sets, because reading the
     * sentence is reading the answer.
     */
    val sentenceRevealed: Boolean = false,
    /**
     * Replays before answering. Deliberately does **not** reset the clock: with
     * the download and the clip length already excluded, replay time points the
     * right way — needing three listens *is* 困難.
     */
    val replayCount: Int = 0,
    /** The clip was missing (so this was synthesis), or nothing came out. */
    val audioFailed: Boolean = false,
    val isPlayingSentence: Boolean = false,
    /** This presentation is the one the user turned listening off on. */
    val convertedFromListening: Boolean = false,

    /**
     * Whether the progress bar has already counted this word. The count happens
     * at two different moments — immediately for a re-test or an auto-rated
     * answer, only after the rating otherwise — so the guard is what keeps one
     * rule from becoming two counters.
     */
    val counted: Boolean = false,
) {
    /** A transition that also has something to say about the tap. */
    data class Tapped(val question: ReviewQuestion, val tap: ReviewTap)

    // Presenting

    /**
     * Record what this card turned out to be asking.
     *
     * [awaitsAudio] is separate from a listening kind because the card is drawn
     * and answerable while the sentence plays — only the clock waits.
     *
     * **A sentence is what makes it a listening question**, so asking for one
     * without an example lands on 選字 — the same fallback every other
     * ineligible card takes. Keeping the invariant here is what lets everything
     * below read [kind] alone instead of [kind] plus a null check.
     */
    fun present(
        kind: ReviewQuestionKind,
        example: StudyExample? = null,
        imageOptions: List<ImageChoiceOption>? = null,
        awaitsAudio: Boolean = false,
    ): ReviewQuestion =
        if (kind == ReviewQuestionKind.HearSentence && example != null) {
            copy(
                kind = ReviewQuestionKind.HearSentence,
                example = example,
                imageOptions = imageOptions,
                awaitingAudio = awaitsAudio,
                ready = true,
            )
        } else {
            copy(
                kind = ReviewQuestionKind.PickWord,
                example = null,
                imageOptions = null,
                ready = true,
            )
        }

    // 聽句 controls

    /** A play has begun. Null when this question has no sentence to play. */
    fun playbackBegan(): ReviewQuestion? =
        if (kind != ReviewQuestionKind.HearSentence) null else copy(isPlayingSentence = true)

    /**
     * A play has ended. Only the first opens the clock — a replay must not
     * reset it, or the button becomes a way to buy time, and the time a replay
     * costs is exactly the signal that this word was hard.
     */
    fun playbackEnded(finished: Boolean, isReplay: Boolean, nowMs: Long): ReviewQuestion {
        var next = copy(
            isPlayingSentence = false,
            audioFailed = audioFailed || !finished,
        )
        if (!isReplay && awaitingAudio) {
            next = next.copy(awaitingAudio = false, startedAtMs = nowMs)
        }
        return next
    }

    /**
     * 慢讀 counts as a replay, because it is one: reaching for it says the
     * sentence did not land at speed. Null when there is nothing to replay.
     */
    fun willReplay(): ReviewQuestion? =
        if (kind != ReviewQuestionKind.HearSentence || example == null) null
        else copy(replayCount = replayCount + 1)

    /**
     * Lift the blur. Same cost as 求救提示's flip and for a stronger reason: the
     * sentence spells the answer out, so from here this is a reading question,
     * not a listening one (ADR-0014).
     */
    fun revealSentence(): ReviewQuestion =
        if (phase != ReviewPhase.Answer || kind != ReviewQuestionKind.HearSentence) this
        else copy(sentenceRevealed = true, hinted = true)

    /**
     * 這輪不做聽句題, applied to the card in front of the user.
     *
     * Someone presses this *because* they cannot answer the one they are
     * looking at, so leaving it up would be asking a question they just said
     * they cannot hear. The clock restarts, because a different question starts
     * now. Nothing is marked hinted: no answer was revealed.
     *
     * [replayCount] and [audioFailed] need no reset — the payload reads them
     * only for a listening kind, so they stop being sent the moment it changes.
     */
    fun optOutOfListening(nowMs: Long): ReviewQuestion? =
        if (phase != ReviewPhase.Answer || kind != ReviewQuestionKind.HearSentence) null
        else copy(
            convertedFromListening = true,
            kind = ReviewQuestionKind.PickWord,
            example = null,
            imageOptions = null,
            sentenceRevealed = false,
            isPlayingSentence = false,
            awaitingAudio = false,
            startedAtMs = nowMs,
        )

    // 求救提示

    /**
     * Flip the image over to read the gloss, and back. Only while the item is
     * still unanswered: the reveal sheet leaves the hero tappable underneath
     * it, so an answered item would otherwise still turn.
     */
    fun toggleHint(): ReviewQuestion {
        if (phase != ReviewPhase.Answer || kind != ReviewQuestionKind.PickWord) return this
        val up = !hintFaceUp
        return copy(hintFaceUp = up, hinted = hinted || up)
    }

    /**
     * Whether the 8-second 「點一下圖片」 nudge still has anything to teach.
     *
     * Never in 聽句: that delay compensates for an affordance drawn nowhere —
     * 選字's hint is a tap on a picture with nothing to say so — and 聽句's eye
     * is on screen from the first frame.
     */
    val canNudge: Boolean
        get() = phase == ReviewPhase.Answer && !hinted && !isRetest &&
            kind == ReviewQuestionKind.PickWord

    // Answering

    /**
     * One of the two pictures in 聽句. Compared by **id, not by label**: two
     * catalogue words can print the same string, they cannot share an id.
     */
    fun pickImage(option: ImageChoiceOption, nowMs: Long): Tapped {
        if (kind != ReviewQuestionKind.HearSentence) return Tapped(this, ReviewTap.Ignored)
        return resolve(
            choice = ReviewChoice(id = option.id, label = option.word),
            correct = option.id == item.word.id,
            nowMs = nowMs,
        )
    }

    /**
     * 看圖選字. A wrong option is marked and taken out of play while the
     * question stays open — the user keeps choosing until they find the word.
     *
     * What it hands [resolve] is **whether they got it first try**, not whether
     * this tap was right: everything downstream reads [wasCorrect] and none of
     * it counts taps, so the rating table, the requeue and the payload stay
     * exactly the wrong-answer path they have always been.
     *
     * 聽句 is not on this path. Ruling out one of *two* pictures is the same act
     * as answering, so [pickImage] resolves on the first tap.
     */
    fun pick(choice: String, nowMs: Long): Tapped {
        if (phase != ReviewPhase.Answer) return Tapped(this, ReviewTap.Ignored)
        val ok = choice == item.word.word
        if (!ok && kind == ReviewQuestionKind.PickWord) {
            // The row is disabled once it is in the set; this keeps a repeat
            // from being caught by the haptic alone.
            if (choice in wrongPicks) return Tapped(this, ReviewTap.Ignored)
            return Tapped(copy(wrongPicks = wrongPicks + choice), ReviewTap.RuledOut)
        }
        return resolve(
            choice = ReviewChoice(id = null, label = choice),
            correct = ok && wrongPicks.isEmpty(),
            nowMs = nowMs,
        )
    }

    private fun resolve(choice: ReviewChoice, correct: Boolean, nowMs: Long): Tapped {
        if (phase != ReviewPhase.Answer) return Tapped(this, ReviewTap.Ignored)
        // Answering before the sentence finished leaves nothing timed — the
        // clock had not started. It may be genuine (the word was recognised
        // mid-sentence) or a rush, and the two are indistinguishable, so the
        // suggestion falls back to correctness rather than claiming a speed
        // that was never measured.
        val elapsed = if (awaitingAudio) null else nowMs - startedAtMs
        val next = copy(
            measuredElapsedMs = elapsed,
            suggested = suggestion(correct, elapsed, item.mastery, hinted),
            picked = choice,
            wasCorrect = correct,
            phase = ReviewPhase.Review,
        )
        if (isRetest) {
            // Practice only, never a second SRS write.
            val res = if (correct) {
                ReviewResolution.FlashRetestPassed
            } else {
                ReviewResolution.Reveal(ReviewRevealMode.ContinueOnly)
            }
            return Tapped(next, ReviewTap.Resolved(res))
        }
        if (correct && next.suggested != SRSRating.Hard && kind == ReviewQuestionKind.PickWord) {
            // The suggestion is unambiguous — apply it and keep the session
            // moving instead of raising a sheet to confirm it.
            //
            // 聽句 is excluded by that very precondition, not by an exception:
            // its answer is one of *two* pictures, so a fast correct answer is
            // one coin flip and "unambiguous" is not true of it (ADR-0014).
            return Tapped(next, ReviewTap.Resolved(ReviewResolution.AutoRated(next.suggested)))
        }
        // Wrong, or correct-but-slow: the user's own judgment carries signal.
        return Tapped(next, ReviewTap.Resolved(ReviewResolution.Reveal(ReviewRevealMode.Rate)))
    }

    /** Record the rating. Null when one has already landed. */
    fun applyRating(rating: SRSRating): ReviewQuestion? =
        if (rated != null) null else copy(rated = rating)

    /**
     * Rating buttons in the reveal sheet. Wrong answers offer only 重來/困難
     * (困難 = misclick escape hatch) — anything higher would let a missed word
     * skip its relearn.
     *
     * A hinted answer takes the wrong-answer table even when it was right: the
     * user told us they could not retrieve the word, so 穩定/熟練 are not theirs
     * to claim. Only the *suggestion* still tracks correctness.
     */
    val availableRatings: List<SRSRating>
        get() = if (!wasCorrect || hinted) {
            listOf(SRSRating.Again, SRSRating.Hard)
        } else {
            listOf(SRSRating.Hard, SRSRating.Good, SRSRating.Easy)
        }

    /**
     * What has been chosen so far, for 報錯: the pick that ended the question,
     * or the options ruled out while it is still open. [picked] is only set by
     * the pick that lands, so without this a report filed mid-question would
     * throw away everything the user had already tried.
     */
    val reportedSelection: String?
        get() = picked?.label ?: wrongPicks.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(" / ")

    /**
     * Whether nothing will ask this word again, so the progress bar may count
     * it.
     *
     * One rule for what used to be three increments under three different
     * conditions. A re-test settles the moment it resolves (it never requeues
     * and is never rated); anything else settles when a rating lands, and only
     * if it was right — a wrong one goes back on the tail.
     */
    val settled: Boolean
        get() = when {
            phase != ReviewPhase.Review -> false
            isRetest -> true
            rated == null -> false
            else -> wasCorrect
        }

    fun markCounted(): ReviewQuestion = copy(counted = true)

    /**
     * The SRS row for this presentation.
     *
     * [listeningOptedOut] is the session's, not the question's, so it arrives
     * as an argument: it rides on **every** activity, because a session that
     * turned 聽句 off answers the rest of its cards as 選字, and rows that
     * cannot be told apart from a session that never met a listening question
     * are what makes an aggregate accuracy lie.
     */
    fun payload(rating: SRSRating, listeningOptedOut: Boolean): StudyAnswerPayload {
        val listening = kind == ReviewQuestionKind.HearSentence
        return StudyAnswerPayload(
            cardId = item.card.id,
            rating = rating,
            responseMs = measuredElapsedMs?.toInt(),
            activity = kind.activity,
            hinted = hinted,
            // Only 聽句 has these, and sending them as null elsewhere keeps a
            // 選字 row's metadata honestly empty rather than claiming zero
            // replays of audio that was never played.
            replayCount = if (listening) replayCount else null,
            audioFailed = if (listening) audioFailed else null,
            listeningOptedOut = if (listeningOptedOut) true else null,
            convertedFromListening = if (convertedFromListening) true else null,
        )
    }

    companion object {
        /**
         * Computed once per answer. Fast correct answers auto-apply this; the
         * sheet highlights it as 建議 otherwise.
         *
         * **Mastery caps the top end**: a 2-second hit on a barely-known word
         * is normal recall, not 熟練 — only well-established words (≥ 50) earn
         * the long-interval jump.
         *
         * A hinted item is capped at 困難 regardless of speed. That cap is also
         * what switches off the auto-rate path, which requires a suggestion
         * other than 困難 (ADR-0007).
         *
         * [elapsedMs] is null when nothing was timed — 聽句 answered before its
         * sentence finished. A correct-but-untimed answer suggests 穩定: 熟練 is
         * the one rating that rests entirely on the speed signal, and claiming
         * it without one would be inventing the evidence.
         */
        fun suggestion(
            correct: Boolean,
            elapsedMs: Long?,
            mastery: Int?,
            hinted: Boolean = false,
        ): SRSRating = when {
            !correct -> SRSRating.Again
            hinted -> SRSRating.Hard
            elapsedMs == null -> SRSRating.Good
            elapsedMs < 3_000 -> if ((mastery ?: 0) >= 50) SRSRating.Easy else SRSRating.Good
            elapsedMs < 7_000 -> SRSRating.Good
            else -> SRSRating.Hard
        }
    }
}
