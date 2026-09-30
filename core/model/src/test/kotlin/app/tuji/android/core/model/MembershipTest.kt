package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three-tier view, as `/api/atlas/entitlement` sends it and as older servers don't. */
class MembershipTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) = json.decodeFromString<Entitlement>(body)

    @Test fun `a lifetime member reads as lifetime although plan says free`() {
        val e = decode(
            """{"plan":"free","atlasSlotsLimit":20,"membership":{"tier":"lifetime","lifetime":{"source":"appstore","acquiredAt":"2026-09-29T00:00:00Z"},
               "proExpiresAt":null,"graceEndsAt":null,"canPurchaseLifetime":false,"canPurchasePro":true,"policy":"v2","studyableCategories":null}}""",
        )
        assertEquals(MembershipTier.Lifetime, e.membershipTier)
        assertFalse(e.isPro)
        assertTrue(e.isPolicyV2)
        assertNull(e.membership?.studyableCategories)
    }

    @Test fun `a v2 non-member carries the studyable list`() {
        val e = decode("""{"plan":"free","membership":{"tier":"free","policy":"v2","studyableCategories":["fruits","bedroom"]}}""")
        assertEquals(MembershipTier.Free, e.membershipTier)
        assertEquals(listOf("fruits", "bedroom"), e.membership?.studyableCategories)
    }

    @Test fun `a server without membership falls back to plan`() {
        assertEquals(MembershipTier.Pro, decode("""{"plan":"pro"}""").membershipTier)
        assertEquals(MembershipTier.Free, decode("""{"plan":"free"}""").membershipTier)
        assertFalse(decode("""{"plan":"free"}""").isPolicyV2)
    }

    @Test fun `an unknown future tier falls back to plan rather than failing`() {
        val e = decode("""{"plan":"pro","membership":{"tier":"platinum","policy":"v2"}}""")
        assertEquals(MembershipTier.Pro, e.membershipTier)
    }

    @Test fun `the badge is a product mark, not a sentence`() {
        assertEquals(listOf("Free", "Lifetime", "Pro"), MembershipTier.entries.map { it.badge })
    }
}
