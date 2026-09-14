package app.tuji.android.core.network

/**
 * What the HTTP client needs from authentication, and nothing else.
 *
 * On iOS this seam is the whole reason the transport is testable: `APIClient`
 * used to take a concrete `AuthService` whose only instance reaches Supabase
 * and crashes without configuration, so a stub could reach the 11 public
 * endpoints and no others — 50 of 61 endpoints, the 401 retry and the multipart
 * upload all went through `validAccessToken()` first and could not be stood up.
 *
 * Three members. That is the entire slice the transport uses.
 */
interface AccessTokenProvider {
    /**
     * Throws when no usable session exists. Refreshes the session only when
     * *this device* believes the token is about to expire.
     */
    suspend fun validAccessToken(): String

    /**
     * A token to replace [rejected], which the server has just answered with a
     * 401 — so the device's belief about when it expires is not to be trusted.
     *
     * [validAccessToken] alone cannot serve the 401 retry. It refreshes by the
     * device's clock, and a clock that has moved since the token was issued
     * makes an expired token look good for hours: the retry re-sent the value
     * the server had just refused, and every signed-in request failed until the
     * device's idea of expiry caught up.
     *
     * Returns the current token without refreshing when it is already not
     * [rejected] — another request's retry got there first, and the refresh
     * token must not be spent twice. [rejected] is null when the refused
     * request went out without a token.
     */
    suspend fun refreshedAccessToken(rejected: String?): String

    /**
     * Whether to *attempt* a token on an optional-auth endpoint. Distinct from
     * "a token is available": an optional-auth request must stay usable for
     * signed-out guests, so this only decides whether to try.
     */
    val isSignedIn: Boolean
}

/** The signed-out case, spelled out so guest-only builds need no auth module. */
object NoAccessToken : AccessTokenProvider {
    override suspend fun validAccessToken(): String = throw ApiError.NotAuthenticated
    override suspend fun refreshedAccessToken(rejected: String?): String = throw ApiError.NotAuthenticated
    override val isSignedIn: Boolean = false
}
