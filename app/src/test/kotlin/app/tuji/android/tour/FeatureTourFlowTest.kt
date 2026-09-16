package app.tuji.android.tour

import app.tuji.android.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour runs once, for about twenty seconds, on somebody's first launch —
 * so every way it can be wrong is a way nobody will ever report.
 */
class FeatureTourFlowTest {

    @Test fun `it is five steps`() {
        assertEquals(5, FeatureTourFlow(isGuest = false).steps.size)
        assertEquals(5, FeatureTourFlow(isGuest = true).steps.size)
    }

    @Test fun `a guest is pointed at what a guest actually has`() {
        // No CTA pair and no goal bar until there is an account, so pointing at
        // them cuts a hole over nothing.
        val guest = FeatureTourFlow(isGuest = true).steps
        assertEquals(TourTarget.Hero, guest[0].target)
        assertEquals(TourTarget.Streak, guest[1].target)

        val user = FeatureTourFlow(isGuest = false).steps
        assertEquals(TourTarget.HeroCtas, user[0].target)
        assertEquals(TourTarget.DailyGoal, user[1].target)
    }

    @Test fun `every step that points at content has somewhere to fall back to`() {
        // Except the two that point at the bar, which is always there.
        FeatureTourFlow(isGuest = false).steps.forEach { step ->
            if (step.target != null && step.target !in setOf(TourTarget.TabBar, TourTarget.Capture)) {
                assert(step.fallback != null) { "step ${step.id} has no fallback" }
            }
        }
    }

    @Test fun `the closing step has no hole to cut`() {
        val last = FeatureTourFlow(isGuest = false).steps.last()
        assertNull(last.target)
        assertNull(last.fallback)
    }

    @Test fun `capture is framed round and the bar is framed square`() {
        // The eye is the one circle in the app; the bar is a slab.
        val steps = FeatureTourFlow(isGuest = false).steps
        assertEquals(TourCutoutShape.Pill, steps.single { it.target == TourTarget.Capture }.shape)
        assertEquals(TourCutoutShape.Square, steps.single { it.target == TourTarget.TabBar }.shape)
    }

    @Test fun `advancing within a tab does not switch tabs`() {
        val flow = FeatureTourFlow(isGuest = false)
        assertEquals(TourAdvance.Show(1), flow.advance(from = 0, showing = AppRoute.Today))
    }

    @Test fun `the last step lives on another tab and says so`() {
        // The branch that can only be reached by launching the app, which is
        // why it is the one worth a test.
        val flow = FeatureTourFlow(isGuest = false)
        assertEquals(
            TourAdvance.CrossTab(AppRoute.Atlas, 4),
            flow.advance(from = 3, showing = AppRoute.Today),
        )
    }

    @Test fun `a step already on the showing tab is not a cross-tab`() {
        val flow = FeatureTourFlow(isGuest = false)
        // Somebody who tapped 圖鑑 mid-tour is already there.
        assertEquals(TourAdvance.Show(4), flow.advance(from = 3, showing = AppRoute.Atlas))
    }

    @Test fun `advancing past the end finishes`() {
        val flow = FeatureTourFlow(isGuest = false)
        assertEquals(TourAdvance.Finish, flow.advance(from = 4, showing = AppRoute.Atlas))
    }

    @Test fun `a deep link beats the tour`() {
        // They asked for the word; the tour did not.
        assertFalse(
            FeatureTourFlow.mayStart(
                tourDone = false, studyFocusActive = false,
                hasPendingLink = true, alreadyRunning = false,
            ),
        )
    }

    @Test fun `it does not open over a study session, or twice, or again`() {
        assertFalse(FeatureTourFlow.mayStart(true, false, false, false))
        assertFalse(FeatureTourFlow.mayStart(false, true, false, false))
        assertFalse(FeatureTourFlow.mayStart(false, false, false, true))
        assertTrue(FeatureTourFlow.mayStart(false, false, false, false))
    }

    @Test fun `finishing lands where the closing card sends them`() {
        assertEquals(AppRoute.Today, FeatureTourFlow.tabAfterFinishing)
    }
}
