package app.tuji.android.study

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.LearnedRating
import app.tuji.android.core.study.NewTaskKind
import app.tuji.android.core.study.SpellSubject
import app.tuji.android.core.study.StudyLadder
import app.tuji.android.core.study.StudyWriteOutcome
import app.tuji.android.core.study.TileBoard
import app.tuji.android.core.study.studyChoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 學新字's performing half. [StudyLadder] decides the order; this holds the
 * things that outlive one task and everything with a latency in it.
 *
 * The three per-word counters here are the reason the SRS write is *held*
 * rather than posted at 認識: the self-rating happens before the user has been
 * asked to retrieve anything, and [LearnedRating] needs the quiz result to say
 * what it was worth. See [commitLearned].
 */
class NewFlowViewModel(
    private val queues: StudyQueueReading,
    private val writer: DurableAnswerWriter,
    private val direction: LearningDirection,
    private val uiLang: String,
    private val pool: () -> List<Word>,
    private val requestDrain: () -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Failed(val message: String) : State
        data class Studying(
            val ladder: StudyLadder,
            val stage: Stage,
            val unsynced: Int,
        ) : State

        data class Done(val learned: Int, val unsynced: Int) : State
    }

    /** What the card in front of the user is asking, and what they have done to it. */
    sealed interface Stage {
        val item: StudyQueueItem

        /** 這個字你認識嗎 — the teach card and three self-ratings. */
        data class Recognize(
            override val item: StudyQueueItem,
            /** Set for the beat between the tap and the next task. */
            val rated: SRSRating? = null,
        ) : Stage

        /** 選字 — the picture and four labels. */
        data class Identify(
            override val item: StudyQueueItem,
            val choices: List<String>,
            val picked: String? = null,
            /** The answer is on screen because the pick was wrong. */
            val revealed: Boolean = false,
        ) : Stage

        /** 拼字 — the scrambled tiles. */
        data class Spell(
            override val item: StudyQueueItem,
            val subject: SpellSubject,
            val board: TileBoard,
            val tiles: List<String>,
            /** Indices into [tiles], in the order they were tapped. */
            val picks: List<Int> = emptyList(),
            /** Null while still assembling. */
            val correct: Boolean? = null,
        ) : Stage {
            val assembled: String get() = picks.joinToString("") { tiles[it] }
            val isFull: Boolean get() = picks.size == tiles.size
        }
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var beat: Job? = null
    private var unsynced = 0

    /** Self-ratings waiting on the stages that get a vote. Keyed by card id. */
    private val pendingRatings = mutableMapOf<String, SRSRating>()

    /** Wrong 選字/拼字 answers per word — what [LearnedRating] folds in. */
    private val mistakes = mutableMapOf<String, Int>()

    /** Bumped per retry so the options and the scramble both move. */
    private val identifyAttempts = mutableMapOf<String, Int>()
    private val spellAttempts = mutableMapOf<String, Int>()

    /**
     * First-attempt 選字 latency only. A retry has already seen the answer, so
     * its speed says nothing about recall.
     */
    private val identifyResponseMs = mutableMapOf<String, Int>()
    private var identifyShownAt: Pair<String, Long>? = null

    fun load(limit: Int = 5) {
        _state.value = State.Loading
        work.launch {
            val queue = runCatching {
                queues.queue(
                    mode = StudyMode.New,
                    limit = limit,
                    new = limit,
                    categories = emptyList(),
                    lang = uiLang,
                    learning = direction,
                ).queue
            }.getOrElse {
                Log.e(TAG, "new-word queue load failed", it)
                _state.value = State.Failed(it.message ?: "load failed")
                return@launch
            }
            show(StudyLadder(queue))
        }
    }

    // 認識

    fun rateRecognize(rating: SRSRating) {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Recognize ?: return
        if (stage.rated != null) return

        _state.value = now.copy(stage = stage.copy(rated = rating))
        beat?.cancel()
        beat = work.launch {
            // A beat so the button fill registers before the card changes.
            delay(450)
            resolveRecognize(rating)
        }
    }

    /**
     * The synchronous core, split from the button handler so a test can walk
     * the ladder without real sleeps.
     */
    fun resolveRecognize(rating: SRSRating) {
        val now = studying() ?: return
        val task = now.ladder.current ?: return
        if (task.kind != NewTaskKind.Recognize) return

        // Held back: the write fires only once this word clears its last
        // stage, which is also what keeps 今日目標 counting completions rather
        // than bare 認識 taps.
        pendingRatings[task.item.card.id] = rating

        // 已認識 skips straight to production. The tiles still gate the commit
        // and a tile miss still downgrades the rating, so an overconfident
        // self-rating is corrected there rather than by an easy MCQ.
        val ladder = if (rating == SRSRating.Good) {
            now.ladder.skipIdentify(task.item)
        } else {
            now.ladder
        }
        complete(ladder)
    }

    // 選字

    fun pickIdentify(label: String) {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Identify ?: return
        if (stage.picked != null) return
        val wordId = stage.item.word.id

        identifyShownAt?.let { (shown, at) ->
            if (shown == wordId && wordId !in identifyResponseMs) {
                identifyResponseMs[wordId] = (nowMs() - at).toInt()
            }
        }

        val correct = label == stage.item.word.word
        _state.value = now.copy(stage = stage.copy(picked = label, revealed = !correct))
        if (!correct) {
            mistakes[wordId] = (mistakes[wordId] ?: 0) + 1
            return  // waits for 下一題 — the answer stays on screen
        }
        beat?.cancel()
        beat = work.launch {
            delay(450)
            val settled = studying() ?: return@launch
            complete(settled.ladder.markIdentifyCleared(wordId))
        }
    }

    // 拼字

    fun tapTile(index: Int) {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Spell ?: return
        if (stage.correct != null || index in stage.picks) return

        val picks = stage.picks + index
        val next = stage.copy(picks = picks)
        if (!next.isFull) {
            _state.value = now.copy(stage = next)
            return
        }

        val correct = next.assembled == stage.board.target
        _state.value = now.copy(stage = next.copy(correct = correct))
        if (!correct) {
            mistakes[stage.item.word.id] = (mistakes[stage.item.word.id] ?: 0) + 1
            return  // waits for 下一題
        }
        beat?.cancel()
        beat = work.launch {
            delay(600)
            val settled = studying() ?: return@launch
            complete(settled.ladder)
        }
    }

    /** Take the last tile back. Only while still assembling. */
    fun undoTile() {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Spell ?: return
        if (stage.correct != null || stage.picks.isEmpty()) return
        _state.value = now.copy(stage = stage.copy(picks = stage.picks.dropLast(1)))
    }

    /**
     * 下一題 on a wrong answer: requeue the missed task a few positions back and
     * bump its attempt, so the retry gets new options and a new scramble rather
     * than the layout the user just stared at.
     */
    fun continueFromWrong() {
        val now = studying() ?: return
        when (val stage = now.stage) {
            is Stage.Identify -> identifyAttempts.increment(stage.item.word.id)
            is Stage.Spell -> spellAttempts.increment(stage.item.word.id)
            is Stage.Recognize -> return
        }
        show(now.ladder.requeueCurrent())
    }

    /**
     * Drops the pending beat. Leaving during the pause must not advance a
     * session the user walked out of, or post an answer after the screen is
     * gone.
     */
    fun leave() {
        beat?.cancel()
        beat = null
    }

    override fun onCleared() {
        leave()
        super.onCleared()
    }

    // Internals

    private fun studying(): State.Studying? = _state.value as? State.Studying

    /** Pop the finished task, flush its write if the word is done, and draw the next. */
    private fun complete(ladder: StudyLadder) {
        val completion = ladder.completeCurrent()
        completion.finishedWord?.let(::commitLearned)
        show(completion.ladder)
    }

    private fun show(ladder: StudyLadder) {
        val task = ladder.current
        if (task == null) {
            _state.value = State.Done(ladder.clearedWords, unsynced)
            return
        }
        val item = task.item
        val stage = when (task.kind) {
            NewTaskKind.Recognize -> Stage.Recognize(item)
            NewTaskKind.Identify -> {
                identifyShownAt = item.word.id to nowMs()
                Stage.Identify(
                    item = item,
                    choices = studyChoices(
                        item = item,
                        pool = pool(),
                        session = direction.targetLanguage,
                        variant = identifyAttempts[item.word.id] ?: 0,
                    ),
                )
            }
            NewTaskKind.SpellTiles -> Stage.Spell(
                item = item,
                subject = TileBoard.spellSubject(item),
                board = TileBoard.of(item),
                tiles = TileBoard.scrambled(item, spellAttempts[item.word.id] ?: 0),
            )
        }
        _state.value = State.Studying(ladder, stage, unsynced)
    }

    /**
     * The one SRS row a learned word writes.
     *
     * Popped rather than read, so a word writes exactly once however many times
     * its stages were requeued.
     */
    private fun commitLearned(item: StudyQueueItem) {
        val rating = pendingRatings.remove(item.card.id) ?: return
        val payload = StudyAnswerPayload(
            cardId = item.card.id,
            rating = LearnedRating.effective(rating, mistakes[item.word.id] ?: 0),
            responseMs = identifyResponseMs[item.word.id],
            activity = LearnedRating.ACTIVITY,
        )
        work.launch {
            if (writer.submitAnswer(payload) is StudyWriteOutcome.Parked) {
                unsynced += 1
                requestDrain()
                (_state.value as? State.Studying)?.let { _state.value = it.copy(unsynced = unsynced) }
                (_state.value as? State.Done)?.let { _state.value = it.copy(unsynced = unsynced) }
            }
        }
    }

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private companion object {
        const val TAG = "TujiNew"
    }
}
