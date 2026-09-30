package app.tuji.android.core.study

import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse

/**
 * The one thing durability needs from the network, and nothing else.
 *
 * A narrow read seam rather than the whole study repository (ADR-0001). It is
 * also what keeps this module free of Android: the outbox and the writer are
 * rules about *when to give up and what to keep*, and none of that needs an
 * HTTP client to be true.
 */
fun interface AnswerSubmitting {
    /**
     * Throws on any failure — [AnswerRejected] when the server refused this
     * answer for good. Retrying is the caller's decision, not this one's.
     */
    suspend fun submit(payload: StudyAnswerPayload): StudyAnswerResponse
}

/**
 * The server refused this answer and always will: the card is gone (404), the
 * account may not write it (403), it needs a plan (402), the request itself is
 * refused (another 4xx). Such an answer is dropped, not retried or parked —
 * [StudyAnswerOutbox.replay] stops at the first failure, so one parked
 * permanent failure blocked every answer queued behind it, forever.
 *
 * Thrown by the network adapter, which is the side that knows what a status
 * is; this module only knows the verdict.
 */
class AnswerRejected(val status: Int, cause: Throwable? = null) :
    Exception("Answer rejected: HTTP $status", cause) {

    companion object {
        /**
         * Which HTTP statuses are permanent. 401 is not (the next attempt
         * carries a refreshed token), nor are 408 and 429, nor anything that is
         * not a 4xx. Unknown failures stay transient: dropping an answer is the
         * worse mistake of the two.
         */
        fun isPermanent(status: Int): Boolean =
            status in 400..499 && status != 401 && status != 408 && status != 429
    }
}

/**
 * Who the parked answers belong to, read at the moment of asking.
 *
 * Read live rather than captured, because the account can change while a replay
 * is in flight — which is the case that turns "resend a pending answer" into
 * "write one account's answer into another account's history".
 */
fun interface ActiveAccount {
    fun currentUserId(): String?
}
