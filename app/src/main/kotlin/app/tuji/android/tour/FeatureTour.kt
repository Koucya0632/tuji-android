package app.tuji.android.tour

import app.tuji.android.AppRoute

/** What a step points at. Screens mark themselves with `Modifier.tourAnchor`. */
enum class TourTarget {
    /** The whole hero card on 今日 — the fallback when the CTA pair is not in the tree. */
    Hero,

    /** The 複習/學新字 pair inside the hero. */
    HeroCtas,

    /** The cat on the 今日 hero, which is the way into 助手. */
    Assistant,

    /** 拍照, in the bar. */
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
 * The first-run tour, as decisions rather than as state on the shell.
 *
 * Kept apart from the overlay for the reason iOS gives: an index machine that
 * only exists inside a tab shell can only be exercised by launching the app.
 * [steps] is a parameter so the cross-tab branch can still be pinned now that
 * the shipped script never leaves 今日.
 */
class FeatureTourFlow(val steps: List<TourStep> = SCRIPT) {

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
         * skipped. The closing card's 開始學習 opens 學新字, which lives on
         * 今日 — so finishing goes there and skipping stays put.
         */
        val tabAfterFinishing: AppRoute.Tab = AppRoute.Today

        /** What 開始學習 on the closing card opens. */
        val routeAfterFinishing: AppRoute = AppRoute.LearnNew

        /**
         * Only what a new user would not find alone, all on 今日 — iOS's
         * `TourStep.steps`.
         *
         * The tab bar and the daily goal are no longer steps. The bar names its
         * own tabs, and its step was wrong four times as they changed; each tab
         * now introduces itself on the first visit (`TabTip`). The goal reads 0
         * on day one and explains itself after the first lesson.
         */
        val SCRIPT: List<TourStep> = listOf(
            TourStep(
                id = 0,
                tab = AppRoute.Today,
                target = TourTarget.HeroCtas,
                fallback = TourTarget.Hero,
                shape = TourCutoutShape.Pill,
            ),
            TourStep(
                id = 1,
                tab = AppRoute.Today,
                // The cat gives no sign that it can be pressed — the speech
                // bubble beside it is the only hint — which makes it the one
                // thing on 今日 a new user is least likely to find.
                target = TourTarget.Assistant,
                fallback = null,
                shape = TourCutoutShape.Square,
            ),
            TourStep(
                id = 2,
                tab = AppRoute.Today,
                target = TourTarget.Capture,
                fallback = null,
                // Round, because what it frames is: the eye is the one circle
                // in the app.
                shape = TourCutoutShape.Pill,
            ),
            TourStep(
                id = 3,
                tab = AppRoute.Today,
                target = null,
                fallback = null,
                shape = TourCutoutShape.Square,
            ),
        )
    }
}
