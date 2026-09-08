package app.tuji.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    // Guest mode — the silent no-ops.

    @Test
    fun `guest mode is enterable only from signed out`() {
        assertEquals(AuthState.Guest, signedOut.enterGuest().state)
    }

    @Test
    fun `entering guest from anywhere else does nothing at all`() {
        val checking = AuthSession()
        assertSame(checking, checking.enterGuest())

        val inSession = AuthSession().signedIn(alice)
        assertSame(inSession, inSession.enterGuest())

        val guest = signedOut.enterGuest()
        assertSame(guest, guest.enterGuest())
    }

    @Test
    fun `leaving guest from anywhere else does nothing at all`() {
        assertSame(signedOut, signedOut.exitGuest())

        val inSession = AuthSession().signedIn(alice)
        assertSame(inSession, inSession.exitGuest())
    }

    @Test
    fun `leaving guest marks Welcome as reachable from browsing`() {
        // Without this flag Welcome is an exit-less dead end for someone who
        // tapped 登入 by accident.
        val s = signedOut.enterGuest().exitGuest()
        assertEquals(AuthState.SignedOut, s.state)
        assertTrue(s.cameFromGuest)
    }

    @Test
    fun `entering guest clears the flag`() {
        val s = signedOut.enterGuest().exitGuest().enterGuest()
        assertFalse(s.cameFromGuest)
    }

    @Test
    fun `signing out clears the flag`() {
        val s = signedOut.enterGuest().exitGuest().signedIn(alice).signedOut()
        assertEquals(AuthState.SignedOut, s.state)
        assertFalse(s.cameFromGuest)
    }

    @Test
    fun `a first launch has not come from guest`() {
        assertFalse(AuthSession().cameFromGuest)
        assertFalse(signedOut.cameFromGuest)
    }

    // Observed "no session" — the shape Android has and iOS does not.

    @Test
    fun `a guest survives the client reporting no session`() {
        // supabase-kt emits NotAuthenticated continuously, including all the
        // way through guest browsing. Folding that into SignedOut would throw
        // the guest back to Welcome, repeatedly, with nothing on screen to say
        // why. iOS never had to answer this — its resolve is a one-shot call,
        // not a stream.
        val guest = signedOut.enterGuest()
        assertSame(guest, guest.observedNoSession())
        assertSame(guest, guest.observedNoSession().observedNoSession())
    }

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
        assertNull(signedOut.enterGuest().signedInUser)
    }
}
