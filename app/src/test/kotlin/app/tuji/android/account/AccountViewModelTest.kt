package app.tuji.android.account

import app.tuji.android.core.billing.PurchaseGate
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.UserMe
import app.tuji.android.core.network.AccountReading
import app.tuji.android.core.network.EntitlementReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val me = UserMe(id = "u1", username = "TJ16227931", nickname = "Redtea")

    private fun vm(
        meOf: suspend () -> UserMe? = { me },
        entOf: suspend () -> Entitlement = { Entitlement() },
        billingAvailable: Boolean = false,
    ) = AccountViewModel(
        billingAvailable = billingAvailable,
        accounts = object : AccountReading {
            override suspend fun me() = meOf()
        },
        entitlements = object : EntitlementReading {
            override suspend fun entitlement() = entOf()
        },
        scope = TestScope(dispatcher),
    )

    @Test fun `nothing is decided before the entitlement lands`() {
        // A verdict computed from a default `free` would offer a purchase to
        // someone who already subscribed on the App Store.
        assertNull(AccountViewModel.State().purchase)
    }

    @Test fun `a free account is offered the purchase`() = runTest(dispatcher) {
        val vm = vm(); vm.refresh(); advanceUntilIdle()
        assertEquals(PurchaseGate.Verdict.Allowed, vm.state.value.purchase)
        assertEquals("Redtea", vm.state.value.me!!.displayName)
    }

    @Test fun `an App Store subscriber is not offered a second charge`() = runTest(dispatcher) {
        val vm = vm(entOf = { Entitlement(plan = "pro", source = "appstore") })
        vm.refresh(); advanceUntilIdle()
        assertEquals(
            PurchaseGate.Verdict.ManagedElsewhere("appstore"),
            vm.state.value.purchase,
        )
    }

    @Test fun `a failed entitlement read keeps the answer it had`() = runTest(dispatcher) {
        var fail = false
        val vm = vm(entOf = {
            if (fail) throw IOException("down") else Entitlement(plan = "pro", source = "play")
        })
        vm.refresh(); advanceUntilIdle()
        fail = true
        vm.refresh(); advanceUntilIdle()

        // Telling a paying subscriber they are on the free plan because one
        // request timed out is worse than saying nothing new.
        assertEquals("pro", vm.state.value.entitlement!!.plan)
    }

    @Test fun `a nickname wins over the UID, never the other way`() {
        assertEquals("Redtea", UserMe(id = "u1", username = "TJ1", nickname = "Redtea").displayName)
        assertEquals("TJ1", UserMe(id = "u1", username = "TJ1").displayName)
        assertEquals("TJ1", UserMe(id = "u1", username = "TJ1", nickname = " ").displayName)
    }

    @Test fun `no purchase button exists while the store is not set up`() =
        runTest(dispatcher) {
            // The verdict still says Allowed — the account *could* buy. What is
            // missing is a way to sell, and the screen must not offer a control
            // that cannot do its job.
            val vm = vm(billingAvailable = false); vm.refresh(); advanceUntilIdle()
            assertEquals(PurchaseGate.Verdict.Allowed, vm.state.value.purchase)
            assertEquals(false, vm.state.value.billingAvailable)
        }

    @Test fun `the flag flips in one place`() = runTest(dispatcher) {
        val vm = vm(billingAvailable = true); vm.refresh(); advanceUntilIdle()
        assertEquals(true, vm.state.value.billingAvailable)
    }

    @Test fun `a failed me read does not blank the screen`() = runTest(dispatcher) {
        var fail = false
        val vm = vm(meOf = { if (fail) throw IOException("down") else me })
        vm.refresh(); advanceUntilIdle()
        fail = true
        vm.refresh(); advanceUntilIdle()
        assertEquals("Redtea", vm.state.value.me!!.displayName)
    }
}
