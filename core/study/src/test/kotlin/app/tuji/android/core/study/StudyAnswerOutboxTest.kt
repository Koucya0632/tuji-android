package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The same cases the iOS suite pins, because a divergence here is a divergence
 * in whose answers reach the server — and this is the one part of the app where
 * a bug destroys data the user cannot get back.
 */
class StudyAnswerOutboxTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var activeUser: String? = "alice"
    private val account = ActiveAccount { activeUser }

    private fun file(): File = File(temp.root, "study-answer-outbox.json")

    private fun outbox(f: File = file()) = StudyAnswerOutbox(f, account)

    private fun answer(card: String) =
        StudyAnswerPayload(cardId = card, rating = SRSRating.Good)

    private class Recorder(private val fail: Boolean = false) : AnswerSubmitting {
        val sent = mutableListOf<StudyAnswerPayload>()
        override suspend fun submit(payload: StudyAnswerPayload): StudyAnswerResponse {
            sent += payload
            if (fail) throw java.io.IOException("offline")
            return StudyAnswerResponse(ok = true)
        }
    }

    @Test
    fun `parked answers survive a relaunch`() {
        val f = file()
        outbox(f).add(answer("bath-mat"))

        // A different instance over the same file is what "relaunch" means here.
        assertEquals(listOf("bath-mat"), outbox(f).pending.map { it.cardId })
    }

    @Test
    fun `a successful replay clears the outbox`() = runTest {
        val f = file()
        val box = outbox(f)
        box.add(answer("a"))
        box.add(answer("b"))

        val recorder = Recorder()
        box.replay(recorder)

        assertEquals(listOf("a", "b"), recorder.sent.map { it.cardId })
        assertEquals(0, box.count)
        // Cleared on disk too, or the next launch resends everything.
        assertEquals(0, outbox(f).count)
    }

    @Test
    fun `a replay stamps the owner so the server can reject a switched token`() = runTest {
        val box = outbox()
        box.add(answer("a"))

        val recorder = Recorder()
        box.replay(recorder)

        assertEquals("alice", recorder.sent.single().ownerUserId)
    }

    @Test
    fun `an offline replay holds everything`() = runTest {
        val f = file()
        val box = outbox(f)
        box.add(answer("a"))
        box.add(answer("b"))

        val recorder = Recorder(fail = true)
        box.replay(recorder)

        // Stopped after the first failure — the next entry meets the same
        // network — and kept both.
        assertEquals(1, recorder.sent.size)
        assertEquals(2, box.count)
        assertEquals(2, outbox(f).count)
    }

    @Test
    fun `answers never replay under another account`() = runTest {
        val box = outbox()
        box.add(answer("alice-answer"))

        activeUser = "bob"
        val recorder = Recorder()
        box.replay(recorder)

        assertTrue(recorder.sent.isEmpty())
        // Not visible to Bob either — the completion screen must not count
        // somebody else's pending answers.
        assertEquals(0, box.count)
        assertTrue(box.pending.isEmpty())
    }

    @Test
    fun `an account change mid-replay stops the pass`() = runTest {
        val box = outbox()
        box.add(answer("a"))
        box.add(answer("b"))

        val recorder = object : AnswerSubmitting {
            val sent = mutableListOf<StudyAnswerPayload>()
            override suspend fun submit(payload: StudyAnswerPayload): StudyAnswerResponse {
                sent += payload
                // Somebody signs out while the first POST is in flight.
                activeUser = "bob"
                return StudyAnswerResponse(ok = true)
            }
        }
        box.replay(recorder)

        assertEquals(1, recorder.sent.size)
        // The successful one must not be removed either: the check happens
        // before the removal, so Alice's remaining state is not edited by a
        // session that is no longer hers.
        activeUser = "alice"
        assertEquals(2, box.count)
    }

    @Test
    fun `an answer with no active account is refused rather than parked unowned`() {
        activeUser = null
        val box = outbox()
        box.add(answer("orphan"))

        activeUser = "alice"
        assertEquals(0, box.count)
    }

    @Test
    fun `reset clears and persists the account boundary`() {
        val f = file()
        val box = outbox(f)
        box.add(answer("a"))
        box.reset()

        assertEquals(0, box.count)
        assertEquals(0, outbox(f).count)
    }

    @Test
    fun `a legacy unowned file is quarantined, not replayed`() = runTest {
        val f = file()
        // What a pre-account-binding build wrote: a bare payload array.
        f.writeText("""[{"cardId":"legacy","rating":"穩定"}]""")

        val box = outbox(f)

        assertEquals(0, box.count)
        assertFalse("the original must not be left in place", f.exists())
        val quarantined = File(temp.root, "study-answer-outbox.json.unowned")
        assertTrue("it is still somebody's data", quarantined.exists())
        assertTrue(quarantined.readText().contains("legacy"))

        val recorder = Recorder()
        box.replay(recorder)
        assertTrue(recorder.sent.isEmpty())
    }

    @Test
    fun `an unreadable file is not a crash on launch`() {
        val f = file()
        f.writeText("{ this is not json")
        assertEquals(0, outbox(f).count)
    }

    @Test
    fun `a payload's optional fields round-trip through disk`() {
        // The reason nearly every field is nullable: these are read back by a
        // later build, and a new required key would fail to decode exactly the
        // answers the outbox exists to protect.
        val f = file()
        val rich = StudyAnswerPayload(
            cardId = "listen-1",
            rating = SRSRating.Hard,
            responseMs = 4200,
            sessionId = "s-1",
            activity = "listening",
            hinted = true,
            replayCount = 3,
            audioFailed = false,
            listeningOptedOut = true,
            convertedFromListening = true,
        )
        outbox(f).add(rich)

        val restored = outbox(f).pending.single()
        assertEquals(rich.copy(ownerUserId = null), restored.copy(ownerUserId = null))
    }
}
