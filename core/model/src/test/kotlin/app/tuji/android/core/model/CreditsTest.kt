package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CreditsTest {
    private fun wallet(version: String, environment: String = "sandbox") = CreditWallet(
        1000, 0, 0, 1000, version, environment, false, CreditBenefits(false, false, 0, true))
    @Test fun `wallet versions above Long MAX preserve server ordering`() {
        val high = wallet("9223372036854775809")
        assertTrue(high.isNewerThan(wallet("9223372036854775808")))
        assertFalse(wallet("9223372036854775808").isNewerThan(high))
        assertTrue(wallet("10000000000000000000").isNewerThan(high))
        assertTrue(wallet("1", "production").isNewerThan(high))
    }
    @Test fun `recognition and included details keep polling until delivery`() {
        fun op(state: String, fulfillment: String) = CreditOperation("a", state, "atlas.recognize.primary", "ja", "i", 100, null, fulfillment)
        assertTrue(op("running", "unclaimed").needsPolling)
        assertTrue(op("committed", "pending").needsPolling)
        assertFalse(op("committed", "compensated").needsPolling)
        assertFalse(op("released", "unclaimed").needsPolling)
    }
    @Test fun `older entitlement responses remain decodable`() {
        assertNull(Json.decodeFromString<Entitlement>("""{"plan":"free"}""").billingMode)
    }
}
