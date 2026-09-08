package app.tuji.android.core.auth

/**
 * Why an attempt failed, as a **decision rather than a sentence**.
 *
 * iOS returns an already-localised string from the service. That is where one
 * of its worst-looking bugs came from: the unrecognised-error arm used to
 * `return msg`, so during the 2026-08 outage the sign-in screen showed
 * *"Service for this project is restricted due to the following violations:
 * exceed_cached_egress_quota…"* — a billing notice addressed to the developer,
 * printed to a user trying to log in.
 *
 * An enum makes that structurally impossible: there is no path by which raw
 * server English reaches a screen, because the service has no way to say
 * anything except one of these. It also means the mapping is testable without
 * resources, and that the tests assert the decision rather than the copy —
 * which they must, because CI runs in English and a developer's machine
 * does not.
 */
enum class AuthFailure {
    InvalidCredentials,
    EmailAlreadyRegistered,
    RateLimited,
    ProviderNotEnabled,
    PasswordTooShort,
    InvalidEmail,

    /** Anything else. The UI shows its own sentence; the server's never appears. */
    Unknown,
    ;

    companion object {
        /**
         * Classifies by message because that is the only signal Supabase gives
         * for these — the HTTP status is 400 for most of them.
         */
        fun from(error: Throwable?): AuthFailure {
            val msg = error?.message?.lowercase() ?: return Unknown
            return when {
                "invalid login credentials" in msg -> InvalidCredentials
                "user already registered" in msg -> EmailAlreadyRegistered
                "rate limit" in msg -> RateLimited
                "provider" in msg && "not enabled" in msg -> ProviderNotEnabled
                "password should be" in msg -> PasswordTooShort
                "email address" in msg && "invalid" in msg -> InvalidEmail
                else -> Unknown
            }
        }
    }
}

/** The outcome of one sign-in attempt. */
sealed interface AuthAttempt {
    data object Succeeded : AuthAttempt

    /** The user backed out. Not an error, and must not leave a red line. */
    data object Cancelled : AuthAttempt

    data class Failed(val reason: AuthFailure) : AuthAttempt

    /**
     * The flow was handed to a browser and the result will arrive as a deep
     * link — Apple sign-in, which has no native Android form.
     *
     * Spelled out rather than folded into [Succeeded] because it is not
     * success: nothing has been authenticated when this returns, and a screen
     * that treats it as success dismisses itself before the user has typed
     * their Apple ID.
     */
    data object LaunchedExternally : AuthAttempt
}

/** What [AuthService.signUp] resolved to. */
sealed interface SignUpResult {
    data object SignedIn : SignUpResult

    /** The project requires email confirmation; there is no session yet. */
    data object PendingEmailConfirmation : SignUpResult

    data class Failed(val reason: AuthFailure) : SignUpResult
}
