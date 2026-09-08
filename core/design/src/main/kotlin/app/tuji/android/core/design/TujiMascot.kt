package app.tuji.android.core.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * The cat, and the two numbers that place it.
 *
 * The artwork has transparent margins, so a pose cannot be laid out from its
 * frame alone — everything that sits *against* the cat (a card it leans on, a
 * contact shadow, a speech bubble) needs to know where the drawn pixels
 * actually start and stop. iOS measures that once per pose; the numbers are
 * copied here rather than re-measured, because a second measurement is a second
 * chance to disagree.
 */
enum class MascotPose(
    @DrawableRes val res: Int,
    /** Transparent margin above the artwork, as a fraction of the frame. */
    val topInset: Float,
    /** The cat's visual ground line (lowest mass), from the top of the frame. */
    val groundLine: Float,
) {
    Peek(R.drawable.mascot_peek, topInset = 0.10f, groundLine = 0.99f),
    Wave(R.drawable.mascot_wave, topInset = 0.05f, groundLine = 0.96f);

    /** Drawn height, after trimming the transparent margins. */
    val visibleHeightRatio: Float get() = groundLine - topInset
}

/**
 * The cat at [size], with its transparent margins cropped away so the frame is
 * the artwork.
 *
 * iOS spells this as negative padding. Compose rejects negative padding at
 * *runtime* — it compiles and then throws — so the same effect is a `layout`
 * modifier: measure the image at full size, report only the drawn height, and
 * place it shifted up by its own top margin.
 */
@Composable
fun MascotFigure(
    pose: MascotPose,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(pose.res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val top = (pose.topInset * placeable.height).roundToInt()
                val visible = (pose.visibleHeightRatio * placeable.height).roundToInt()
                layout(placeable.width, visible) { placeable.place(0, -top) }
            },
    )
}
