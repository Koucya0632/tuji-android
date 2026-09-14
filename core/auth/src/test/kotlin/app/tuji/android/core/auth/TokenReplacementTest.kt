package app.tuji.android.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenReplacementTest {

    /**
     * The defect: the device believed the refused token had hours left, so the
     * retry was handed the same token and failed the same way, on every request.
     */
    @Test fun `a refused token is refreshed even if it is the one the session holds`() {
        assertEquals(TokenReplacement.Refresh, TokenReplacement.decide(current = "a", rejected = "a"))
    }

    @Test fun `a token another retry already replaced is used, not refreshed again`() {
        assertEquals(TokenReplacement.UseCurrent, TokenReplacement.decide(current = "b", rejected = "a"))
    }

    @Test fun `no token in the session means refreshing from the stored one`() {
        assertEquals(TokenReplacement.Refresh, TokenReplacement.decide(current = null, rejected = "a"))
        assertEquals(TokenReplacement.Refresh, TokenReplacement.decide(current = null, rejected = null))
    }

    /** An optional-auth request whose token lookup failed went out bare. */
    @Test fun `a request refused without a token takes whatever the session now holds`() {
        assertEquals(TokenReplacement.UseCurrent, TokenReplacement.decide(current = "b", rejected = null))
    }
}
