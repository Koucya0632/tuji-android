package app.tuji.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How the user rated a card.
 *
 * The wire values are **Chinese words**, because that is what the backend
 * stores and what `study_logs` is full of. They are not display copy: the UI
 * resolves its own localized label, so a `ja` or `en` user never sees these
 * strings even though every one of their answers carries them.
 */
@Serializable
enum class SRSRating(val wire: String) {
    @SerialName("重來")
    Again("重來"),

    @SerialName("困難")
    Hard("困難"),

    @SerialName("穩定")
    Good("穩定"),

    @SerialName("熟練")
    Easy("熟練"),
}

/**
 * One answer, as `POST /api/study/answer` takes it.
 *
 * Almost every field is optional, and that is load-bearing rather than sloppy:
 * these payloads get **written to disk** when they cannot be sent, and are read
 * back by a later build. A new non-optional key would fail to decode every
 * answer a previous version had parked — which is to say, it would delete
 * exactly the data the outbox exists to protect.
 */
@Serializable
data class StudyAnswerPayload(
    val cardId: String,
    val rating: SRSRating,
    val responseMs: Int? = null,
    val sessionId: String? = null,
    val activity: String? = null,
    /**
     * Present on durable replays so the server can reject the request if the
     * access token changed accounts while the replay was in flight.
     */
    val ownerUserId: String? = null,
    /**
     * The user turned the picture over before answering (複習's 求救提示).
     * What they read there varies; what this records is that they *asked*,
     * which is the part the rating cares about (ADR-0007).
     */
    val hinted: Boolean? = null,
    /** 聽句 only: how many times the sentence was replayed before answering. */
    val replayCount: Int? = null,
    /**
     * 聽句 only: the clip was missing and the sentence was read by on-device
     * synthesis instead. That answer is not evidence about listening in either
     * direction, so the analysis has to be able to drop it.
     */
    val audioFailed: Boolean? = null,
    /**
     * 這輪不做聽句題 was on when this was answered.
     *
     * Sent on **every** activity, not just 聽句 — that is the point. A session
     * with listening off answers the rest of its cards as 選字, and without this
     * flag those rows are indistinguishable from a session that never had a
     * listening question at all. That difference is what makes an aggregate
     * listening accuracy honest.
     */
    val listeningOptedOut: Boolean? = null,
    /** This card was a 聽句 question until the user turned listening off on it. */
    val convertedFromListening: Boolean? = null,
)

@Serializable
data class StudyAnswerResponse(
    val ok: Boolean? = null,
    val milestone: Milestone? = null,
    val mastery: MasteryDelta? = null,
)

/**
 * Word-level mastery before/after one answer (decayed `before`, blended
 * `after`). Drives the completion summary's per-word change rows.
 */
@Serializable
data class MasteryDelta(val before: Int, val after: Int, val delta: Int)

/** A streak milestone (30 / 100 / 365 days) the server attached to this answer. */
@Serializable
data class Milestone(val streak: Int)
