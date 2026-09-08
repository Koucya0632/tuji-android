package app.tuji.android.core.billing

import app.tuji.android.core.model.Entitlement
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseGateTest {

    private fun ent(plan: String = "free", source: String? = null) =
        Entitlement(plan = plan, source = source)

    @Test fun `a free account may buy`() {
        assertEquals(PurchaseGate.Verdict.Allowed, PurchaseGate.verdict(ent()))
    }

    @Test fun `an App Store subscriber is sent back to the App Store`() {
        // The whole point of ADR-0001: two stores would each take money and
        // Tuji can refund neither.
        val v = PurchaseGate.verdict(ent("pro", "appstore"))
        assertEquals(PurchaseGate.Verdict.ManagedElsewhere("appstore"), v)
    }

    @Test fun `the verdict carries where to manage it, not just a refusal`() {
        val v = PurchaseGate.verdict(ent("pro", "appstore")) as PurchaseGate.Verdict.ManagedElsewhere
        assertEquals("「你已經是 Pro」而說不出去哪裡管理，是死路", "appstore", v.source)
    }

    @Test fun `a Play subscriber has nothing left to buy`() {
        assertEquals(
            PurchaseGate.Verdict.AlreadySubscribed,
            PurchaseGate.verdict(ent("pro", "play")),
        )
    }

    @Test fun `a comped account is not blocked`() {
        // A manual grant is not a second charge. Blocking on plan==pro alone
        // would stop a comped user from ever actually subscribing.
        assertEquals(PurchaseGate.Verdict.Allowed, PurchaseGate.verdict(ent("pro", null)))
        assertEquals(PurchaseGate.Verdict.Allowed, PurchaseGate.verdict(ent("pro", "")))
    }

    @Test fun `an older backend that sends no source does not lock anyone out`() {
        // The field ships after this client does. Failing open here is the
        // difference between "cannot buy yet" and "cannot buy, ever".
        assertEquals(PurchaseGate.Verdict.Allowed, PurchaseGate.verdict(Entitlement(plan = "pro")))
    }

    @Test fun `the same rule works from the other side`() {
        // Stated as a parameter rather than an Android constant, so the iOS
        // half of this decision is the same function with a different argument.
        assertEquals(
            PurchaseGate.Verdict.ManagedElsewhere("play"),
            PurchaseGate.verdict(ent("pro", "play"), thisStore = PurchaseGate.APP_STORE),
        )
    }

    @Test fun `an unknown third store still says where to go`() {
        val v = PurchaseGate.verdict(ent("pro", "stripe"))
        assertEquals(PurchaseGate.Verdict.ManagedElsewhere("stripe"), v)
    }
}
