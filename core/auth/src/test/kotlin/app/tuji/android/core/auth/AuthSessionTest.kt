package app.tuji.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Every rule the iOS counterpart had to write down in prose because nothing
 * verified it. The point of this file is that they are now checked rather than
 * described.
 */
class AuthSessionTest {

    private val alice = SessionUser(id = "u-alice", email = "a@example.test", username = "TJ00000001")
    private val bob = SessionUser(id = "u-bob", email = "b@example.test", username = "TJ00000002")

    private val signedOut = AuthSession().failedRefresh(SessionRefreshFailure.NoSession, cached = null)

    @Test
    fun `a launch starts by checking, not signed out`() {
        // The distinction routes the splash screen: Checking shows the splash,
        // SignedOut shows Welcome. Collapsing them flashes Welcome at every
        // signed-in user on every cold start.
        assertEquals(AuthState.Checking, AuthSession().state)
    }

    @Test
    fun `a restored session signs in`() {
        assertEquals(AuthState.SignedIn(alice), AuthSession().restored(alice).state)
    }

    // The offline rule — the app's whole offline-launch behaviour.

    @Test
    fun `an unreachable refresh with a cached session stays signed in`() {
        val s = AuthSession().failedRefresh(SessionRefreshFailure.Unreachable, cached = alice)
        assertEquals(AuthState.SignedIn(alice), s.state)
    }

    @Test
    fun `an unreachable refresh with nothing cached signs out`() {
        val s = AuthSession().failedRefresh(SessionRefreshFailure.Unreachable, cached = null)
        assertEquals(AuthState.SignedOut, s.state)
    }

    @Test
    fun `no session at all signs out even when something is cached`() {
        // "There was never a session" is the server's answer, not the network's.
        // A cached user must not override it, or a revoked account stays in.
        val s = AuthSession().failedRefresh(SessionRefreshFailure.NoSession, cached = alice)
        assertEquals(AuthState.SignedOut, s.state)
    }

    @Test
    fun `a retried refresh settles a launch still on the splash`() {
        // Offline, with a token past 80% of its life but not expired: the
        // client retries every ten seconds and never leaves Initializing.
        val s = AuthSession().refreshRetrying(cached = alice)
        assertEquals(AuthState.SignedIn(alice), s.state)
    }

    @Test
    fun `a retried refresh with nothing cached keeps waiting`() {
        assertEquals(AuthState.Checking, AuthSession().refreshRetrying(cached = null).state)
    }

    @Test
    fun `a retried refresh changes nothing once past the splash`() {
        // The client emits the same event for a mid-session refresh that fails.
        // It must not sign anyone in, or swap the account on screen.
        assertSame(signedOut, signedOut.refreshRetrying(cached = alice))
        val bobIn = AuthSession().signedIn(bob)
        assertSame(bobIn, bobIn.refreshRetrying(cached = alice))
    }

    // Observed "no session" — the shape Android has and iOS does not.

    @Test
    fun `a signed-in session ending signs out`() {
        val s = AuthSession().signedIn(alice).observedNoSession()
        assertEquals(AuthState.SignedOut, s.state)
    }

    @Test
    fun `checking resolves to signed out when there is no session`() {
        assertEquals(AuthState.SignedOut, AuthSession().observedNoSession().state)
    }

    // Profile mirror — optimistic, and only while signed in.

    @Test
    fun `a nickname edit shows immediately without waiting for a token refresh`() {
        val s = AuthSession().signedIn(alice).applyNickname("阿貓")
        assertEquals("阿貓", s.signedInUser?.nickname)
        // The UID is system-assigned and must survive a display-name edit.
        assertEquals("TJ00000001", s.signedInUser?.username)
    }

    @Test
    fun `a profile edit landing after sign-out does not resurrect the session`() {
        val s = AuthSession().signedIn(alice).signedOut().applyProfile("阿貓", "avatar.webp")
        assertEquals(AuthState.SignedOut, s.state)
        assertNull(s.signedInUser)
    }

    @Test
    fun `a nickname edit landing after sign-out does not resurrect the session`() {
        val s = AuthSession().signedIn(alice).signedOut().applyNickname("阿貓")
        assertEquals(AuthState.SignedOut, s.state)
    }

    // Reconciliation — the request runs off the launch-critical path.

    @Test
    fun `server truth is published into the session that asked for it`() {
        val fresh = alice.merging(username = "TJ99999999", nickname = "阿貓", avatar = "a.webp")
        val s = AuthSession().signedIn(alice).reconcile(fresh, ifStillSignedInAs = alice.id)
        assertEquals("TJ99999999", s.signedInUser?.username)
    }

    @Test
    fun `server truth for the previous account is dropped after a switch`() {
        // The hydrate request was in flight while the user switched accounts.
        // Publishing it would show Alice's UID on Bob's 我的.
        val fresh = alice.merging(username = "TJ99999999", nickname = null, avatar = null)
        val s = AuthSession().signedIn(bob).reconcile(fresh, ifStillSignedInAs = alice.id)
        assertEquals(bob, s.signedInUser)
    }

    @Test
    fun `server truth arriving after a sign-out is dropped`() {
        val fresh = alice.merging(username = "TJ99999999", nickname = null, avatar = null)
        val s = AuthSession().signedIn(alice).signedOut().reconcile(fresh, ifStillSignedInAs = alice.id)
        assertEquals(AuthState.SignedOut, s.state)
    }

    @Test
    fun `a partial server payload never blanks a good value`() {
        val merged = alice.copy(nickname = "阿貓", avatar = "a.webp")
            .merging(username = null, nickname = null, avatar = null)
        assertEquals("TJ00000001", merged.username)
        assertEquals("阿貓", merged.nickname)
        assertEquals("a.webp", merged.avatar)
    }

    @Test
    fun `signedInUser is null in every state that is not signed in`() {
        assertNull(AuthSession().signedInUser)
        assertNull(signedOut.signedInUser)
    }
}
