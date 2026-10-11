package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureEntryTest {

    @Test fun `a credit account gets the capture flow`() {
        assertEquals(CaptureEntry.Capture, CaptureEntry.of(Entitlement(billingMode = "credits")))
    }

    @Test fun `anyone else is shown how to become a member`() {
        // There is no second flow to fall back to; the server refuses its every request.
        assertEquals(CaptureEntry.Membership, CaptureEntry.of(Entitlement(billingMode = "legacy")))
        assertEquals(CaptureEntry.Membership, CaptureEntry.of(Entitlement()))
    }

    @Test fun `an entitlement that has not arrived is not guessed at`() {
        assertEquals(CaptureEntry.Resolving, CaptureEntry.of(null))
    }
}
