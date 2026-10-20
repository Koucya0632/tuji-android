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

    @Test fun `it is four steps, all on today`() {
        val steps = FeatureTourFlow().steps
        assertEquals(4, steps.size)
        assertTrue(steps.all { it.tab == AppRoute.Today })
    }

    @Test fun `it walks the hero buttons, the cat, then capture`() {
        val steps = FeatureTourFlow().steps
        assertEquals(TourTarget.HeroCtas, steps[0].target)
        assertEquals(TourTarget.Assistant, steps[1].target)
        assertEquals(TourTarget.Capture, steps[2].target)
    }

    @Test fun `the closing step has no hole to cut`() {
        val last = FeatureTourFlow().steps.last()
        assertNull(last.target)
        assertNull(last.fallback)
    }

    @Test fun `capture is framed round`() {
        // The eye is the one circle in the app.
        val steps = FeatureTourFlow().steps
        assertEquals(TourCutoutShape.Pill, steps.single { it.target == TourTarget.Capture }.shape)
    }

    @Test fun `advancing within a tab does not switch tabs`() {
        val flow = FeatureTourFlow()
        assertEquals(TourAdvance.Show(1), flow.advance(from = 0, showing = AppRoute.Today))
    }

    /**
     * The cross-tab branch is kept for a script that needs it; the shipped one
     * does not, so it is pinned against a two-tab fixture.
     */
    private val crossTab = FeatureTourFlow(
        listOf(
            TourStep(0, AppRoute.Today, TourTarget.HeroCtas, null, TourCutoutShape.Pill),
            TourStep(1, AppRoute.Atlas, null, null, TourCutoutShape.Square),
        ),
    )

    @Test fun `a step on another tab says so`() {
        assertEquals(TourAdvance.CrossTab(AppRoute.Atlas, 1), crossTab.advance(from = 0, showing = AppRoute.Today))
    }

    @Test fun `a step already on the showing tab is not a cross-tab`() {
        // Somebody who swiped mid-tour is already there.
        assertEquals(TourAdvance.Show(1), crossTab.advance(from = 0, showing = AppRoute.Atlas))
    }

    @Test fun `advancing past the end finishes`() {
        val flow = FeatureTourFlow()
        assertEquals(TourAdvance.Finish, flow.advance(from = flow.steps.lastIndex, showing = AppRoute.Today))
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

    @Test fun `finishing opens new words, which is what the closing card offers`() {
        assertEquals(AppRoute.Today, FeatureTourFlow.tabAfterFinishing)
        assertEquals(AppRoute.LearnNew, FeatureTourFlow.routeAfterFinishing)
    }
}

class TabTipTest {
    private fun shows(
        tab: AppRoute.Tab,
        armed: Boolean = true,
        seen: Set<String> = emptySet(),
        atRoot: Boolean = true,
        tourRunning: Boolean = false,
        studyFocusActive: Boolean = false,
    ) = TabTip.shows(tab, armed, seen, atRoot, tourRunning, studyFocusActive)

    @Test fun `every tab but today has a tip, keyed as on iOS`() {
        assertNull(TabTip.key(AppRoute.Today))
        assertEquals("cards", TabTip.key(AppRoute.Atlas))
        assertEquals("community", TabTip.key(AppRoute.Community))
        assertEquals("me", TabTip.key(AppRoute.Me))
    }

    @Test fun `a fresh tab at its root shows its tip`() {
        assertTrue(shows(AppRoute.Atlas))
        assertFalse(shows(AppRoute.Today))
    }

    @Test fun `unarmed, seen or busy keeps it hidden`() {
        // Somebody who took the old tour never armed them.
        assertFalse(shows(AppRoute.Atlas, armed = false))
        assertFalse(shows(AppRoute.Atlas, seen = setOf("cards")))
        assertFalse(shows(AppRoute.Atlas, atRoot = false))
        assertFalse(shows(AppRoute.Atlas, tourRunning = true))
        assertFalse(shows(AppRoute.Atlas, studyFocusActive = true))
    }
}
