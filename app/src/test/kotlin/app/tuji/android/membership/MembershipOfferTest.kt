package app.tuji.android.membership

import app.tuji.android.R
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.LifetimeHolding
import app.tuji.android.core.model.Membership
import app.tuji.android.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MembershipOfferTest {

    private fun entitlement(tier: String, policy: String = "v2", plan: String = "free", lifetime: Boolean = false) =
        Entitlement(
            plan = plan,
            membership = Membership(
                tier = tier,
                policy = policy,
                lifetime = if (lifetime) LifetimeHolding(source = "appstore") else null,
            ),
        )

    @Test fun `before the cutover there is no lifetime card and Pro keeps its old quota`() {
        val offer = MembershipOffer.from(entitlement("free", policy = "v1"))
        assertFalse(offer.showsLifetime)
        assertTrue(R.string.membership_benefit_ai_500 in offer.proRows)
        assertEquals(R.string.membership_pro_headline, offer.headline)
    }

    @Test fun `a v2 non-member is sold lifetime first`() {
        val offer = MembershipOffer.from(entitlement("free"))
        assertTrue(offer.showsLifetime)
        assertFalse(offer.ownsLifetime)
        assertEquals(R.string.membership_lifetime_headline, offer.headline)
        assertTrue(R.string.membership_benefit_ai_200 in offer.proRows)
        assertEquals(R.string.membership_benefit_notes, offer.lifetimeRows.last())
    }

    @Test fun `a lifetime member is shown what Pro adds`() {
        val offer = MembershipOffer.from(entitlement("lifetime", lifetime = true))
        assertTrue(offer.ownsLifetime)
        assertFalse(offer.ownsPro)
        assertEquals(R.string.membership_pro_headline, offer.headline)
    }

    @Test fun `a Pro subscriber who also bought lifetime owns both`() {
        val offer = MembershipOffer.from(entitlement("pro", plan = "pro", lifetime = true))
        assertTrue(offer.ownsPro)
        assertTrue(offer.ownsLifetime)
    }

    @Test fun `an unknown entitlement is a non-member under v1`() {
        val offer = MembershipOffer.from(null)
        assertFalse(offer.showsLifetime)
        assertFalse(offer.ownsPro)
    }

    @Test fun `only a 402 is a refusal, and only its message is read`() {
        val refusal = ApiError.Http(402, """{"error":"membership_required","message":"收藏是會員功能"}""")
        assertTrue(MembershipRefusal.isRefusal(refusal))
        assertEquals("收藏是會員功能", MembershipRefusal.message(refusal))
        assertNull(MembershipRefusal.message(ApiError.Http(402, "not json")))
        assertFalse(MembershipRefusal.isRefusal(ApiError.Http(429, null)))
        assertFalse(MembershipRefusal.isRefusal(java.io.IOException("offline")))
    }
}
