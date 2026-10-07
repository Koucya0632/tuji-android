package app.tuji.android.account

import app.tuji.android.core.model.CheckInDecision.Reward
import app.tuji.android.core.model.CreditBenefits
import app.tuji.android.core.model.CreditCatalog
import app.tuji.android.core.model.CreditWallet
import app.tuji.android.core.model.StudyCalendarMonth
import app.tuji.android.core.model.StudyStreak
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

    private fun month(month: String, totalDays: Int = 9) =
        StudyCalendarMonth(month, "Asia/Taipei", "2026-10-07", listOf("2026-10-01"), StudyStreak(1, 4, totalDays, 1, "2026-10-07"))

    private fun calendarStore(load: suspend (String?) -> StudyCalendarMonth) = CheckInStore(
        loadCatalog = { catalog() }, loadWallet = { wallet() }, checkIn = { wallet() }, loadCalendar = load,
    )

    @Test fun `month paging stops at the current month and a year back`() = runTest {
        val asked = mutableListOf<String?>()
        val store = calendarStore { asked += it; month(it ?: "2026-10") }
        store.loadCalendar()
        assertFalse(store.snapshot.value.canShowLater)
        assertTrue(store.snapshot.value.canShowEarlier)
        store.showMonth(1)
        assertEquals(listOf<String?>(null), asked)
        store.showMonth(-1)
        assertEquals("2026-09", asked.last())
        assertTrue(store.snapshot.value.canShowLater)
        repeat(20) { store.showMonth(-1) }
        assertEquals("2025-10", store.snapshot.value.calendar?.month)
        assertFalse(store.snapshot.value.canShowEarlier)
    }

    @Test fun `an account that never studied has no earlier month`() = runTest {
        val store = calendarStore { month(it ?: "2026-10", totalDays = 0) }
        store.loadCalendar()
        assertFalse(store.snapshot.value.canShowEarlier)
    }

    @Test fun `a month that will not load keeps the one on screen`() = runTest {
        var fail = false
        val store = calendarStore { if (fail) error("offline") else month(it ?: "2026-10") }
        store.loadCalendar()
        fail = true
        store.showMonth(-1)
        assertEquals("2026-10", store.snapshot.value.calendar?.month)
        assertFalse(store.snapshot.value.calendarFailed)
    }

    @Test fun `a first calendar read that fails says so`() = runTest {
        val store = calendarStore { error("offline") }
        store.loadCalendar()
        assertTrue(store.snapshot.value.calendarFailed)
    }

    @Test fun `the chip's read is skipped while fresh`() = runTest {
        var clock = 0L
        val fake = Fake(catalog(), wallet()) { wallet() }
        var catalogReads = 0
        val store = CheckInStore(
            loadCatalog = { catalogReads++; fake.catalog }, loadWallet = { fake.wallet }, checkIn = { fake.wallet },
            now = { clock },
        )
        store.loadRewardIfStale()
        clock = 30_000
        store.loadRewardIfStale()
        assertEquals(1, catalogReads)
        clock = 61_000
        store.loadRewardIfStale()
        assertEquals(2, catalogReads)
    }
}
