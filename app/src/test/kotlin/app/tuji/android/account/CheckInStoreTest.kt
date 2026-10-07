package app.tuji.android.account

import app.tuji.android.core.model.CheckInDecision.Reward
import app.tuji.android.core.model.CreditBenefits
import app.tuji.android.core.model.CreditCatalog
import app.tuji.android.core.model.CreditWallet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the store does around a claim — iOS's `CheckInModelTests`, minus the calendar. */
class CheckInStoreTest {
    private fun catalog(billingMode: String = "credits") =
        CreditCatalog(billingMode, "sandbox", true, false, true, true, true, emptyList())

    private fun wallet(checkedIn: Boolean = false, version: String = "1") =
        CreditWallet(1000, 0, 0, 1000, version, "sandbox", false, CreditBenefits(true, checkedIn, 0, true, true))

    private class Fake(var catalog: CreditCatalog, var wallet: CreditWallet, var claim: () -> CreditWallet) {
        var walletReads = 0
    }

    private fun store(fake: Fake) = CheckInStore(
        loadCatalog = { fake.catalog },
        loadWallet = { fake.walletReads++; fake.wallet },
        checkIn = { fake.claim() },
    )

    @Test fun `a legacy account never asks for a wallet it does not have`() = runTest {
        val fake = Fake(catalog("legacy"), wallet()) { wallet() }
        val store = store(fake)
        store.loadReward()
        assertEquals(0, fake.walletReads)
        assertEquals(Reward.Locked(10), store.snapshot.value.reward(true))
    }

    @Test fun `a claim swaps in the returned wallet`() = runTest {
        val fake = Fake(catalog(), wallet()) { wallet(checkedIn = true, version = "2") }
        val store = store(fake)
        store.loadReward()
        assertEquals(Reward.Claimable(10), store.snapshot.value.reward(false))
        assertTrue(store.claim())
        assertEquals(Reward.Claimed, store.snapshot.value.reward(false))
        assertFalse(store.snapshot.value.claimFailed)
    }

    @Test fun `a refused claim says so and re-reads the wallet`() = runTest {
        val fake = Fake(catalog(), wallet()) { error("409 check_in_requires_study") }
        val store = store(fake)
        store.loadReward()
        fake.wallet = wallet(checkedIn = true, version = "2")
        assertFalse(store.claim())
        assertTrue(store.snapshot.value.claimFailed)
        assertFalse(store.snapshot.value.claiming)
        assertEquals(Reward.Claimed, store.snapshot.value.reward(false))
    }

    @Test fun `an older wallet never replaces a newer one`() = runTest {
        val fake = Fake(catalog(), wallet(checkedIn = true, version = "2")) { wallet() }
        val store = store(fake)
        store.loadReward()
        fake.wallet = wallet(version = "1")
        store.loadReward()
        assertEquals(Reward.Claimed, store.snapshot.value.reward(false))
    }

    @Test fun `sign-out forgets the wallet`() = runTest {
        val store = store(Fake(catalog(), wallet()) { wallet() })
        store.loadReward()
        store.reset()
        assertEquals(Reward.Hidden, store.snapshot.value.reward(true))
    }
}
