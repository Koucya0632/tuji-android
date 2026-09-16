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
        fun of(step: TourStep, isGuest: Boolean): TourCopy = when (step.id) {
            0 -> TourCopy(
                MascotPose.Wave,
                R.string.tour_1_title,
                if (isGuest) R.string.tour_1_guest else R.string.tour_1_text,
            )
            1 -> TourCopy(
                MascotPose.Think,
                R.string.tour_2_title,
                if (isGuest) R.string.tour_2_guest else R.string.tour_2_text,
            )
            2 -> TourCopy(MascotPose.Face, R.string.tour_3_title, R.string.tour_3_text)
            3 -> TourCopy(
                MascotPose.Peek,
                R.string.tour_4_title,
                // 拍照 needs an account — the upload is authenticated — so the
                // guest line says when it becomes theirs rather than telling
                // them to go and do it now.
                if (isGuest) R.string.tour_4_guest else R.string.tour_4_text,
            )
            else -> TourCopy(
                MascotPose.Cheer,
                // The closing card used to send guests off to 「開始今天的學習」
                // — the one thing a guest cannot do. Their hero button is
                // 建立帳號，開始學習, so the tour ends on the same ask.
                if (isGuest) R.string.tour_5_guest_title else R.string.tour_5_title,
                if (isGuest) R.string.tour_5_guest else R.string.tour_5_text,
            )
        }
    }
}
