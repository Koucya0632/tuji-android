package app.tuji.android.study

import android.util.Log
import app.tuji.android.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.ImageChoiceOption
import app.tuji.android.core.study.ImageChoicePair
import app.tuji.android.core.study.ListeningQuestion
import app.tuji.android.core.study.PendingWrite
import app.tuji.android.core.study.ReviewFlash
import app.tuji.android.core.study.ReviewOutcome
import app.tuji.android.core.study.ReviewRevealMode
import app.tuji.android.core.study.ReviewSession
import app.tuji.android.core.model.ClipPlayback
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.study.StudyWriteOutcome
import app.tuji.android.core.study.studyChoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The performing half. [ReviewSession] decides; this waits, writes, and tells
 * the screen.
 *
 * Everything with a latency in it lives here on purpose — the 600 ms before the
 * sheet, the 700 ms flash, the POST — because that is exactly what makes the
 * decision half testable without a clock.
 */
class ReviewViewModel(
    private val queues: StudyQueueReading,
    private val writer: DurableAnswerWriter,
    private val direction: LearningDirection,
    private val uiLang: String,
    /** The catalogue, for topping up MCQ options on cards the server did not fill. */
    private val pool: () -> List<Word>,
    /**
     * Ask for a drain, because an answer was just parked.
     *
     * The worker's own doc said it was enqueued "at launch and whenever an
     * answer is parked" and only the first half was true — so a rating parked
     * mid-session sat on disk until the next cold start, which is the one
     * moment the user is least likely to be watching for it.
     */
    private val requestDrain: () -> Unit = {},
    /**
     * Plays 聽句's sentence. Defaults to a player that can do nothing, which
     * is not a stub but the honest answer for a build with no audio wired: it
     * reports [ClipPlaying.canPlay] false, and every card lands on 選字.
     */
    private val audio: ClipPlaying = SilentPlaying,
    /**
     * Whether the device has usable internet, asked per card rather than held.
     * Freezing it when the queue loaded would decide a whole session's
     * questions against the network as it was a minute ago.
     */
    private val online: () -> Boolean = { false },
    /**
     * The one-time lessons this screen has already taught. Defaults to an
     * in-memory one so a test never reaches for storage.
     */
    private val hints: StudyHints = InMemoryStudyHints(),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Failed(val message: String) : State
        data class Studying(
            val session: ReviewSession,
            val choices: List<String>,
            val revealMode: ReviewRevealMode?,
            val flash: ReviewFlash?,
            /** Answers that could not be sent and are waiting on disk. */
            val unsynced: Int,
            /** Whether this card's word has a recording, so the button can say so. */
            val canPlayWord: Boolean = false,
            /** Whether that recording is playing right now. */
            val playingWord: Boolean = false,
            /**
             * Whether the user has already been taught that the picture turns
             * over. Suppresses the nudge — a hint about a hint, offered forever,
             * is just a label.
             */
            val hintTaught: Boolean = false,
        ) : State

        data class Done(val session: ReviewSession, val unsynced: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var beat: Job? = null
    private var clipJob: Job? = null
    private var wordJob: Job? = null
    private var playingWord = false
    private var unsynced = 0

    /**
     * [mode] is a parameter because the endpoint has one. Hard-coding Review
     * here would have made the screen untestable against a brand-new account,
     * which is exactly the account that has nothing to review.
     */
    fun load(mode: StudyMode = StudyMode.Review, limit: Int = 20) {
        _state.value = State.Loading
        work.launch {
            val queue = runCatching {
                queues.queue(
                    mode = mode,
                    limit = limit,
                    new = if (mode == StudyMode.New) limit else 0,
                    categories = emptyList(),
                    lang = uiLang,
                    learning = direction,
                ).queue
            }.getOrElse {
                // The screen says one plain sentence; the reason goes here.
                // Showing the server's own words to a user is the mistake the
                // auth layer already made once — but throwing them away
                // entirely is the other half of it, and this had done that.
                Log.e(TAG, "study queue load failed", it)
                _state.value = State.Failed(it.message ?: "load failed")
                return@launch
            }
            val session = prepared(ReviewSession(queue, nowMs()))
            _state.value = if (session.finished) {
                State.Done(session, unsynced)
            } else {
                studying(session)
            }
            autoPlay()
        }
    }

    fun pick(label: String) {
        val now = current() ?: return
        if (now.revealMode != null || now.flash != null) return
        apply(now.session.pick(label, nowMs()))
    }

    fun rate(rating: SRSRating) {
        val now = current() ?: return
        if (now.revealMode != ReviewRevealMode.Rate) return
        val step = now.session.rate(rating)
        send(step.write)
        _state.value = studying(step.session, revealMode = null)
        // A fixed, network-independent beat so the button fill registers.
        scheduleAdvance(after = 300)
    }

    /** 下一題 on the retest-wrong sheet. */
    fun continueFromReveal() {
        val now = current() ?: return
        if (now.revealMode != ReviewRevealMode.ContinueOnly) return
        _state.value = studying(now.session, revealMode = null)
        scheduleAdvance(after = 0)
    }

    fun toggleHint() {
        val now = current() ?: return
        val q = now.session.question ?: return
        val flipped = q.toggleHint()
        if (flipped.hintFaceUp) hints.reviewHintTaught = true
        _state.value = studying(now.session.withQuestion(flipped), now.revealMode, now.flash)
    }

    /**
     * Drops the pending beat. Leaving during the pause must not raise a sheet
     * over the screen the user just left for, or finish a session they walked
     * out of.
     */
    fun leave() {
        beat?.cancel()
        beat = null
        stopAudio()
    }

    override fun onCleared() {
        leave()
        super.onCleared()
    }

    // Internals

    private fun current(): State.Studying? = _state.value as? State.Studying

    private fun apply(step: ReviewSession.Step) {
        send(step.write)
        when (val outcome = step.outcome) {
            is ReviewOutcome.Nothing -> _state.value = studying(step.session)
            is ReviewOutcome.RuledOut -> _state.value = studying(step.session)
            is ReviewOutcome.Flash -> {
                _state.value = studying(step.session, flash = outcome.flash)
                scheduleAdvance(after = 700)
            }
            is ReviewOutcome.Reveal -> {
                _state.value = studying(step.session)
                // The sheet used to go up in the same frame the options
                // resolved, so the block that says *what just happened* was on
                // screen for no time at all before a modal slid over it — the
                // user was asked to rate an answer they had not been shown.
                beat?.cancel()
                beat = work.launch {
                    delay(600)
                    current()?.let { _state.value = studying(it.session, revealMode = outcome.mode) }
                }
            }
        }
    }

    private fun scheduleAdvance(after: Long) {
        beat?.cancel()
        beat = work.launch {
            if (after > 0) delay(after)
            val now = current() ?: return@launch
            val next = now.session.advance(nowMs())
            _state.value = if (next.finished) {
                State.Done(next, unsynced)
            } else {
                studying(prepared(next))
            }
            autoPlay()
        }
    }

    private fun send(write: PendingWrite?) {
        val pending = write ?: return
        work.launch {
            if (writer.submitAnswer(pending.payload) is StudyWriteOutcome.Parked) {
                unsynced += 1
                requestDrain()
                (_state.value as? State.Studying)
                    ?.let { _state.value = it.copy(unsynced = unsynced) }
                (_state.value as? State.Done)
                    ?.let { _state.value = it.copy(unsynced = unsynced) }
            }
        }
    }

    private fun studying(
        session: ReviewSession,
        revealMode: ReviewRevealMode? = null,
        flash: ReviewFlash? = null,
    ): State.Studying {
        val item = session.question?.item
        val choices = item?.let {
            studyChoices(
                item = it,
                pool = pool(),
                session = direction.targetLanguage,
                variant = session.choicesVariant(it),
            )
        }.orEmpty()
        return State.Studying(
            session = session,
            choices = choices,
            revealMode = revealMode,
            flash = flash,
            unsynced = unsynced,
            canPlayWord = wordClip(session) != null,
            playingWord = playingWord,
            hintTaught = hints.reviewHintTaught,
        )
    }

    // The word, said aloud

    /**
     * Play the word on the current card.
     *
     * The same [ClipPlaying] seam 聽句 uses, and the same pre-generated
     * recordings — this is 圖鑑's pronunciation button on a study screen, not a
     * second way to make sound. It shares the one player, so a word cuts a
     * sentence off; the two never appear on the same card, and cutting is the
     * right answer if they ever do.
     */
    fun playWord() {
        val url = wordClip(current()?.session) ?: return
        wordJob?.cancel()
        wordJob = work.launch {
            setPlayingWord(true)
            audio.play(url)
            setPlayingWord(false)
        }
    }

    /**
     * Looked up in the catalogue, not read off the card.
     *
     * `/api/study/queue` is lean and carries no recordings — [StudyQueueWord]
     * has no `audioUrls` field at all, which is why reaching for one compiles
     * into a different `get` and not an error. The catalogue is the only place
     * a study card's clip lives, and a card the catalogue does not have (a
     * 自製圖鑑 `atlas:` card) simply has none, so the button is not drawn.
     */
    private fun wordClip(session: ReviewSession?): String? {
        val id = session?.question?.item?.word?.id ?: return null
        return pool().firstOrNull { it.id == id }?.audioUrls?.get(voice)
    }

    private fun setPlayingWord(value: Boolean) {
        playingWord = value
        val now = current() ?: return
        _state.value = now.copy(playingWord = value)
    }

    /**
     * The picture turned over, so the nudge has done its job — for good, not
     * for this card. Marked on the *flip*, not when the line appears: someone
     * who ignored the nudge has not learned anything yet.
     */

    // 聽句

    /** One of the two pictures. */
    fun pickImage(option: ImageChoiceOption) {
        val now = current() ?: return
        if (now.revealMode != null || now.flash != null) return
        apply(now.session.pickImage(option, nowMs()))
    }

    /**
     * 再聽一次 / 慢讀. The latter passes 0.8 and counts as a replay, because it
     * is one: reaching for it says the sentence did not land at speed.
     */
    fun replaySentence(rate: Float = 1f) {
        val now = current() ?: return
        val replayed = now.session.question?.willReplay() ?: return
        _state.value = studying(now.session.withQuestion(replayed), now.revealMode, now.flash)
        autoPlay(rate = rate, isReplay = true)
    }

    /** Lift the blur — from here this is a reading question, not a listening one. */
    fun revealSentence() {
        val now = current() ?: return
        val q = now.session.question ?: return
        _state.value = studying(
            now.session.withQuestion(q.revealSentence()), now.revealMode, now.flash,
        )
    }

    /** 這輪不做聽句題. Stops the clip too: it is answering a question that just left. */
    fun optOutOfListening() {
        val now = current() ?: return
        stopAudio()
        val next = now.session.optOutOfListening(nowMs())
        if (next === now.session) return
        _state.value = studying(next, now.revealMode, now.flash)
    }

    // Choosing the question

    /**
     * Decide what to ask about the current card.
     *
     * The catalogue and connectivity are read here, per card, rather than
     * frozen at construction — see [online].
     */
    private fun prepared(session: ReviewSession): ReviewSession {
        val item = session.current ?: return session.present(ReviewQuestionKind.PickWord)
        val presentation = session.choicesVariant(item)
        val example = ListeningQuestion.example(item, item.mastery, presentation)
        val clip = example?.audioUrls?.get(voice)

        var kind = ListeningQuestion.kind(
            wordId = item.word.id,
            canHear = !session.listeningOptedOut &&
                example != null &&
                audio.canPlay(clip, online()),
            previous = session.previousKind,
            alreadyHeard = item.word.id in session.heardWordIds,
        )

        // Two pictures or it is not this question. A pool that cannot produce a
        // fair distractor sends the card to 選字 — the same fallback every
        // other ineligible card takes.
        var options: List<ImageChoiceOption>? = null
        if (kind == ReviewQuestionKind.HearSentence) {
            options = ImageChoicePair.options(
                item = item,
                pool = pool(),
                session = direction.targetLanguage,
                mentionedWordIds = example?.mentionedWordIds.orEmpty().toSet(),
                queuedWordIds = session.upcomingWordIds,
                variant = presentation,
            )
            if (options == null) kind = ReviewQuestionKind.PickWord
        }

        // Kept, not deleted after it did its job. 聽句 declining to appear is
        // *silent* — every reason falls back to 選字, which is also what a
        // perfectly ordinary card does — and that is exactly how an unassigned
        // catalogue pool hid the whole question type from every card in a run.
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "listen? word=${item.word.id} slot=${ListeningQuestion.fallsOnSlot(item.word.id)} " +
                "example=${example != null} clip=${clip != null} online=${online()} " +
                "canPlay=${audio.canPlay(clip, online())} pool=${pool().size} " +
                "options=${options?.size} -> $kind",
        )

        // Ready *before* the audio: the card is fully drawn and answerable
        // while the sentence plays. Only the clock waits.
        return session.present(
            kind = kind,
            example = example,
            imageOptions = options,
            awaitsAudio = kind == ReviewQuestionKind.HearSentence,
        )
    }

    /**
     * Play the current card's sentence, if it has one.
     *
     * The card id is captured and re-checked on the way out: a clip that
     * finishes after the user has moved on must not start the *next* card's
     * clock, and cancelling the job is not enough on its own because the
     * completion can already be in flight.
     */
    private fun autoPlay(rate: Float = 1f, isReplay: Boolean = false) {
        val now = current() ?: return
        val q = now.session.question ?: return
        val began = q.playbackBegan() ?: return
        val clip = q.example?.audioUrls?.get(voice)
        val askedBy = q.item.card.id

        _state.value = studying(now.session.withQuestion(began), now.revealMode, now.flash)

        clipJob?.cancel()
        clipJob = work.launch {
            val outcome = audio.play(clip, rate)
            val settled = current() ?: return@launch
            val current = settled.session.question ?: return@launch
            if (current.item.card.id != askedBy) return@launch
            _state.value = studying(
                settled.session.withQuestion(
                    current.playbackEnded(
                        finished = outcome == ClipPlayback.Finished,
                        isReplay = isReplay,
                        nowMs = nowMs(),
                    ),
                ),
                settled.revealMode,
                settled.flash,
            )
        }
    }

    private fun stopAudio() {
        clipJob?.cancel()
        clipJob = null
        wordJob?.cancel()
        wordJob = null
        playingWord = false
        audio.stop()
    }

    /**
     * Which recording to ask for. English has two accents on the server and the
     * 發音口音 setting that picks between them has no Android home yet, so this
     * takes the same default iOS does when nothing is saved.
     */
    private val voice: String
        get() = if (direction.targetLanguage == TargetLanguage.JA) "ja-JP" else "en-US"

    private companion object {
        const val TAG = "TujiStudy"
    }
}

/**
 * A player that can play nothing.
 *
 * The default for [ReviewViewModel.audio], and not a stub: `canPlay` answering
 * false is the correct description of a build with no audio wired, and it sends
 * every card to 選字 rather than raising a listening question nobody can hear.
 */
object SilentPlaying : ClipPlaying {
    override fun canPlay(url: String?, online: Boolean): Boolean = false
    override suspend fun play(url: String?, rate: Float): ClipPlayback =
        ClipPlayback.Failed
    override fun stop() = Unit
}
