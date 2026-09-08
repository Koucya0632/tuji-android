package app.tuji.android.core.auth

import android.content.Context
import android.util.Log
import app.tuji.android.core.network.AccessTokenProvider
import app.tuji.android.core.network.ApiError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime

/**
 * Supabase glue and the fan-outs. The transitions live in [AuthSession], which
 * a test can hold without a network client.
 *
 * The split is the whole point, and it is copied from what iOS had to learn:
 * there, `AuthService` is reached from 43 sites, cannot be constructed in a
 * test process, and every rule it enforces was documented in prose because
 * nothing could check it. Everything here is Supabase-shaped; everything that
 * is a *rule* is next door and covered.
 */
class AuthService(
    private val supabase: SupabaseClient,
    private val google: GoogleCredentialBridge,
    scope: CoroutineScope,
    /** Everything that must be forgotten when the account changes. */
    private val accountScopedStores: () -> List<AccountScopedStore> = { emptyList() },
) : AccessTokenProvider {

    private val _session = MutableStateFlow(AuthSession())
    val session: StateFlow<AuthSession> = _session.asStateFlow()

    val state: AuthState get() = _session.value.state

    init {
        // The auth client publishes a stream, where iOS resolves once. Folding
        // it through the state machine is what keeps guest mode alive across
        // the NotAuthenticated the client emits continuously — see
        // AuthSession.observedNoSession.
        scope.launch {
            supabase.auth.sessionStatus.collect { status -> apply(status) }
        }
    }

    private fun apply(status: SessionStatus) {
        _session.value = when (status) {
            is SessionStatus.Initializing -> _session.value
            is SessionStatus.Authenticated -> {
                // `user` is nullable on the wire. A session we cannot name is
                // not a session we can show a 我的 page for, so it is dropped
                // rather than signed in as a blank account.
                val user = status.session.user?.toSessionUser()
                if (user != null) {
                    _session.value.signedIn(user)
                } else {
                    Log.e(TAG, "authenticated session carried no user")
                    _session.value.observedNoSession()
                }
            }
            is SessionStatus.NotAuthenticated -> _session.value.observedNoSession()
            is SessionStatus.RefreshFailure -> {
                // A refresh that failed is not a sign-out. If a session is
                // still cached, the likely cause is a flat network, and
                // bouncing an authenticated user to Welcome over a transient
                // hiccup is worse than carrying a stale token to the next
                // refresh. That is the app's whole offline-launch behaviour.
                val cause = when (status.cause) {
                    is RefreshFailureCause.NetworkError -> SessionRefreshFailure.Unreachable
                    is RefreshFailureCause.InternalServerError -> SessionRefreshFailure.Unreachable
                    else -> SessionRefreshFailure.Unreachable
                }
                val cached = supabase.auth.currentSessionOrNull()?.user?.toSessionUser()
                _session.value.failedRefresh(cause, cached)
            }
        }
    }

    // Guest mode

    fun enterGuestMode() {
        _session.value = _session.value.enterGuest()
    }

    /** So a guest can land on Welcome and pick a flow. */
    fun exitGuestMode() {
        _session.value = _session.value.exitGuest()
    }

    val cameFromGuest: Boolean get() = _session.value.cameFromGuest

    // Email

    /**
     * Registration creates only the server-minted UID and default avatar.
     * A public nickname is optional and goes through profile-edit moderation
     * only after authentication.
     */
    suspend fun signUp(email: String, password: String): SignUpResult = try {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        if (supabase.auth.currentSessionOrNull() != null) {
            SignUpResult.SignedIn
        } else {
            SignUpResult.PendingEmailConfirmation
        }
    } catch (e: Throwable) {
        Log.e(TAG, "signup failed", e)
        SignUpResult.Failed(AuthFailure.from(e))
    }

    suspend fun signIn(email: String, password: String): AuthAttempt = try {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthAttempt.Succeeded
    } catch (e: Throwable) {
        Log.e(TAG, "signin failed", e)
        AuthAttempt.Failed(AuthFailure.from(e))
    }

    // Google

    /**
     * Credential Manager gets the ID token, then Supabase validates it.
     * [activityContext] must be an Activity — the credential sheet is UI.
     */
    suspend fun signInWithGoogle(activityContext: Context): AuthAttempt =
        when (val outcome = google.requestIdToken(activityContext)) {
            is GoogleCredentialBridge.Outcome.Cancelled -> AuthAttempt.Cancelled
            is GoogleCredentialBridge.Outcome.Failed -> {
                Log.e(TAG, "google credential failed", outcome.cause)
                AuthAttempt.Failed(AuthFailure.from(outcome.cause))
            }
            is GoogleCredentialBridge.Outcome.Token -> try {
                supabase.auth.signInWith(IDToken) {
                    provider = Google
                    idToken = outcome.idToken
                    nonce = outcome.nonce
                }
                AuthAttempt.Succeeded
            } catch (e: Throwable) {
                Log.e(TAG, "google signin failed", e)
                AuthAttempt.Failed(AuthFailure.from(e))
            }
        }

    // Apple

    /**
     * Apple has no native Android sign-in, so this leaves the app: Supabase
     * opens the flow in a Custom Tab and the result comes back as a deep link
     * that `MainActivity` hands to `handleDeeplinks`.
     *
     * **This is not optional.** Existing users who registered on iPhone with
     * 「以 Apple 登入」 belong to Supabase's Apple provider; without this path
     * they cannot reach their own account on Android at all — and to them that
     * reads as the app being broken, not as a missing feature.
     */
    suspend fun signInWithApple(): AuthAttempt = try {
        supabase.auth.signInWith(Apple)
        AuthAttempt.LaunchedExternally
    } catch (e: Throwable) {
        Log.e(TAG, "apple signin failed to launch", e)
        AuthAttempt.Failed(AuthFailure.from(e))
    }

    // Sign out

    suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
            .onFailure { Log.e(TAG, "supabase signOut failed; clearing locally anyway", it) }
        // Cleared even when the server call failed: a sign-out the user asked
        // for must not leave the previous account's data on the device because
        // the network was down.
        accountScopedStores().forEach { it.reset() }
        _session.value = _session.value.signedOut()
    }

    // Profile mirror

    fun applyNickname(nickname: String?) {
        _session.value = _session.value.applyNickname(nickname)
    }

    fun applyProfile(nickname: String?, avatar: String?) {
        _session.value = _session.value.applyProfile(nickname, avatar)
    }

    /**
     * Publish server truth into the session, if it is still the same account.
     *
     * The UID lives in `profiles.username`; the session carries only a copy
     * minted when the token was issued, and nothing the client does refreshes
     * that copy on demand. An account whose UID was assigned server-side reads
     * as null (OAuth signups, which never had the key) until the token rolls
     * over — which renders as a broken 我的公開主頁.
     */
    fun reconcileProfile(username: String?, nickname: String?, avatar: String?) {
        val current = _session.value.signedInUser ?: return
        val merged = current.merging(username, nickname, avatar)
        if (merged == current) return
        _session.value = _session.value.reconcile(merged, ifStillSignedInAs = current.id)
    }

    // AccessTokenProvider

    override val isSignedIn: Boolean get() = state is AuthState.SignedIn

    /**
     * Refreshing is a side effect of asking, which is what makes the client's
     * one-shot 401 retry work: the retry re-reads the token and must get a
     * different one, or it re-sends the rejected value and fails identically.
     *
     * The margin is why this is not just "if expired": a token that is valid
     * for another two seconds when the request is built is expired by the time
     * it is validated.
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun validAccessToken(): String {
        val current = supabase.auth.currentSessionOrNull() ?: throw ApiError.NotAuthenticated
        val expiresInMillis = current.expiresAt.toEpochMilliseconds() - System.currentTimeMillis()
        if (expiresInMillis > REFRESH_MARGIN_MILLIS) return current.accessToken

        runCatching { supabase.auth.refreshCurrentSession() }
            .onFailure { Log.e(TAG, "token refresh failed", it) }
        return supabase.auth.currentSessionOrNull()?.accessToken ?: throw ApiError.NotAuthenticated
    }

    private companion object {
        const val TAG = "TujiAuth"

        /** A token about to expire is treated as expired. */
        const val REFRESH_MARGIN_MILLIS = 60_000L
    }
}

/**
 * `user_metadata` is a mirror of `profiles`, not the authority — see
 * [SessionUser].
 */
private fun UserInfo.toSessionUser() = SessionUser(
    id = id,
    email = email,
    username = userMetadata?.get("username")?.jsonPrimitive?.contentOrNull,
    nickname = userMetadata?.get("nickname")?.jsonPrimitive?.contentOrNull,
    avatar = userMetadata?.get("avatar")?.jsonPrimitive?.contentOrNull,
)
