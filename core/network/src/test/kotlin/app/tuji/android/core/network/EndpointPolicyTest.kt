package app.tuji.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three access cases, pinned as a table.
 *
 * On iOS these were two independent booleans, and the bug that produced was not
 * theoretical: `isPublic` answered both 「attach no bearer」 and 「never retry a
 * 401」, which part company on exactly one endpoint — the public collection
 * route, whose signed-in caller *does* send a token. Its 401 was the one 401
 * never retried, and the user saw the guest view of a collection they had
 * saved.
 */
class EndpointPolicyTest {

    @Test
    fun `only authenticated requires a token up front`() {
        assertTrue(EndpointAccess.Authenticated.requiresToken)
        assertFalse(EndpointAccess.Anonymous.requiresToken)
        assertFalse(EndpointAccess.OptionalToken.requiresToken)
    }

    @Test
    fun `only optional attaches a token opportunistically`() {
        assertFalse(EndpointAccess.Authenticated.attachesTokenWhenAvailable)
        assertFalse(EndpointAccess.Anonymous.attachesTokenWhenAvailable)
        assertTrue(EndpointAccess.OptionalToken.attachesTokenWhenAvailable)
    }

    @Test
    fun `a 401 is retried wherever a token may have been attached`() {
        // Including OptionalToken — the case the old `!isPublic` guard excluded.
        assertTrue(EndpointAccess.Authenticated.mayRetryUnauthorized)
        assertTrue(EndpointAccess.OptionalToken.mayRetryUnauthorized)
        assertFalse(EndpointAccess.Anonymous.mayRetryUnauthorized)
    }

    @Test
    fun `only an anonymous response may sit in a cache shared across accounts`() {
        assertTrue(EndpointAccess.Anonymous.mayBeCachedAcrossCallers)
        assertFalse(EndpointAccess.Authenticated.mayBeCachedAcrossCallers)
        assertFalse(EndpointAccess.OptionalToken.mayBeCachedAcrossCallers)
    }
}
