package app.tuji.android.core.network

import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse

/**
 * Submitting an answer, as a role.
 *
 * `core:study` declares the shape it needs (`AnswerSubmitting`) and this is the
 * witness. That direction is what keeps the durability rules on the pure-JVM
 * side of the build: the outbox is about *when to give up and what to keep*,
 * and none of that should need an HTTP client to be true.
 */
interface AnswerSubmission {
    suspend fun submitAnswer(payload: StudyAnswerPayload): StudyAnswerResponse
}

class StudyRepository(private val api: TujiApiClient) : AnswerSubmission {
    override suspend fun submitAnswer(payload: StudyAnswerPayload): StudyAnswerResponse =
        api.post(Endpoint.StudyAnswer, payload)
}
