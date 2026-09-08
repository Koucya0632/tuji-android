package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyQueueItem

enum class NewTaskKind(val wire: String) {
    Recognize("recognize"),
    Identify("identify"),
    SpellTiles("spell_tiles"),
}

data class NewStudyTask(val item: StudyQueueItem, val kind: NewTaskKind) {
    val id: String get() = "${item.id}#${kind.wire}"
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
     * subjects (a 1-tile board is a free answer). Shrinks again when the 已認識
     * fast path drops a 選字.
     */
    val totalStages: Int,
) {
    // Reading

    val current: NewStudyTask? get() = tasks.firstOrNull()
    val finished: Boolean get() = tasks.isEmpty()
    val progress: Double get() = if (totalStages > 0) stageClears.toDouble() / totalStages else 0.0

    /** Whether this word still has a 拼字 stage on its ladder. */
    fun hasSpellStage(item: StudyQueueItem): Boolean = TileBoard.of(item).unitCount >= 2

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
            if (head.kind != NewTaskKind.SpellTiles) break
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
         * between them. Words whose tile board has a single unit skip 拼字.
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
                if (TileBoard.of(item).unitCount >= 2) {
                    add(3 * i + 8, NewStudyTask(item, NewTaskKind.SpellTiles))
                }
            }
            return scheduled
                .sortedWith(compareBy({ it.pos }, { it.order }))
                .map { it.task }
        }
    }
}
