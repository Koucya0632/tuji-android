package app.tuji.android.core.auth

/**
 * The auth state machine, minus Supabase.
 *
 * On iOS, `AuthService` is reached from 43 sites and had no characterisation at
 * all — a `private init` plus a Supabase client that crashes on missing config
 * made it unconstructible in a test process. So the rules below, every one of
 * them a fact a caller must know and none of them stated by a type, were
 * verified by nobody:
 *
 *  - [enterGuest] only works from [AuthState.SignedOut] and [exitGuest] only
 *    from [AuthState.Guest]. From anywhere else they are **silent no-ops** —
 *    no throw, no signal, nothing happens.
 *  - [cameFromGuest] is what stops the Welcome screen being an exit-less dead
 *    end for someone who tapped 登入 by accident. It is set by *leaving* guest
 *    mode and cleared by signing out.
 *  - **A failed session refresh does not mean signed out.** If a session is
 *    still cached and the error is anything other than "no session at all",
 *    the likely cause is a flat network, and bouncing an authenticated user to
 *    Welcome over a transient hiccup is worse than carrying a stale token to
 *    the next refresh. That is the app's whole offline-launch behaviour.
 *  - [applyNickname] / [applyProfile] are optimistic mirrors that apply only
 *    while signed in — a profile edit that lands after a sign-out must not
 *    resurrect the session.
 *
 * None of that needs a network client, so none of it lives behind one.
 * Immutable rather than Swift's `mutating`: every transition returns the next
 * session, which is also what lets a test write the whole machine as a table.
 */
data class AuthSession(
    val state: AuthState = AuthState.Checking,
    /**
     * True when Welcome was reached by *leaving* guest mode rather than at
     * first launch, so Welcome can offer a way back to browsing.
     */
    val cameFromGuest: Boolean = false,
) {
    val signedInUser: SessionUser?
        get() = (state as? AuthState.SignedIn)?.user

    // Launch

    fun restored(user: SessionUser) = copy(state = AuthState.SignedIn(user))

    /**
     * A refresh that threw. [SessionRefreshFailure.Unreachable] keeps the
     * cached session: see the offline rule above.
     */
    fun failedRefresh(failure: SessionRefreshFailure, cached: SessionUser?): AuthSession =
        if (failure == SessionRefreshFailure.Unreachable && cached != null) {
            copy(state = AuthState.SignedIn(cached))
        } else {
            copy(state = AuthState.SignedOut)
        }

    // Guest

    /** No-op unless signed out. Stated here because the type cannot say it. */
    fun enterGuest(): AuthSession =
        if (state is AuthState.SignedOut) copy(state = AuthState.Guest, cameFromGuest = false) else this

    /** No-op unless in guest mode. */
    fun exitGuest(): AuthSession =
        if (state is AuthState.Guest) copy(state = AuthState.SignedOut, cameFromGuest = true) else this

    // Sign in / out

    fun signedIn(user: SessionUser) = copy(state = AuthState.SignedIn(user))

    /**
     * The client reported "no session".
     *
     * Distinct from [signedOut], which is the user's own act. Guest is a
     * deliberate signed-out state, and the auth client emits "not
     * authenticated" continuously while in it — folding that straight into
     * [AuthState.SignedOut] would throw a guest back to Welcome for no reason,
     * repeatedly, with nothing on screen to explain it.
     */
    fun observedNoSession(): AuthSession =
        if (state is AuthState.Guest) this else copy(state = AuthState.SignedOut)

    fun signedOut() = copy(state = AuthState.SignedOut, cameFromGuest = false)

    // Profile mirror

    /**
     * Optimistic mirrors, applied only while signed in — an edit that lands
     * after a sign-out must not resurrect the session.
     */
    fun applyNickname(nickname: String?): AuthSession =
        when (val s = state) {
            is AuthState.SignedIn -> copy(state = AuthState.SignedIn(s.user.copy(nickname = nickname)))
            else -> this
        }

    fun applyProfile(nickname: String?, avatar: String?): AuthSession =
        when (val s = state) {
            is AuthState.SignedIn ->
                copy(state = AuthState.SignedIn(s.user.copy(nickname = nickname, avatar = avatar)))
            else -> this
        }

    /**
     * Publish a reconciled profile only if the session is still the same
     * account. The hydrate request runs off the launch-critical path, so the
     * user may have signed out or switched while it was in flight.
     */
    fun reconcile(user: SessionUser, ifStillSignedInAs: String): AuthSession =
        when (val s = state) {
            is AuthState.SignedIn ->
                if (s.user.id == ifStillSignedInAs) copy(state = AuthState.SignedIn(user)) else this
            else -> this
        }
}
