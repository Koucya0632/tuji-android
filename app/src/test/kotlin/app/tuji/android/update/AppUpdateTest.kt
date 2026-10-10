package app.tuji.android.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 60L * 60 * 1000
private const val DAY = 24 * HOUR

/**
 * The live half of this only runs on a Play-installed build, so the rules are
 * the part that can be wrong without anybody on the team ever seeing it.
 */
class AppUpdatePolicyTest {

    @Test fun `play is asked once a day, and at once the first time`() {
        assertTrue(AppUpdatePolicy.shouldCheck(lastCheckedAtMillis = null, nowMillis = 0))
        assertFalse(AppUpdatePolicy.shouldCheck(lastCheckedAtMillis = 0, nowMillis = DAY - 1))
        assertTrue(AppUpdatePolicy.shouldCheck(lastCheckedAtMillis = 0, nowMillis = DAY))
    }

    @Test fun `a clock set backwards does not switch the check off`() {
        assertTrue(AppUpdatePolicy.shouldCheck(lastCheckedAtMillis = 10 * DAY, nowMillis = DAY))
    }

    @Test fun `only a newer build is worth mentioning`() {
        assertTrue(AppUpdatePolicy.shouldPrompt(9, 10, snoozed = null, nowMillis = 0))
        assertFalse(AppUpdatePolicy.shouldPrompt(9, 9, snoozed = null, nowMillis = 0))
        // A tester on a build ahead of the track must not be told to "update" backwards.
        assertFalse(AppUpdatePolicy.shouldPrompt(11, 10, snoozed = null, nowMillis = 0))
    }

    @Test fun `later keeps that version quiet for three days, not forever`() {
        val snoozed = SnoozedAppUpdate(versionCode = 10, atMillis = 0)
        assertFalse(AppUpdatePolicy.shouldPrompt(9, 10, snoozed, nowMillis = 3 * DAY - 1))
        assertTrue(AppUpdatePolicy.shouldPrompt(9, 10, snoozed, nowMillis = 3 * DAY))
    }

    @Test fun `a newer build than the one put off asks again at once`() {
        val snoozed = SnoozedAppUpdate(versionCode = 10, atMillis = 0)
        assertTrue(AppUpdatePolicy.shouldPrompt(9, 11, snoozed, nowMillis = HOUR))
    }

    @Test fun `it waits for a study session and for the tour`() {
        assertTrue(AppUpdatePolicy.mayPresent(10, studyFocusActive = false, tourRunning = false))
        assertFalse(AppUpdatePolicy.mayPresent(10, studyFocusActive = true, tourRunning = false))
        assertFalse(AppUpdatePolicy.mayPresent(10, studyFocusActive = false, tourRunning = true))
        assertFalse(AppUpdatePolicy.mayPresent(null, studyFocusActive = false, tourRunning = false))
    }
}

class AppUpdateStoreTest {

    private class Memory : AppUpdateMemory {
        override var lastCheckedAtMillis: Long? = null
        override var snoozed: SnoozedAppUpdate? = null
    }

    private class Lookup(var answer: Int? = null, var fails: Boolean = false) : AppUpdateLookup {
        var calls = 0
        override suspend fun availableVersionCode(): Int? {
            calls++
            if (fails) throw IllegalStateException("not installed from Play")
            return answer
        }
    }

    private var now = 100 * DAY
    private val memory = Memory()

    private fun store(lookup: Lookup, installed: Int = 9) =
        AppUpdateStore(installed, lookup, memory, now = { now })

    @Test fun `a newer build becomes the pending one`() = runTest {
        val store = store(Lookup(answer = 10))
        store.checkIfNeeded()
        assertEquals(10, store.pendingVersionCode.value)
    }

    @Test fun `nothing newer leaves nothing pending`() = runTest {
        val lookup = Lookup(answer = null)
        val store = store(lookup)
        store.checkIfNeeded()
        assertNull(store.pendingVersionCode.value)
    }

    @Test fun `a second foreground the same day does not ask play again`() = runTest {
        val lookup = Lookup(answer = 10)
        val store = store(lookup)
        store.checkIfNeeded()
        now += HOUR
        store.checkIfNeeded()
        assertEquals(1, lookup.calls)
    }

    /** The debug build's whole life: Play refuses, and nothing is shown or remembered. */
    @Test fun `a lookup that fails is silent and is tried again next time`() = runTest {
        val lookup = Lookup(fails = true)
        val store = store(lookup)
        store.checkIfNeeded()
        assertNull(store.pendingVersionCode.value)
        assertNull(memory.lastCheckedAtMillis)

        lookup.fails = false
        lookup.answer = 10
        store.checkIfNeeded()
        assertEquals(10, store.pendingVersionCode.value)
    }

    @Test fun `answering puts that version off across a relaunch`() = runTest {
        val first = store(Lookup(answer = 10))
        first.checkIfNeeded()
        first.dismiss()
        assertNull(first.pendingVersionCode.value)
        assertEquals(SnoozedAppUpdate(10, 100 * DAY), memory.snoozed)

        // The next day, a new process: Play is asked, the same build stays quiet.
        now += DAY
        val second = store(Lookup(answer = 10))
        second.checkIfNeeded()
        assertNull(second.pendingVersionCode.value)

        now += 2 * DAY
        val third = store(Lookup(answer = 10))
        third.checkIfNeeded()
        assertEquals(10, third.pendingVersionCode.value)
    }

    @Test fun `dismissing with nothing pending remembers nothing`() {
        store(Lookup()).dismiss()
        assertNull(memory.snoozed)
    }
}
