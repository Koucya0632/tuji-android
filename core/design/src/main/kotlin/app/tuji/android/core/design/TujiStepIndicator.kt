package app.tuji.android.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Where you are in a multi-step flow (D.10) — iOS's `TujiStepIndicator`.
 *
 * A 3dp bar cut into one segment per step: done is ink, the current one is
 * 瞳黃, the rest are paper2 — the same three marks the rest of the app uses for
 * "finished / now / not yet", so the bar needs no legend. Shared by 拍照 and
 * the first-run onboarding.
 */
@Composable
fun TujiStepIndicator(total: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(TujiBorder.Bw3),
        horizontalArrangement = Arrangement.spacedBy(TujiBorder.Bw1),
    ) {
        repeat(total) { index ->
            val fill by animateColorAsState(
                when {
                    index < current -> TujiColor.Ink
                    index == current -> TujiColor.Current
                    else -> TujiColor.Paper2
                },
                TujiMotion.ease(TujiMotion.D2),
                label = "step",
            )
            Box(Modifier.weight(1f).fillMaxSize().background(fill))
        }
    }
}
