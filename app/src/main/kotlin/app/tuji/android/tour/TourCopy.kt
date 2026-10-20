package app.tuji.android.tour

import androidx.annotation.StringRes
import app.tuji.android.R
import app.tuji.android.core.design.MascotPose

/**
 * What each step says, and which cat says it.
 *
 * Apart from [FeatureTourFlow] because that has to be testable without
 * resources, and together in one place because copy inlined at each call
 * site drifts: the old tab-bar step was wrong four times as tabs moved, which
 * is why it is gone and each tab now introduces itself (`TabTip`).
 */
data class TourCopy(
    val pose: MascotPose,
    @StringRes val title: Int,
    @StringRes val text: Int,
) {
    companion object {
        fun of(step: TourStep): TourCopy = when (step.id) {
            0 -> TourCopy(MascotPose.Wave, R.string.tour_1_title, R.string.tour_1_text)
            1 -> TourCopy(MascotPose.Face, R.string.tour_2_title, R.string.tour_2_text)
            2 -> TourCopy(MascotPose.Peek, R.string.tour_3_title, R.string.tour_3_text)
            else -> TourCopy(MascotPose.Cheer, R.string.tour_4_title, R.string.tour_4_text)
        }
    }
}
