package app.tuji.android.study

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.design.TujiHaptics
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Milestone
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.network.CatalogReading
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.LearnedRating
import app.tuji.android.core.study.NewStageStep
import app.tuji.android.core.study.NewTaskKind
import app.tuji.android.core.study.SpellForm
import app.tuji.android.core.study.SpellGaps
import app.tuji.android.core.study.SpellSubject
import app.tuji.android.core.study.SpokenVoice
import app.tuji.android.core.study.StudyLadder
import app.tuji.android.core.study.StudyQuotas
import app.tuji.android.core.study.StudyWriteOutcome
import app.tuji.android.core.study.TileBoard
import app.tuji.android.core.study.StudyChoiceSession
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
    /** Says the word. [SilentPlaying] draws no button, which is right for a build with no audio. */
    private val audio: ClipPlaying = SilentPlaying,
    /** The saved 發音口音. */
    private val accent: String = "us",
    private val online: () -> Boolean = { false },
    /**
     * Where 認識's example sentence comes from.
     *
     * The queue sends a sentence with no translation and no 詞塊 — deliberately,
     * on both platforms: carrying the annotation for a hundred cards to serve
     * the one the user opens is a hundred copies of it. So the detail is
     * fetched per word, in queue order, and a miss simply leaves the card as it
     * was. null means no teaching pass at all, which is what a test wants.
     */
    private val catalog: CatalogReading? = null,
    /**
     * What the phone says back. Fired from here rather than from the buttons
     * because the moment worth feeling is when an answer *resolves*, and no
     * composable can name that moment — the tap and the verdict are 450ms
     * apart, and on a miss the verdict is the whole point.
     */
    private val haptics: TujiHaptics = TujiHaptics.None,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private var choiceSession = StudyChoiceSession()

    sealed interface State {
        data object Loading : State
        data class Failed(val message: String) : State
        data class Studying(
            val ladder: StudyLadder,
            val stage: Stage,
            val unsynced: Int,
            /**
             * Words this session set out to teach. The ladder counts *stages*,
             * which requeue and so cannot answer "N of how many words" — the
             * denominator has to come from the queue that started it.
             */
            val total: Int,
            /** The current word's 認識 → 選字 → 拼字 dots. */
            val steps: List<NewStageStep> = emptyList(),
            /**
             * The current word's full entry, once its fetch lands. Absent is
             * the normal case for the first second of a session, and for every
             * 自製 card — [Stage.Recognize] falls back to the queue's own bare
             * sentence.
             */
            val teach: WordDetail? = null,
            /** Whether the word on screen has a recording that can play now. */
            val canPlayWord: Boolean = false,
            val playingWord: Boolean = false,
        ) : State

        /**
         * @param queue every word this session taught, in the order it taught
         *   them — what the celebration lists.
         * @param mistakes wrong 選字/拼字 answers per word id, so the recap can
         *   point at the ones to watch.
         */
        data class Done(
            val learned: Int,
            val unsynced: Int,
            val queue: List<StudyQueueItem> = emptyList(),
            val mistakes: Map<String, Int> = emptyMap(),
        ) : State
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

        /**
         * 拼字 — one stage, two boards.
         *
         * [SpellForm] decides which: English gets the gap-fill (the word with a
         * few confusable chunks cut out), a kana reading gets the from-scratch
         * tiles. One stage rather than two because the gesture is identical —
         * a shuffled pool, slots filled left to right, tap a filled slot to take
         * it back — and the server is told `spell_tiles` either way.
         */
        data class Spell(
            override val item: StudyQueueItem,
            val subject: SpellSubject,
            val form: SpellForm,
            /** What the learner taps: scrambled tiles, or shuffled chunks. */
            val pool: List<String>,
            /** Indices into [pool] in tap order — pick *i* fills slot *i*. */
            val picks: List<Int> = emptyList(),
            /** Null while still assembling. */
            val correct: Boolean? = null,
        ) : Stage {
            val board: TileBoard? get() = (form as? SpellForm.Tiles)?.board
            val plan: SpellGaps? get() = (form as? SpellForm.Gaps)?.plan

            /** The picks resolved to their strings, bounds-checked. */
            val chosen: List<String> get() = picks.mapNotNull { pool.getOrNull(it) }

            /**
             * Full when every *slot* is filled — which the form answers and the
             * pool cannot: a gap-fill's pool carries distractors that belong in
             * no slot at all.
             */
            val isFull: Boolean get() = picks.size == form.slotCount

            /**
             * Does this spell the word? The two forms ask different questions of
             * the same picks: tiles want the assembled string, a gap-fill wants
             * each chunk in its own slot. Joining a gap-fill's picks would
             * accept them in any order.
             */
            val matches: Boolean
                get() = when (form) {
                    is SpellForm.Tiles -> chosen.joinToString("") == form.board.target
                    is SpellForm.Gaps -> chosen == form.plan.answers
                }
        }
    }

    /** word id → its full entry, filled in as fetches land. */
    private val taught = mutableMapOf<String, WordDetail>()

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _milestone = MutableStateFlow<Milestone?>(null)

    /** A streak milestone an answer crossed; see `ReviewViewModel.milestone`. */
    val milestone: StateFlow<Milestone?> = _milestone.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var beat: Job? = null
    private var wordJob: Job? = null
    private var total = 0
    private var unsynced = 0

    /** This session's words, kept for the celebration that lists them. */
    private var sessionQueue: List<StudyQueueItem> = emptyList()

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

    /**
     * @param request what today's session asks for — see
     *   [StudyQuotas.newQueue]. The default is the setting's default with no
     *   theme filter, for callers that have neither to hand.
     */
    fun load(request: StudyQuotas.NewQueue = StudyQuotas.NewQueue(limit = 10, categories = emptyList())) {
        choiceSession = StudyChoiceSession()
        _state.value = State.Loading
        work.launch {
            val queue = runCatching {
                queues.queue(
                    mode = StudyMode.New,
                    limit = request.limit,
                    new = request.limit,
                    categories = request.categories,
                    lang = uiLang,
                    learning = direction,
                ).queue
            }.getOrElse {
                Log.e(TAG, "new-word queue load failed", it)
                _state.value = State.Failed(it.message ?: "load failed")
                return@launch
            }
            total = queue.size
            sessionQueue = queue
            show(StudyLadder(queue))
            preloadTeach(queue)
        }
    }

    // 認識

    fun rateRecognize(rating: SRSRating) {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Recognize ?: return
        if (stage.rated != null) return

        _state.value = now.copy(stage = stage.copy(rated = rating))
        haptics.success()
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
            // Now, not after a beat: iOS delays its warning by 800ms because
            // that is when the answer appears there. Here the answer is already
            // on screen in this frame, and a buzz arriving after it would be
            // reporting something the user has finished reading.
            haptics.warning()
            return  // waits for 下一題 — the answer stays on screen
        }
        beat?.cancel()
        beat = work.launch {
            delay(450)
            val settled = studying() ?: return@launch
            complete(settled.ladder.markIdentifyCleared(wordId))
            haptics.success()
        }
    }

    // 拼字

    fun pickSpell(index: Int) {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Spell ?: return
        if (stage.correct != null || index in stage.picks) return

        // Every tile that lands, not just the one that finishes the word: the
        // board is the one place in the app where the user is building
        // something a piece at a time.
        haptics.soft()
        val picks = stage.picks + index
        val next = stage.copy(picks = picks)
        if (!next.isFull) {
            _state.value = now.copy(stage = next)
            return
        }

        val correct = next.matches
        _state.value = now.copy(stage = next.copy(correct = correct))
        if (!correct) {
            mistakes[stage.item.word.id] = (mistakes[stage.item.word.id] ?: 0) + 1
            haptics.warning()
            return  // waits for 下一題
        }
        beat?.cancel()
        beat = work.launch {
            delay(600)
            val settled = studying() ?: return@launch
            complete(settled.ladder)
            haptics.success()
        }
    }

    /**
     * Tap a filled slot to take that entry back out. Only while still
     * assembling.
     *
     * Everything after it shifts left, which is iOS's `unpickSpell(atSlot:)` —
     * the picks *are* the slots, so removing one is a list removal and the
     * board re-reads itself.
     */
    fun unpickSpell(slot: Int) {
        val now = studying() ?: return
        val stage = now.stage as? Stage.Spell ?: return
        if (stage.correct != null || slot !in stage.picks.indices) return
        haptics.soft()
        _state.value = now.copy(stage = stage.copy(picks = stage.picks.filterIndexed { i, _ -> i != slot }))
    }

    /** 退一格 — the last one out. Android keeps this button; see `NewFlowScreen`. */
    fun undoSpell() {
        val stage = (studying()?.stage as? Stage.Spell) ?: return
        unpickSpell(stage.picks.lastIndex)
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
     * Say the word on screen. 認識 calls this as its card settles — hearing it
     * is the cheapest teach signal there is — and every stage has the button.
     */
    fun playWord() {
        val now = studying() ?: return
        val url = wordClip(now.stage.item) ?: return
        wordJob?.cancel()
        wordJob = work.launch {
            setPlayingWord(true)
            audio.play(url)
            setPlayingWord(false)
        }
    }

    /**
     * Drops the pending beat. Leaving during the pause must not advance a
     * session the user walked out of, or post an answer after the screen is
     * gone — nor keep saying a word over the screen the user went to.
     */
    fun leave() {
        beat?.cancel()
        beat = null
        wordJob?.cancel()
        audio.stop()
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
            _state.value = State.Done(
                learned = ladder.clearedWords,
                unsynced = unsynced,
                queue = sessionQueue,
                mistakes = mistakes.toMap(),
            )
            return
        }
        val item = task.item
        val stage = when (task.kind) {
            NewTaskKind.Recognize -> Stage.Recognize(item)
            NewTaskKind.Identify -> {
                identifyShownAt = item.word.id to nowMs()
                Stage.Identify(
                    item = item,
                    choices = choiceSession.choices(
                        item = item,
                        pool = pool(),
                        session = direction.targetLanguage,
                        variant = identifyAttempts[item.word.id] ?: 0,
                    ),
                )
            }
            NewTaskKind.Spell -> {
                val attempt = spellAttempts[item.word.id] ?: 0
                // The ladder scheduled this stage off the same predicate, so a
                // task that got here always has a form. The elvis is for the
                // compiler, not for a case that happens.
                val form = SpellForm.of(item) ?: SpellForm.Tiles(TileBoard.of(item))
                Stage.Spell(
                    item = item,
                    subject = TileBoard.spellSubject(item),
                    form = form,
                    pool = when (form) {
                        is SpellForm.Tiles -> TileBoard.scrambled(item, attempt)
                        is SpellForm.Gaps -> SpellGaps.options(item, attempt)
                    },
                )
            }
        }
        _state.value = State.Studying(
            ladder = ladder,
            stage = stage,
            unsynced = unsynced,
            total = total,
            steps = ladder.stagePlan(item, recognized = item.card.id in pendingRatings),
            teach = taught[item.word.id],
            canPlayWord = wordClip(item).let { it != null && audio.canPlay(it, online()) },
        )
    }

    /**
     * Fetches each word's full entry, in queue order, so the first card's
     * detail lands first and the later ones are long warmed by the time their
     * 認識 step surfaces.
     *
     * Never blocks and never spins: a card whose fetch has not landed — or
     * failed — shows exactly what it showed before this existed. 自製 cards are
     * skipped outright; the catalogue has never heard of them.
     */
    private fun preloadTeach(queue: List<StudyQueueItem>) {
        val catalog = catalog ?: return
        work.launch {
            for (item in queue) {
                val id = item.word.id
                if (id in taught || CardsSourceRules.isCustom(id)) continue
                val detail = runCatching { catalog.word(id, uiLang, direction) }.getOrNull() ?: continue
                taught[id] = detail
                // Patch the card in front of the user rather than rebuilding
                // it: `show()` would rebuild the stage as well, and a rebuilt
                // 拼字 board is a scramble the user was halfway through.
                val now = studying() ?: continue
                if (now.stage.item.word.id == id) _state.value = now.copy(teach = detail)
            }
        }
    }

    /**
     * From the catalogue, as 複習 finds it: `/api/study/queue` carries no
     * recordings, and a card the catalogue does not have (a 自製 `atlas:` card)
     * has none, so its button is not drawn.
     */
    private fun wordClip(item: StudyQueueItem): String? =
        pool().firstOrNull { it.id == item.word.id }?.audioUrls
            ?.let { SpokenVoice.clip(it, direction, accent, item.word.targetLanguage) }

    private fun setPlayingWord(value: Boolean) {
        val now = studying() ?: return
        _state.value = now.copy(playingWord = value)
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
            val outcome = writer.submitAnswer(payload)
            (outcome as? StudyWriteOutcome.Synced)?.response?.milestone?.let { _milestone.value = it }
            if (outcome is StudyWriteOutcome.Parked) {
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
