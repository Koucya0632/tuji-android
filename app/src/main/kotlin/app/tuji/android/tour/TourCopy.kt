package app.tuji.android.tour

import androidx.annotation.StringRes
import app.tuji.android.R
import app.tuji.android.core.design.MascotPose

/**
 * What each step says, and which cat says it.
 *
 * Apart from [FeatureTourFlow] because that has to be testable without
 * resources, and together in one place because the alternative — copy inlined
 * at five call sites — is how iOS's third step managed to be **wrong four
 * times**: it listed four tabs and missed 物見; it was fixed to add 物見 but
 * kept three tab names that no longer existed; the count went stale the day
 * 拍照 moved into the bar; and then it said 「中間的黃色按鈕」 the day 拍照
 * stopped being in the middle.
 *
 * So this copy neither counts them nor points at them. It names what each one
 * is *for* and lets the cutout say where — and there is still nothing that
 * makes a divergence fail to compile.
 */
data class TourCopy(
    val pose: MascotPose,
    @StringRes val title: Int,
    @StringRes val text: Int,
) {
    companion object {
        fun of(step: TourStep): TourCopy = when (step.id) {
            0 -> TourCopy(MascotPose.Wave, R.string.tour_1_title, R.string.tour_1_text)
            1 -> TourCopy(MascotPose.Think, R.string.tour_2_title, R.string.tour_2_text)
            2 -> TourCopy(MascotPose.Face, R.string.tour_3_title, R.string.tour_3_text)
            3 -> TourCopy(MascotPose.Peek, R.string.tour_4_title, R.string.tour_4_text)
            else -> TourCopy(MascotPose.Cheer, R.string.tour_5_title, R.string.tour_5_text)
        }
    }
}
