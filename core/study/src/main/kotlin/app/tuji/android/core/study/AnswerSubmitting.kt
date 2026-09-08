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
    /** Throws on any failure. Retrying is the caller's decision, not this one's. */
    suspend fun submit(payload: StudyAnswerPayload): StudyAnswerResponse
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
