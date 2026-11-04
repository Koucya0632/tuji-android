package app.tuji.android.assistant

import android.speech.SpeechRecognizer
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.UiLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 語音輸入 and 跟讀's session with the system recogniser faked and coroutine
 * time moved by hand: what reaches the caller, which errors are a problem to
 * show, the one retry on the server, and when a try ends by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeechSessionTest {
    private class FakeEngine : SpeechEngine {
        var available = true
        var throwOnStart = false
        val recognizers = mutableListOf<FakeRecognizer>()
        val latest get() = recognizers.last()

        override fun isAvailable() = available

        override fun create(listener: SpeechEngine.Listener): SpeechEngine.Recognizer =
            FakeRecognizer(listener, this).also { recognizers += it }
    }

    private class FakeRecognizer(val listener: SpeechEngine.Listener, private val engine: FakeEngine) : SpeechEngine.Recognizer {
        val starts = mutableListOf<SpeechEngine.Request>()
        var stopped = 0
        var destroyed = false

        override fun start(request: SpeechEngine.Request) {
            if (engine.throwOnStart) throw IllegalStateException("no service")
            starts += request
        }

        override fun stop() { stopped++ }
        override fun cancel() = Unit
        override fun destroy() { destroyed = true }
    }

    private class Recorded : SpeechSession.Events {
        val heard = mutableListOf<String>()
        var finished: String? = null
        var problem: SpeechSession.Problem? = null
        override fun heard(text: String) { heard += text }
        override fun finished(text: String) { finished = text }
        override fun problem(problem: SpeechSession.Problem) { this.problem = problem }
    }

    private val engine = FakeEngine()

    private fun TestScope.session() = SpeechSession(engine, this, pause = 3_000, silence = 8_000, ceiling = 60_000)

    @Test fun `partials reach the caller and the results end it`() = runTest {
        val session = session()
        val events = Recorded()
        session.start("ja-JP", events, punctuate = true)
        val request = engine.latest.starts.single()
        assertEquals(SpeechEngine.Request("ja-JP", punctuate = true, offline = true, pauseMillis = 3_000), request)
        engine.latest.listener.partial("公園")
        engine.latest.listener.partial("公園に行きたい")
        engine.latest.listener.results("公園に行きたい。")
        assertEquals(listOf("公園", "公園に行きたい", "公園に行きたい。"), events.heard)
        assertEquals("公園に行きたい。", events.finished)
        assertTrue(engine.latest.destroyed)
        session.cancel()
    }

    @Test fun `empty results keep what was heard`() = runTest {
        val session = session()
        val events = Recorded()
        session.start("ja-JP", events)
        engine.latest.listener.partial("公園")
        engine.latest.listener.results(null)
        assertEquals("公園", events.finished)
    }

    @Test fun `silence reported as an error is nothing heard, not a problem`() = runTest {
        for (code in listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            val session = session()
            val events = Recorded()
            session.start("ja-JP", events)
            engine.latest.listener.error(code)
            assertEquals("", events.finished)
            assertEquals(null, events.problem)
        }
    }

    @Test fun `a language with no pack on the device is asked once more on the server`() = runTest {
        val session = session()
        val events = Recorded()
        session.start("ja-JP", events)
        val recognizer = engine.latest
        recognizer.listener.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(listOf(true, false), recognizer.starts.map { it.offline })
        assertEquals(1, engine.recognizers.size)
        assertEquals(null, events.problem)
        recognizer.listener.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(SpeechSession.Problem.Unavailable, events.problem)
    }

    @Test fun `a network error with nothing heard is offline, with something heard it ends`() = runTest {
        for (code in listOf(SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER)) {
            val session = session()
            val nothing = Recorded()
            session.start("ja-JP", nothing)
            engine.latest.listener.error(code)
            assertEquals(SpeechSession.Problem.Offline, nothing.problem)

            val something = Recorded()
            session.start("ja-JP", something)
            engine.latest.listener.partial("公園")
            engine.latest.listener.error(code)
            assertEquals(null, something.problem)
            assertEquals("公園", something.finished)
            session.cancel()
        }
    }

    @Test fun `other errors fail only when nothing was heard`() = runTest {
        val session = session()
        val nothing = Recorded()
        session.start("ja-JP", nothing)
        engine.latest.listener.error(SpeechRecognizer.ERROR_CLIENT)
        assertEquals(SpeechSession.Problem.Failed, nothing.problem)

        val denied = Recorded()
        session.start("ja-JP", denied)
        engine.latest.listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals(SpeechSession.Problem.Denied, denied.problem)

        val unsupported = Recorded()
        session.start("ja-JP", unsupported)
        engine.latest.listener.error(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        assertEquals(SpeechSession.Problem.Unavailable, unsupported.problem)
    }

    @Test fun `no recogniser on the device, or one that will not start`() = runTest {
        val session = session()
        engine.available = false
        val missing = Recorded()
        session.start("ja-JP", missing)
        assertEquals(SpeechSession.Problem.Unavailable, missing.problem)
        assertTrue(engine.recognizers.isEmpty())

        engine.available = true
        engine.throwOnStart = true
        val broken = Recorded()
        session.start("ja-JP", broken)
        assertEquals(SpeechSession.Problem.Failed, broken.problem)
    }

    @Test fun `a word from an earlier try is ignored`() = runTest {
        val session = session()
        val first = Recorded()
        session.start("ja-JP", first)
        val earlier = engine.latest.listener
        val second = Recorded()
        session.start("ja-JP", second)
        earlier.partial("遅い")
        earlier.results("遅い")
        earlier.error(SpeechRecognizer.ERROR_CLIENT)
        assertTrue(first.heard.isEmpty())
        assertEquals(null, first.finished)
        assertEquals(null, first.problem)
        session.cancel()
    }

    @Test fun `nothing heard stops after the silence`() = runTest {
        val session = session()
        session.start("ja-JP", Recorded())
        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(0, engine.latest.stopped)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, engine.latest.stopped)
        session.cancel()
    }

    @Test fun `a pause after a word stops it`() = runTest {
        val session = session()
        session.start("ja-JP", Recorded())
        engine.latest.listener.partial("公園")
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, engine.latest.stopped)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, engine.latest.stopped)
        session.cancel()
    }

    @Test fun `an earlier try's ceiling does not stop the next one`() = runTest {
        val session = session()
        session.start("ja-JP", Recorded())
        advanceTimeBy(5_000)
        session.start("ja-JP", Recorded())
        val current = engine.latest
        // Past where the first try's ceiling was (60 s), short of the new one's (65 s).
        repeat(28) {
            current.listener.partial("まだ")
            advanceTimeBy(2_000)
            runCurrent()
        }
        assertEquals(0, current.stopped)
        session.cancel()
    }

    @Test fun `跟讀 scores what was heard when the session ends`() = runTest {
        val recorder = ShadowingRecorder(engine, this)
        recorder.start(0, "Coffee, please.", TargetLanguage.EN, null)
        assertTrue(recorder.state.value.isActive(0))
        engine.latest.listener.partial("coffee")
        engine.latest.listener.results("coffee please")
        val phase = recorder.state.value.phase
        assertTrue(phase is ShadowingRecorder.Phase.Scored)
        assertEquals(1.0, (phase as ShadowingRecorder.Phase.Scored).score.ratio, 0.0)
        assertFalse(recorder.state.value.isActive(0))
    }

    @Test fun `語音輸入 shows offline and goes idle when done`() = runTest {
        val dictation = Dictation(engine, this)
        val heard = mutableListOf<String>()
        dictation.start("ja-JP") { heard += it }
        assertEquals(Dictation.Phase.Listening, dictation.phase.value)
        engine.latest.listener.partial("公園")
        engine.latest.listener.results("公園です")
        assertEquals(Dictation.Phase.Idle, dictation.phase.value)
        assertEquals(listOf("公園", "公園です"), heard)

        dictation.start("ja-JP") {}
        engine.latest.listener.error(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(Dictation.Phase.Offline, dictation.phase.value)
    }

    @Test fun `Chinese is listened for in the interface's script`() {
        assertEquals("zh-CN", Dictation.chineseLocale(UiLanguage.ZhHans))
        assertEquals("zh-TW", Dictation.chineseLocale(UiLanguage.ZhHant))
        assertEquals("zh-TW", Dictation.chineseLocale(UiLanguage.Ja))
        assertEquals("zh-TW", Dictation.chineseLocale(UiLanguage.En))
    }
}
