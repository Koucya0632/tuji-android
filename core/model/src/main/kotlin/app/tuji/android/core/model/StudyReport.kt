package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * What the card looked like when it was reported — iOS's
 * `StudyReportSnapshot`, key for key.
 *
 * Sent rather than looked up: the catalogue moves under a report, and the row
 * someone triages next week has to show the picture and the options the user
 * actually saw, not whatever the card says by then.
 */
@Serializable
data class StudyReportSnapshot(
    val word: String,
    val chinese: String,
    val imageUrl: String,
    val pronunciation: String,
    val category: String,
    val cardType: String? = null,
    val deckKey: String? = null,
    val choices: List<String> = emptyList(),
    val spellingChoices: List<String> = emptyList(),
    val displayedSpelling: String? = null,
)

/** `POST /api/study/reports` — iOS's `StudyReportPayload`. camelCase, as iOS encodes it. */
@Serializable
data class StudyReportPayload(
    /** One per opened form, so a retry after a timeout is the same report. */
    val requestId: String,
    val wordId: String,
    val cardId: String,
    val issueType: String,
    val description: String,
    /** `review` or `new`. */
    val mode: String,
    val phase: String,
    val selectedAnswer: String? = null,
    val platform: String,
    val appVersion: String,
    val uiLang: String,
    val snapshot: StudyReportSnapshot,
)
