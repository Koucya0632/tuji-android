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
 * Two methods. That is the entire slice the transport uses.
 */
interface AccessTokenProvider {
    /**
     * Throws when no usable session exists. Refreshing the session is a side
     * effect of asking, which is what makes the one-shot 401 retry work.
     */
    suspend fun validAccessToken(): String

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
    override val isSignedIn: Boolean = false
}
