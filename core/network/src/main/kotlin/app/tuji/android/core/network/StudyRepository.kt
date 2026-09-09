package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.MasteryListResponse
import app.tuji.android.core.model.ProgressResponse
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyStatsResponse

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

/** Reading the day's counts, as a role. */
interface StudyStatsReading {
    suspend fun stats(learning: LearningDirection): StudyStatsResponse
}

/** Every word this account has a score for. */
interface MasteryReading {
    suspend fun mastery(learning: LearningDirection): MasteryListResponse
}

/** The streak, the heatmap and the per-theme rows. */
interface ProgressReading {
    suspend fun progress(learning: LearningDirection): ProgressResponse
}

/** Fetching a session's cards, as a role. */
interface StudyQueueReading {
    suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse
}

class StudyRepository(private val api: TujiApiClient) :
    AnswerSubmission, StudyQueueReading, StudyStatsReading, MasteryReading, ProgressReading {

    override suspend fun mastery(learning: LearningDirection): MasteryListResponse =
        api.get(Endpoint.UsersMastery(learning))

    override suspend fun progress(learning: LearningDirection): ProgressResponse =
        api.get(Endpoint.UsersProgress(learning))

    override suspend fun stats(learning: LearningDirection): StudyStatsResponse =
        api.get(Endpoint.StudyStats(learning = learning))

    override suspend fun submitAnswer(payload: StudyAnswerPayload): StudyAnswerResponse =
        api.post(Endpoint.StudyAnswer, payload)

    override suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse = api.get(
        Endpoint.StudyQueue(
            mode = mode,
            limit = limit,
            new = new,
            categories = categories,
            lang = lang,
            learning = learning,
        )
    )
}
