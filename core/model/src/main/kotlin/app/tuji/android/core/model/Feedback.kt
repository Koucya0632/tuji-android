package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/** 意見收集's four kinds — iOS's `FeedbackType`, in its order. */
enum class FeedbackType(val wire: String) {
    Feature("feature"),
    Bug("bug"),
    Content("content"),
    Other("other"),
}

/** `POST /api/users/feedback` — iOS's `FeedbackPayload`. */
@Serializable
data class FeedbackPayload(
    /** One per opened form; the server keeps it unique, so a retry is the same message. */
    val requestId: String,
    val feedbackType: String,
    val description: String,
    val platform: String,
    val appVersion: String,
    val uiLang: String,
)
