package app.tuji.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthFailureTest {

    private fun classify(message: String?) = AuthFailure.from(message?.let { RuntimeException(it) })

    @Test
    fun `the six recognised server messages map to their own cases`() {
        assertEquals(AuthFailure.InvalidCredentials, classify("Invalid login credentials"))
        assertEquals(AuthFailure.EmailAlreadyRegistered, classify("User already registered"))
        assertEquals(AuthFailure.RateLimited, classify("Email rate limit exceeded"))
        assertEquals(AuthFailure.ProviderNotEnabled, classify("Provider apple is not enabled"))
        assertEquals(AuthFailure.PasswordTooShort, classify("Password should be at least 8 characters"))
        assertEquals(AuthFailure.InvalidEmail, classify("Email address is invalid"))
    }

    @Test
    fun `classification does not depend on the server's capitalisation`() {
        assertEquals(AuthFailure.InvalidCredentials, classify("INVALID LOGIN CREDENTIALS"))
        assertEquals(AuthFailure.InvalidCredentials, classify("invalid login credentials"))
    }

    @Test
    fun `a developer-facing outage notice does not become a user-facing sentence`() {
        // The real 2026-08 string. On iOS this reached the sign-in screen
        // verbatim, because the fallback arm returned the server's own message.
        val outage = "Service for this project is restricted due to the following " +
            "violations: exceed_cached_egress_quota. Please check your usage."
        assertEquals(AuthFailure.Unknown, classify(outage))
    }

    @Test
    fun `a missing message is unknown rather than a crash`() {
        assertEquals(AuthFailure.Unknown, classify(null))
        assertEquals(AuthFailure.Unknown, AuthFailure.from(null))
    }

    @Test
    fun `provider alone is not enough to blame the provider`() {
        // "not enabled" is the other half. Without it, a message merely
        // mentioning a provider would be misreported as a configuration fault.
        assertEquals(AuthFailure.Unknown, classify("Provider returned an unexpected response"))
    }
}
