package app.tuji.android.tour

import app.tuji.android.AppRoute

/** What a step points at. Screens mark themselves with `Modifier.tourAnchor`. */
enum class TourTarget {
    /** The whole hero card on 今日 — the guest fallback, who has no CTA pair. */
    Hero,

    /** The 複習/學新字 pair inside the hero. Signed in only. */
    HeroCtas,

    /** The daily-goal block inside the hero. Signed in only. */
    DailyGoal,

    /** The streak chip in 今日's top row. */
    Streak,

    /** The ink tab bar. */
    TabBar,

    /** 拍照, in the middle of the bar. */
    Capture,
}

/** How the hole is cut. */
enum class TourCutoutShape {
    /** The app's usual zero radius. */
    Square,

    /** Half the cutout's height — for the one circle in the app, and for pills. */
    Pill,
}

/** One step, without its words. The copy lives in `TourCopy`, which has `R`. */
data class TourStep(
    val id: Int,
    val tab: AppRoute.Tab,
    /** Null for the closing step: no hole, and the card is centred. */
    val target: TourTarget?,
    /** Second choice when the target is not in the tree. */
    val fallback: TourTarget?,
    val shape: TourCutoutShape,
)

/** What advancing means, once. */
sealed interface TourAdvance {
    /** The next step is on the tab already showing. */
    data class Show(val index: Int) : TourAdvance

    /** The next step lives elsewhere: switch, let the pager settle, then reveal. */
    data class CrossTab(val to: AppRoute.Tab, val index: Int) : TourAdvance

    data object Finish : TourAdvance
}

/**
 * The five-step first-run tour, as decisions rather than as state on the shell.
 *
 * Kept apart from the overlay for the reason iOS gives: an index machine that
 * only exists inside a tab shell can only be exercised by launching the app,
 * and the one branch worth testing — "the next step lives on another tab" — is
 * exactly the one that is hardest to reach that way.
 */
class FeatureTourFlow(isGuest: Boolean) {

    val steps: List<TourStep> = listOf(
        TourStep(
            id = 0,
            tab = AppRoute.Today,
            target = if (isGuest) TourTarget.Hero else TourTarget.HeroCtas,
            fallback = TourTarget.Hero,
            shape = if (isGuest) TourCutoutShape.Square else TourCutoutShape.Pill,
        ),
        TourStep(
            id = 1,
            tab = AppRoute.Today,
            target = if (isGuest) TourTarget.Streak else TourTarget.DailyGoal,
            fallback = TourTarget.Streak,
            shape = if (isGuest) TourCutoutShape.Pill else TourCutoutShape.Square,
        ),
        TourStep(
            id = 2,
            tab = AppRoute.Today,
            target = TourTarget.TabBar,
            fallback = null,
            // Squared: what it frames is the ink slab, a rectangle with the
            // app's usual zero radius.
            shape = TourCutoutShape.Square,
        ),
        TourStep(
            id = 3,
            // Stays on 今日: 拍照 is in the bar, so it is on screen whichever
            // tab is showing.
            tab = AppRoute.Today,
            target = TourTarget.Capture,
            fallback = null,
            // Round, because what it frames is: the eye is the one circle in
            // the app.
            shape = TourCutoutShape.Pill,
        ),
        TourStep(
            id = 4,
            tab = AppRoute.Atlas,
            target = null,
            fallback = null,
            shape = TourCutoutShape.Square,
        ),
    )

    /**
     * What happens when 下一步 is tapped on [index], given the tab on screen.
     */
    fun advance(from: Int, showing: AppRoute.Tab): TourAdvance {
        val next = from + 1
        if (next >= steps.size) return TourAdvance.Finish
        val step = steps[next]
        return if (step.tab == showing) TourAdvance.Show(next) else TourAdvance.CrossTab(step.tab, next)
    }

    companion object {
        /**
         * May the tour open at all?
         *
         * Checked **after** the launch beat, not before: all of these can
         * change while the shell waits, and the one that bites is a deep link
         * — somebody who asked for a word should get the word, because they
         * asked and the tour did not.
         */
        fun mayStart(
            tourDone: Boolean,
            studyFocusActive: Boolean,
            hasPendingLink: Boolean,
            alreadyRunning: Boolean,
        ): Boolean = !tourDone && !studyFocusActive && !hasPendingLink && !alreadyRunning

        /**
         * Where the shell lands when the tour *finishes* rather than being
         * skipped. The closing card invites the reader to start today's study,
         * which lives on 今日 — so finishing goes there and skipping stays put,
         * which is the only difference between the two.
         */
        val tabAfterFinishing: AppRoute.Tab = AppRoute.Today
    }
}
