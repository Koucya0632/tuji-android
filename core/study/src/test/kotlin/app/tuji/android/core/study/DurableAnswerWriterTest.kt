package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DurableAnswerWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val account = ActiveAccount { "alice" }

    private fun outbox() = StudyAnswerOutbox(File(temp.root, "outbox.json"), account)

    private val payload = StudyAnswerPayload(cardId = "bath-mat", rating = SRSRating.Good)

    /** Fails the first [failures] attempts, then succeeds. */
    private class Flaky(private val failures: Int) : AnswerSubmitting {
        var attempts = 0
            private set

        override suspend fun submit(payload: StudyAnswerPayload): StudyAnswerResponse {
            attempts++
            if (attempts <= failures) throw java.io.IOException("boom")
            return StudyAnswerResponse(ok = true)
        }
    }

    private fun writer(submit: AnswerSubmitting, box: StudyAnswerOutbox) =
        // No real backoff: a test should not spend 1.2 seconds proving a sleep.
        DurableAnswerWriter(submit, box, backoff = {})

    @Test
    fun `a first-try success does not park anything`() = runTest {
        val box = outbox()
        val flaky = Flaky(failures = 0)

        val outcome = writer(flaky, box).submitAnswer(payload)

        assertTrue(outcome is StudyWriteOutcome.Synced)
        assertEquals(1, flaky.attempts)
        assertEquals(0, box.count)
    }

    @Test
    fun `it retries and then syncs`() = runTest {
        val box = outbox()
        val flaky = Flaky(failures = 2)

        val outcome = writer(flaky, box).submitAnswer(payload)

        assertTrue(outcome is StudyWriteOutcome.Synced)
        assertEquals(3, flaky.attempts)
        assertEquals(0, box.count)
    }

    @Test
    fun `it parks after exhausting its attempts`() = runTest {
        val box = outbox()
        val flaky = Flaky(failures = 99)

        val outcome = writer(flaky, box).submitAnswer(payload)

        assertEquals(StudyWriteOutcome.Parked, outcome)
        assertEquals(3, flaky.attempts)
        assertEquals(listOf("bath-mat"), box.pending.map { it.cardId })
    }

    @Test
    fun `the mastery delta survives the write`() = runTest {
        // The reason the outcome carries a response at all: the iOS version
        // that returned Void dropped this, and the completion screen's per-word
        // 變化 rows had to re-hand-roll the whole retry loop to get it back.
        val box = outbox()
        val submit = AnswerSubmitting {
            StudyAnswerResponse(
                ok = true,
                mastery = app.tuji.android.core.model.MasteryDelta(before = 40, after = 55, delta = 15),
            )
        }

        val outcome = writer(submit, box).submitAnswer(payload)

        val synced = outcome as StudyWriteOutcome.Synced
        assertEquals(15, synced.mastery())
    }

    private fun StudyWriteOutcome.Synced.mastery() = response.mastery?.delta

    @Test
    fun `a parked answer is on disk before the call returns`() = runTest {
        // "Survives a kill" means the file, not the field. A process killed one
        // line after this returns must still have the answer.
        val f = File(temp.root, "outbox.json")
        val box = StudyAnswerOutbox(f, account)

        writer(Flaky(failures = 99), box).submitAnswer(payload)

        assertEquals(1, StudyAnswerOutbox(f, account).count)
    }
}
