package app.tuji.android.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app makes of `/api/billing/play/verify`'s answer, and which purchases it sends at all. */
class PlayDeliveryTest {
    private val me = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    @Test fun `a grant and a pending payment are told apart`() {
        assertEquals(DeliveryOutcome.Delivered, PlayDelivery.outcome(200, """{"lifetime":"active"}"""))
        assertEquals(DeliveryOutcome.Pending, PlayDelivery.outcome(202, """{"state":"pending"}"""))
    }

    @Test fun `a second paid lifetime is reported as owned, not as a failure`() {
        assertEquals(DeliveryOutcome.AlreadyOwned, PlayDelivery.outcome(409, """{"error":"lifetime already owned"}"""))
    }

    @Test fun `a purchase already linked on an earlier delivery counts as delivered`() {
        assertEquals(DeliveryOutcome.Delivered, PlayDelivery.outcome(409, """{"error":"purchase already linked"}"""))
    }

    @Test fun `another account's purchase and a dead token are not retried`() {
        assertEquals(DeliveryOutcome.WrongAccount, PlayDelivery.outcome(403, """{"error":"purchase_account_mismatch"}"""))
        assertEquals(DeliveryOutcome.Rejected, PlayDelivery.outcome(400, """{"error":"invalid_purchase"}"""))
        assertEquals(DeliveryOutcome.Rejected, PlayDelivery.outcome(409, """{"error":"purchase_not_active"}"""))
    }

    @Test fun `a server that could not finish leaves the token for the next try`() {
        assertEquals(DeliveryOutcome.RetryLater, PlayDelivery.outcome(503, """{"error":"billing not configured"}"""))
        assertEquals(DeliveryOutcome.RetryLater, PlayDelivery.outcome(500, null))
        assertEquals(DeliveryOutcome.RetryLater, PlayDelivery.outcome(401, null))
    }

    @Test fun `only this account's purchased, unacknowledged purchases are sent`() {
        assertTrue(PlayDelivery.needsDelivery(purchased = true, acknowledged = false, accountId = me, currentUser = me))
        assertTrue(PlayDelivery.needsDelivery(true, false, me.uppercase(), me))
        assertFalse(PlayDelivery.needsDelivery(true, acknowledged = true, accountId = me, currentUser = me))
        assertFalse(PlayDelivery.needsDelivery(purchased = false, acknowledged = false, accountId = me, currentUser = me))
        assertFalse(PlayDelivery.needsDelivery(true, false, accountId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", currentUser = me))
        assertFalse(PlayDelivery.needsDelivery(true, false, accountId = null, currentUser = me))
    }
}
