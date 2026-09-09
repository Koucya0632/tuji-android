package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * One row of `GET /api/users/mastery`: how well this account knows one word,
 * and when it comes back.
 *
 * Decay is applied **server-side at read**, so a score here is "as of the last
 * fetch" rather than a stored number the client could age on its own.
 */
@Serializable
data class MasteryEntry(
    val wordId: String,
    val mastery: Int,
    /**
     * ISO-8601, kept as a string.
     *
     * The server emits fractional seconds, and a strict `Instant.parse` on some
     * platforms rejects them — the whole map would fail to decode over a
     * decimal point. Parsed where it is used, tolerantly.
     */
    val nextReviewAt: String? = null,
)

@Serializable
data class MasteryListResponse(val items: List<MasteryEntry> = emptyList())
