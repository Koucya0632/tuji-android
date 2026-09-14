package app.tuji.android.study

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordSpeaking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The system text-to-speech engine, as [WordSpeaking].
 *
 * Started once for the process. Until the engine says it is ready, nothing can
 * be spoken and the button stays hidden — the same answer a word with no clip
 * got before this existed.
 */
class TtsSpeaker(context: Context) : WordSpeaking {

    @Volatile private var ready = false
    private val ids = AtomicInteger(0)
    private val waiting = ConcurrentHashMap<String, () -> Unit>()

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) Log.w(TAG, "text-to-speech unavailable: $status")
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) { waiting.remove(utteranceId)?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) { waiting.remove(utteranceId)?.invoke() }
            override fun onStop(utteranceId: String, interrupted: Boolean) { waiting.remove(utteranceId)?.invoke() }
        })
    }

    override fun canSpeak(language: TargetLanguage): Boolean =
        ready && engine.isLanguageAvailable(locale(language, "us")) >= TextToSpeech.LANG_AVAILABLE

    override suspend fun speak(text: String, language: TargetLanguage, accent: String) {
        if (!ready || text.isBlank()) return
        engine.language = locale(language, accent)
        val id = "tuji-${ids.incrementAndGet()}"
        suspendCancellableCoroutine { cont ->
            waiting[id] = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation {
                waiting.remove(id)
                engine.stop()
            }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                waiting.remove(id)
                cont.resume(Unit)
            }
        }
    }

    override fun stop() {
        engine.stop()
    }

    /** The saved 發音口音 decides which English; Japanese has one. */
    private fun locale(language: TargetLanguage, accent: String): Locale = when (language) {
        TargetLanguage.JA -> Locale.JAPAN
        TargetLanguage.EN -> if (accent == "uk") Locale.UK else Locale.US
    }

    private companion object {
        const val TAG = "TujiTts"
    }
}
