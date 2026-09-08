package app.tuji.android.core.study

import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse
import kotlinx.coroutines.delay

/**
 * The result of one durable answer write.
 *
 * Exhaustive so a caller cannot silently ignore a parked write — which is the
 * bug that made offline sessions look fully saved on iOS.
 */
sealed interface StudyWriteOutcome {
    /** Accepted; the response carries any mastery / milestone deltas. */
    data class Synced(val response: StudyAnswerResponse) : StudyWriteOutcome

    /** Every attempt failed; the payload is in the outbox and will replay. */
    data object Parked : StudyWriteOutcome
}

/**
 * One durable SRS answer write: a few bounded attempts, then park.
 *
 * **Never throws.** Parking *is* the terminal fallback, so every call resolves
 * to an outcome the caller has to handle. On iOS this consolidated a policy
 * that had lived in two places with two code bodies, one of which dropped the
 * mastery delta and therefore had to re-hand-roll the loop to get it back.
 */
class DurableAnswerWriter(
    private val submit: AnswerSubmitting,
    private val outbox: StudyAnswerOutbox,
    private val maxAttempts: Int = 3,
    /** Injectable so a test does not spend two seconds proving a backoff. */
    private val backoff: suspend (attempt: Int) -> Unit = { attempt ->
        delay(400L * (attempt + 1))
    },
) {
    suspend fun submitAnswer(payload: StudyAnswerPayload): StudyWriteOutcome {
        repeat(maxAttempts) { attempt ->
            val result = runCatching { submit.submit(payload) }
            result.getOrNull()?.let { return StudyWriteOutcome.Synced(it) }
            if (attempt < maxAttempts - 1) backoff(attempt)
        }
        // A dropped SRS write is user-visible damage — the word stays 未學 and
        // the daily goal miscounts — so it is kept rather than reported.
        outbox.add(payload)
        return StudyWriteOutcome.Parked
    }
}
