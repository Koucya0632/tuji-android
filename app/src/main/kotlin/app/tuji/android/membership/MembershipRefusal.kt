package app.tuji.android.membership

import app.tuji.android.core.network.ApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A write the server refused because the account's plan does not cover it —
 * HTTP 402. Buying something would allow it, so the answer is 會員方案, not an
 * error line. (A 429 is different: the top tier's ceiling, which only removing
 * something fixes.)
 */
object MembershipRefusal {

    fun isRefusal(error: Throwable?): Boolean = (error as? ApiError.Http)?.status == 402

    /**
     * The sentence the server wrote for a 402, or null. Never the body itself:
     * it is JSON, and a machine code on screen is worse than a generic line.
     */
    fun message(error: Throwable?): String? {
        val http = error as? ApiError.Http ?: return null
        if (http.status != 402) return null
        return runCatching {
            Json.parseToJsonElement(http.body ?: return null)
                .jsonObject["message"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
