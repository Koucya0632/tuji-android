package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * How far along something is, as a rule of ink.
 *
 * A rectangle at the selection weight, not a rounded Material bar: 紙與墨 has
 * one way to draw "this far", and it is the same 3dp the study header uses.
 *
 * The colours are arguments because the same bar sits on two grounds. On paper
 * the track is [TujiColor.Paper3]; on the ink hero it is paper at 20%, and the
 * fill switches to the **pale** accumulation step — the deep teal only reaches
 * 3.04:1 against ink while the pale one reaches 13.58:1 and carries the same
 * meaning.
 */
@Composable
fun TujiProgressBar(
    progress: Double,
    modifier: Modifier = Modifier,
    track: Color = TujiColor.Paper3,
    fill: Color = TujiColor.Current,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TujiBorder.Bw3)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(fill),
        )
    }
}
