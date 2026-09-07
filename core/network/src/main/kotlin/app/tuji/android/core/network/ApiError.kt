package app.tuji.android.core.network

/**
 * What can go wrong between a call site and a decoded response.
 *
 * The cases are the iOS ones, and they are distinct because the app reacts to
 * them differently: a transport failure is offline-banner territory, a decoding
 * failure is 資料解析失敗 and a bug, and an HTTP status is the server saying
 * something specific.
 */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No usable session. Distinct from a 401, which came back from the server. */
    data object NotAuthenticated : ApiError("No valid access token") {
        private fun readResolve(): Any = NotAuthenticated
    }

    /** Network-level: no route, TLS, timeout. */
    class Transport(cause: Throwable) : ApiError("Transport failure: ${cause.message}", cause)

    /** The response arrived and was not a success status. */
    class Http(val status: Int, val body: String?) :
        ApiError("HTTP $status" + (body?.take(200)?.let { ": $it" } ?: ""))

    /**
     * The body did not match the model.
     *
     * The commonest real cause in this project is not a typo: Postgres NUMERIC
     * columns serialize as JSON *strings* (`"0.9500"`), and a few atlas routes
     * return raw rows. A Double field decoding one of those lands here and
     * surfaces to the user as 資料解析失敗.
     */
    class Decoding(cause: Throwable) : ApiError("Decode failure: ${cause.message}", cause)
}
