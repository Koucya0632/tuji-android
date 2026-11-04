package app.tuji.android.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.tuji.android.core.model.FuriganaSegment
import app.tuji.android.core.model.ShadowingScore
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The system recogniser, behind what [SpeechSession] needs from it, so the
 * session's timing and error handling can be tested without a device.
 */
interface SpeechEngine {
    fun isAvailable(): Boolean

    /** A recogniser that reports to [listener] until destroyed. */
    fun create(listener: Listener): Recognizer

    data class Request(val locale: String, val punctuate: Boolean, val offline: Boolean, val pauseMillis: Long)

    interface Listener {
        fun partial(text: String?)
        fun results(text: String?)
        /** A `SpeechRecognizer.ERROR_*` code. */
        fun error(code: Int)
    }

    interface Recognizer {
        /** Starts listening; asked again on the same recogniser, starts again. */
        fun start(request: Request)
        fun stop()
        fun cancel()
        fun destroy()
    }
}

/** [SpeechEngine] on Android's own `SpeechRecognizer`, without the system dialog. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    override fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun create(listener: SpeechEngine.Listener): SpeechEngine.Recognizer {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) = listener.results(text(results))
            override fun onPartialResults(results: Bundle?) = listener.partial(text(results))
            override fun onError(error: Int) = listener.error(error)
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        return object : SpeechEngine.Recognizer {
            override fun start(request: SpeechEngine.Request) = recognizer.startListening(intent(request))
            override fun stop() = recognizer.stopListening()
            override fun cancel() = recognizer.cancel()
            override fun destroy() = recognizer.destroy()
        }
    }

    private fun intent(request: SpeechEngine.Request) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, request.offline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (request.punctuate) putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            // The recogniser's own end-of-speech guess is shorter than ours; the timers decide.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, request.pauseMillis)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, request.pauseMillis)
        }

    private fun text(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}

/**
 * One listening session on the system speech recogniser, with iOS's timing:
 * it stops by itself after [pause] once something has been heard, after
 * [silence] if nothing ever was, and at [ceiling]. Shared by 跟讀 and 語音輸入.
 *
 * The recogniser is the device's own (on the device where it can be), so a try
 * costs nothing. Nothing is recorded or kept by the app. Must be used from the
 * main thread, as `SpeechRecognizer` requires.
 */
class SpeechSession(
    private val engine: SpeechEngine,
    private val scope: CoroutineScope,
    private val pause: Long,
    private val silence: Long,
    private val ceiling: Long,
) {
    /** [Offline]: the recogniser's server can't be reached, so a try now won't work but a later one may. */
    enum class Problem { Unavailable, Offline, Denied, Failed }

    interface Events {
        /** The whole transcript so far, each time it grows. */
        fun heard(text: String)
        /** The session is over; [text] is the last thing heard ("" for nothing). */
        fun finished(text: String)
        fun problem(problem: Problem)
    }

    private var recognizer: SpeechEngine.Recognizer? = null
    private var timer: Job? = null
    private var limit: Job? = null
    private var lastHeard = ""
    /** Bumped on every start and cancel so a late callback from an earlier try is ignored. */
    private var attempt = 0

    /**
     * On the device first, as iOS does. A language whose on-device pack is not
     * downloaded (error 13) is tried once more on the recogniser's server.
     */
    fun start(locale: String, events: Events, punctuate: Boolean = false) {
        cancel()
        var offline = true
        val mine = ++attempt
        if (!engine.isAvailable()) {
            events.problem(Problem.Unavailable)
            return
        }
        lastHeard = ""
        lateinit var recognizer: SpeechEngine.Recognizer
        recognizer = engine.create(object : SpeechEngine.Listener {
            override fun results(text: String?) {
                if (mine != attempt) return
                text?.takeIf { it.isNotEmpty() }?.let { lastHeard = it; events.heard(it) }
                end(events)
            }

            override fun partial(text: String?) {
                if (mine != attempt) return
                val heard = text?.takeIf { it.isNotEmpty() } ?: return
                lastHeard = heard
                events.heard(heard)
                arm(pause, mine)
            }

            override fun error(code: Int) {
                if (mine != attempt) return
                android.util.Log.w("SpeechSession", "recogniser error $code ($locale, offline=$offline)")
                when (code) {
                    // The recogniser reports silence as an error: nothing heard is not a problem to show.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> end(events)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(events, Problem.Denied)
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        if (offline && lastHeard.isEmpty()) {
                            // Same recogniser, asked again: a new one made inside this callback loses its service.
                            offline = false
                            recognizer.start(request(locale, punctuate, offline))
                        } else {
                            fail(events, Problem.Unavailable)
                        }
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> fail(events, Problem.Unavailable)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        if (lastHeard.isNotEmpty()) end(events) else fail(events, Problem.Offline)
                    // Something was heard before it went wrong: what was heard stands.
                    else -> if (lastHeard.isNotEmpty()) end(events) else fail(events, Problem.Failed)
                }
            }
        })
        this.recognizer = recognizer
        try {
            recognizer.start(request(locale, punctuate, offline))
        } catch (_: Throwable) {
            fail(events, Problem.Failed)
            return
        }
        arm(silence, mine)
        limit = scope.launch {
            delay(ceiling)
            if (mine == attempt) stop()
        }
    }

    /** The person is done: let the recogniser give its last word. */
    fun stop() {
        timer?.cancel()
        recognizer?.stop()
    }

    /** Drop it. Nothing more reaches the caller. */
    fun cancel() {
        attempt++
        timer?.cancel()
        limit?.cancel()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun end(events: Events) {
        val text = lastHeard
        release()
        events.finished(text)
    }

    private fun fail(events: Events, problem: Problem) {
        release()
        events.problem(problem)
    }

    private fun release() {
        attempt++
        timer?.cancel()
        limit?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun arm(delayMillis: Long, mine: Int) {
        timer?.cancel()
        timer = scope.launch {
            delay(delayMillis)
            if (mine == attempt) stop()
        }
    }

    private fun request(locale: String, punctuate: Boolean, offline: Boolean) =
        SpeechEngine.Request(locale, punctuate, offline, pause)

    companion object {
        fun locale(language: TargetLanguage): String = if (language == TargetLanguage.JA) "ja-JP" else "en-US"
    }
}

/**
 * 跟讀 — listens to the person read a line back and scores it. iOS's
 * `ShadowingRecorder`, without the playback of the try: Android's recogniser
 * does not hand the audio back, and iOS shows no 聽我說的 when it has none.
 *
 * One line at a time; starting another line drops the one in progress.
 */
class ShadowingRecorder(engine: SpeechEngine, scope: CoroutineScope) {
    constructor(context: Context, scope: CoroutineScope) : this(AndroidSpeechEngine(context), scope)

    sealed interface Phase {
        data object Idle : Phase
        data class Listening(val heard: String) : Phase
        data class Scored(val score: ShadowingScore, val heard: String) : Phase
        data object Denied : Phase
        data object Unavailable : Phase
        data object Offline : Phase
        data object Failed : Phase
    }

    data class State(val line: Int? = null, val phase: Phase = Phase.Idle) {
        fun isActive(line: Int): Boolean = this.line == line && phase is Phase.Listening
    }

    private val session = SpeechSession(engine, scope, pause = 2_500, silence = 6_000, ceiling = 20_000)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    fun start(line: Int, target: String, language: TargetLanguage, furigana: List<FuriganaSegment>?) {
        mutable.value = State(line, Phase.Listening(""))
        session.start(
            SpeechSession.locale(language),
            object : SpeechSession.Events {
                override fun heard(text: String) {
                    if (mutable.value.line == line) mutable.value = State(line, Phase.Listening(text))
                }

                override fun finished(text: String) {
                    mutable.value = State(line, Phase.Scored(ShadowingScore.of(target, text, language, furigana), text))
                }

                override fun problem(problem: SpeechSession.Problem) {
                    mutable.value = State(line, problem.phase())
                }
            },
        )
    }

    /** Asked for the microphone and refused. */
    fun denied(line: Int) {
        mutable.value = State(line, Phase.Denied)
    }

    fun stop() = session.stop()

    /** Drop everything, result included. Leaving the page, or moving on. */
    fun cancel() {
        session.cancel()
        mutable.value = State()
    }

    private fun SpeechSession.Problem.phase(): Phase = when (this) {
        SpeechSession.Problem.Denied -> Phase.Denied
        SpeechSession.Problem.Unavailable -> Phase.Unavailable
        SpeechSession.Problem.Offline -> Phase.Offline
        SpeechSession.Problem.Failed -> Phase.Failed
    }
}

/**
 * 語音輸入 — speech into a draft. iOS's `Dictation`: hands back the running
 * transcript while the person talks; what to do with it is the caller's.
 * Nothing is recorded or kept. Longer pauses than 跟讀's, because the person
 * is thinking of what to say, not reading.
 */
class Dictation(engine: SpeechEngine, scope: CoroutineScope) {
    constructor(context: Context, scope: CoroutineScope) : this(AndroidSpeechEngine(context), scope)

    enum class Phase { Idle, Listening, Denied, Unavailable, Offline, Failed }

    private val session = SpeechSession(engine, scope, pause = 3_000, silence = 8_000, ceiling = 60_000)
    private val mutable = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = mutable.asStateFlow()

    fun start(locale: String, heard: (String) -> Unit) {
        mutable.value = Phase.Listening
        session.start(
            locale,
            object : SpeechSession.Events {
                override fun heard(text: String) = heard(text)
                override fun finished(text: String) {
                    mutable.value = Phase.Idle
                }

                override fun problem(problem: SpeechSession.Problem) {
                    mutable.value = when (problem) {
                        SpeechSession.Problem.Denied -> Phase.Denied
                        SpeechSession.Problem.Unavailable -> Phase.Unavailable
                        SpeechSession.Problem.Offline -> Phase.Offline
                        SpeechSession.Problem.Failed -> Phase.Failed
                    }
                }
            },
            punctuate = true,
        )
    }

    fun denied() {
        mutable.value = Phase.Denied
    }

    fun stop() {
        session.stop()
    }

    /** Drop it. Nothing more reaches the caller, and a problem shown is cleared. */
    fun cancel() {
        session.cancel()
        mutable.value = Phase.Idle
    }

    companion object {
        /**
         * Chinese in the interface's own script, as iOS picks it: 简体 listens
         * for zh-CN, anything else for zh-TW. Taken from the language itself,
         * not from a locale's region, which a script-only tag would leave blank.
         */
        fun chineseLocale(ui: UiLanguage): String = if (ui == UiLanguage.ZhHans) "zh-CN" else "zh-TW"
    }
}
