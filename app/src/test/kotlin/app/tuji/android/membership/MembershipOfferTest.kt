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

    @Test fun `before the cutover there is no lifetime card`() {
        val offer = MembershipOffer.from(entitlement("free", policy = "v1"))
        assertFalse(offer.showsLifetime)
    }

    @Test fun `a v2 non-member is sold lifetime`() {
        val offer = MembershipOffer.from(entitlement("free"))
        assertTrue(offer.showsLifetime)
        assertFalse(offer.ownsLifetime)
        assertEquals(R.string.membership_lifetime_headline, offer.headline)
    }

    @Test fun `the lifetime card leads with the atlas and scenes, the same under points`() {
        val expected = listOf(
            R.string.membership_benefit_all_series,
            R.string.membership_benefit_scenes,
            R.string.membership_benefit_slots_200,
            R.string.membership_benefit_lists_20,
            R.string.membership_benefit_notes,
            R.string.membership_benefit_community,
            R.string.membership_benefit_ai_credits,
        )
        assertEquals(expected, MembershipOffer.from(entitlement("free")).lifetimeRows)
        assertEquals(expected, MembershipOffer.from(entitlement("free").copy(billingMode = "credits")).lifetimeRows)
    }

    @Test fun `a lifetime member owns lifetime and no Pro`() {
        val offer = MembershipOffer.from(entitlement("lifetime", lifetime = true))
        assertTrue(offer.ownsLifetime)
        assertFalse(offer.ownsPro)
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
