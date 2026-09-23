package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyQueueItem

enum class NewTaskKind(val wire: String) {
    Recognize("recognize"),
    Identify("identify"),

    /**
     * 拼字. The wire value stays `spell_tiles` because it travels to the server
     * as a 報錯 snapshot's `phase`; the constant is no longer named after tiles
     * because most words no longer get them — see [SpellForm].
     */
    Spell("spell_tiles"),
}

data class NewStudyTask(val item: StudyQueueItem, val kind: NewTaskKind) {
    val id: String get() = "${item.id}#${kind.wire}"
}

/** One of the three dots over a 學新字 card: which stage, and where the word stands on it. */
data class NewStageStep(val kind: NewTaskKind, val state: State) {
    enum class State {
        Pending,
        Active,
        Done,

        /** 選字 dropped by the 已認識 fast path — a dimmed check, not a hole. */
        Skipped,
    }
}

/** One step harsher — used when quiz mistakes contradict the self-rating. */
val SRSRating.downgraded: SRSRating
    get() = when (this) {
        SRSRating.Easy -> SRSRating.Good
        SRSRating.Good -> SRSRating.Hard
        SRSRating.Hard, SRSRating.Again -> SRSRating.Again
    }

/**
 * The 學新字 task queue, as list algebra.
 *
 * A session is ONE interleaved queue, not three blocked phases: each word walks
 * 認識 → 選字 → 拼字 with other words' tasks in between, so the quiz retrieves
 * from (short) memory instead of echoing the card just shown. This module owns
 * that queue and nothing else — no beats, no locks, no SRS writes, no latency
 * capture. It is a value type, so a test states a queue and reads an answer.
 *
 * iOS learned the shape the hard way: living inside `NewFlowCoordinator`
 * alongside six other responsibilities, the only way to exercise it was through
 * three internal methods that exist for the tests and that the app never calls.
 * **The interface is the test surface** — if the tests have to enter somewhere
 * the app does not, the module is the wrong shape.
 *
 * Immutable here where Swift mutates in place: every transition returns the
 * next ladder, which is also what lets a test read a whole session as a chain.
 */
data class StudyLadder private constructor(
    /** Pending tasks; `first` is on screen. Empty ⇒ the session is finished. */
    val tasks: List<NewStudyTask>,
    /** Words whose 選字 was answered correctly — gates their 拼字 task. */
    val identifyCleared: Set<String>,
    /**
     * Words whose 選字 was dropped by the 已認識 fast path (a subset of
     * [identifyCleared]) — drawn as a dimmed check rather than a hole.
     */
    val skippedIdentify: Set<String>,
    /** Words that cleared every stage they were scheduled. */
    val clearedWords: Int,
    /**
     * Completed stage count out of [totalStages]. Requeued retries do not
     * inflate the denominator, so the header bar only moves forward.
     */
    val stageClears: Int,
    /**
     * Stages actually scheduled: 3 per word, minus the 拼字 of single-unit
     * subjects (a 1-tile board is a free answer, so those words finish after
     * 選字). Shrinks again when the 已認識 fast path drops a 選字.
     */
    val totalStages: Int,
) {
    // Reading

    val current: NewStudyTask? get() = tasks.firstOrNull()
    val finished: Boolean get() = tasks.isEmpty()
    val progress: Double get() = if (totalStages > 0) stageClears.toDouble() / totalStages else 0.0

    /** Whether this word still has a 拼字 stage on its ladder. */
    fun hasSpellStage(item: StudyQueueItem): Boolean = SpellForm.of(item) != null

    /**
     * The word's own ladder, for the dots over its card — iOS's
     * `stagePlan(for:)`.
     *
     * The interleave hides that every word walks the same three stages; with
     * other words' tasks in between, "選字 again?" reads as a repeat unless the
     * card says where this word is. A word with a single-unit subject has no
     * 拼字, so it draws two dots.
     *
     * @param recognized whether this word's 認識 has been answered — held by
     *   the screen's model, not the ladder, because it is the rating waiting
     *   to be written.
     */
    fun stagePlan(item: StudyQueueItem, recognized: Boolean): List<NewStageStep> {
        val wordId = item.word.id
        val active = current?.takeIf { it.item.word.id == wordId }?.kind
        fun state(kind: NewTaskKind, done: Boolean) = when {
            active == kind -> NewStageStep.State.Active
            done -> NewStageStep.State.Done
            else -> NewStageStep.State.Pending
        }
        return buildList {
            add(NewStageStep(NewTaskKind.Recognize, state(NewTaskKind.Recognize, recognized)))
            add(
                NewStageStep(
                    NewTaskKind.Identify,
                    if (wordId in skippedIdentify) NewStageStep.State.Skipped
                    else state(NewTaskKind.Identify, wordId in identifyCleared),
                ),
            )
            if (hasSpellStage(item)) add(NewStageStep(NewTaskKind.Spell, state(NewTaskKind.Spell, done = false)))
        }
    }

    // Transitions

    /**
     * Pop the head after a completed stage.
     *
     * [Completion.finishedWord] is non-null **only when that word has no tasks
     * left**, which is the signal the caller needs to flush its held-back SRS
     * write. "No tasks left" rather than "拼字 done" because stage counts vary
     * per word, and a wrong answer keeps its task queued — so this never
     * reports early.
     */
    fun completeCurrent(): Completion {
        val task = tasks.firstOrNull() ?: return Completion(this, null)
        val rest = tasks.drop(1)
        val wordIsDone = rest.none { it.item.word.id == task.item.word.id }
        val next = copy(
            tasks = rest,
            stageClears = stageClears + 1,
            clearedWords = clearedWords + if (wordIsDone) 1 else 0,
        ).normalizeHead()
        return Completion(next, if (wordIsDone) task.item else null)
    }

    data class Completion(val ladder: StudyLadder, val finishedWord: StudyQueueItem?)

    /** Requeue the head a few positions back after a wrong answer. */
    fun requeueCurrent(): StudyLadder {
        if (tasks.isEmpty()) return this
        val head = tasks.first()
        val rest = tasks.drop(1).toMutableList()
        rest.add(minOf(REQUEUE_GAP, rest.size), head)
        return copy(tasks = rest).normalizeHead()
    }

    /** Record that this word's 選字 was answered correctly. */
    fun markIdentifyCleared(wordId: String): StudyLadder =
        copy(identifyCleared = identifyCleared + wordId)

    /**
     * The 已認識 fast path: drop the word's pending 選字 task.
     *
     * Marking it cleared is load-bearing — [normalizeHead] gates a head 拼字 on
     * [identifyCleared], so without the insert the word's tiles would be
     * deferred forever.
     */
    fun skipIdentify(item: StudyQueueItem): StudyLadder {
        val wordId = item.word.id
        val idx = tasks.indexOfFirst { it.kind == NewTaskKind.Identify && it.item.word.id == wordId }
        if (idx < 0) return this
        return copy(
            tasks = tasks.filterIndexed { i, _ -> i != idx },
            identifyCleared = identifyCleared + wordId,
            skippedIdentify = skippedIdentify + wordId,
            totalStages = totalStages - 1,
        ).normalizeHead()
    }

    /**
     * A requeued 選字 can end up *behind* its word's pre-scheduled 拼字 task;
     * spelling a word the user just failed to recognise breaks the stage
     * ladder, so push the 拼字 back behind the pending 選字. The loop guard
     * bounds the degenerate all-heads-blocked case.
     */
    private fun normalizeHead(): StudyLadder {
        var work = tasks.toMutableList()
        var moved = 0
        while (true) {
            val head = work.firstOrNull() ?: break
            if (head.kind != NewTaskKind.Spell) break
            if (head.item.word.id in identifyCleared) break
            if (moved > work.size) break
            work.removeAt(0)
            val idIdx = work.indexOfFirst {
                it.kind == NewTaskKind.Identify && it.item.word.id == head.item.word.id
            }
            if (idIdx >= 0) {
                work.add(minOf(idIdx + REQUEUE_GAP, work.size), head)
            } else {
                // No pending 選字 for this word (shouldn't happen) — tail it.
                work.add(head)
            }
            moved += 1
        }
        return copy(tasks = work)
    }

    companion object {
        /** How many tasks sit between a wrong answer and its retry. */
        const val REQUEUE_GAP = 3

        /**
         * Build a ladder for a session's queue.
         *
         * A factory rather than a constructor so the schedule is computed once
         * and so the same normalisation the transitions run also applies to the
         * freshly-built head — iOS calls `normalizeHead()` from its `init` for
         * the same reason.
         */
        operator fun invoke(queue: List<StudyQueueItem>): StudyLadder {
            val tasks = initialSchedule(queue)
            return StudyLadder(
                tasks = tasks,
                identifyCleared = emptySet(),
                skippedIdentify = emptySet(),
                clearedWords = 0,
                stageClears = 0,
                totalStages = tasks.size,
            ).normalizeHead()
        }

        /**
         * rec@3i, id@3i+4, spell@3i+8, stable-sorted by position. Guarantees
         * each word's stages stay ordered while neighbouring words interleave
         * between them. Words that can carry neither a gap-fill nor a two-tile
         * board skip 拼字 entirely — the one predicate lives in [SpellForm] so
         * the gate and the board agree.
         */
        private fun initialSchedule(queue: List<StudyQueueItem>): List<NewStudyTask> {
            data class Slot(val pos: Int, val order: Int, val task: NewStudyTask)
            val scheduled = mutableListOf<Slot>()
            fun add(pos: Int, task: NewStudyTask) {
                scheduled.add(Slot(pos, scheduled.size, task))
            }
            queue.forEachIndexed { i, item ->
                add(3 * i, NewStudyTask(item, NewTaskKind.Recognize))
                add(3 * i + 4, NewStudyTask(item, NewTaskKind.Identify))
                if (SpellForm.of(item) != null) {
                    add(3 * i + 8, NewStudyTask(item, NewTaskKind.Spell))
                }
            }
            return scheduled
                .sortedWith(compareBy({ it.pos }, { it.order }))
                .map { it.task }
        }
    }
}
