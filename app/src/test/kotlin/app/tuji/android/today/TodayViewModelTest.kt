package app.tuji.android.today

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyStats
import app.tuji.android.core.model.StudyStatsResponse
import app.tuji.android.core.network.StudyStatsReading
import app.tuji.android.core.study.TodayDecisions
import app.tuji.android.core.study.TodaySubtitle
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

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** A reader that answers with whatever the test hands it. */
    private fun reader(body: suspend () -> StudyStatsResponse) = object : StudyStatsReading {
        override suspend fun stats(learning: LearningDirection) = body()
    }

    private fun vm(
        source: StudyStatsReading,
        isGuest: () -> Boolean = { false },
    ) = TodayViewModel(
        stats = source,
        direction = LearningDirection.ZH_JA,
        isGuest = isGuest,
        scope = TestScope(dispatcher),
    )

    private val good = StudyStats(total = 557, seen = 28, due = 3, new = 529, todayNew = 2)

    @Test fun `the numbers land`() = runTest(dispatcher) {
        val vm = vm(reader { StudyStatsResponse(stats = good) })
        vm.refresh(); advanceUntilIdle()
        assertEquals(good, vm.inputs.value.stats)
        assertEquals(TodaySubtitle.ReviewDue, TodayDecisions(vm.inputs.value).subtitle)
    }

    @Test fun `a failed refresh keeps the numbers it had`() = runTest(dispatcher) {
        var fail = false
        val vm = vm(
            reader {
                if (fail) throw java.io.IOException("offline") else StudyStatsResponse(stats = good)
            },
        )
        vm.refresh(); advanceUntilIdle()
        fail = true
        vm.refresh(); advanceUntilIdle()

        // Stats a minute stale still describe the day; nulling them would put
        // the screen back to 「正在看今天的進度…」 and grey both buttons.
        assertEquals(good, vm.inputs.value.stats)
    }

    @Test fun `the first failure leaves it honestly unknown rather than empty`() =
        runTest(dispatcher) {
            val vm = vm(reader { throw java.io.IOException("offline") })
            vm.refresh(); advanceUntilIdle()
            assertNull(vm.inputs.value.stats)
            assertEquals(TodaySubtitle.Unknown, TodayDecisions(vm.inputs.value).subtitle)
        }

    @Test fun `signing out between refreshes is picked up`() = runTest(dispatcher) {
        var guest = false
        val vm = vm(reader { StudyStatsResponse(stats = good) }, isGuest = { guest })
        vm.refresh(); advanceUntilIdle()
        assertEquals(TodaySubtitle.ReviewDue, TodayDecisions(vm.inputs.value).subtitle)

        guest = true
        vm.refresh(); advanceUntilIdle()
        assertEquals(TodaySubtitle.GuestBrowsing, TodayDecisions(vm.inputs.value).subtitle)
    }
}
