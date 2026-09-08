package app.tuji.android.study

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.study.DurableAnswerWriter
import app.tuji.android.core.study.PendingWrite
import app.tuji.android.core.study.ReviewFlash
import app.tuji.android.core.study.ReviewOutcome
import app.tuji.android.core.study.ReviewRevealMode
import app.tuji.android.core.study.ReviewSession
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
        ) : State

        data class Done(val session: ReviewSession, val unsynced: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var beat: Job? = null
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
            val session = ReviewSession(queue, nowMs())
                .present(app.tuji.android.core.model.ReviewQuestionKind.PickWord)
            _state.value = if (session.finished) {
                State.Done(session, unsynced)
            } else {
                studying(session)
            }
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
        _state.value = studying(now.session.withQuestion(q.toggleHint()), now.revealMode, now.flash)
    }

    /**
     * Drops the pending beat. Leaving during the pause must not raise a sheet
     * over the screen the user just left for, or finish a session they walked
     * out of.
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
                studying(next.present(app.tuji.android.core.model.ReviewQuestionKind.PickWord))
            }
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
        return State.Studying(session, choices, revealMode, flash, unsynced)
    }

    private companion object {
        const val TAG = "TujiStudy"
    }
}
