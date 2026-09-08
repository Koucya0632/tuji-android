package app.tuji.android.core.auth

/**
 * Where the account stands.
 *
 *   Checking  (app launch)
 *      └─ resolveSession() ──► SignedIn  if a persisted session exists
 *                          └─► SignedOut otherwise
 *   SignedOut
 *      ├─ enterGuest() ──► Guest
 *      └─ signIn()     ──► SignedIn
 *   Guest
 *      └─ exitGuest()  ──► SignedOut
 *   SignedIn
 *      └─ signOut()    ──► SignedOut
 */
sealed interface AuthState {
    /** App launch, before the persisted session has been resolved. */
    data object Checking : AuthState

    data object SignedOut : AuthState

    /** Browsing without an account. */
    data object Guest : AuthState

    data class SignedIn(val user: SessionUser) : AuthState
}

/** What a failed session refresh resolves to. */
enum class SessionRefreshFailure {
    /** There was never a session — a genuinely signed-out launch. */
    NoSession,

    /** A session is cached but could not be refreshed. Most likely offline. */
    Unreachable,
}

/**
 * Display-side view of the currently-authenticated user.
 *
 * Fields come from `raw_user_meta_data`, which is a **mirror** of `profiles`
 * maintained by the backend — not the authority. The session carries whatever
 * the token was minted with, so the mirror can lag a server-side change until
 * the token refreshes; `AuthService.hydrateProfile()` reconciles it against
 * `/api/users/me`, which reads `profiles` directly.
 */
data class SessionUser(
    val id: String,
    val email: String? = null,
    /**
     * The public UID (`TJ` + 8 digits). System-assigned and immutable — the
     * user cannot change it, so a stale value here is always the mirror
     * lagging, never a legitimate edit.
     */
    val username: String? = null,
    /** Editable display name. Falls back to [username] when null. */
    val nickname: String? = null,
    val avatar: String? = null,
) {
    /**
     * Applies server truth over the session mirror. Each field falls back to
     * what is already held, so a partial payload never blanks a good value.
     */
    fun merging(username: String?, nickname: String?, avatar: String?) = copy(
        username = username ?: this.username,
        nickname = nickname ?: this.nickname,
        avatar = avatar ?: this.avatar,
    )
}
