package app.tuji.android.core.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    Wave(R.drawable.mascot_wave, topInset = 0.05f, groundLine = 0.96f),

    /** Asking something. What a confirmation prompt shows. */
    Think(R.drawable.mascot_think, topInset = 0.05f, groundLine = 0.95f),

    /** Curled up. Every "nothing here yet". */
    Sleep(R.drawable.mascot_sleep, topInset = 0.28f, groundLine = 0.86f),

    /** Just the head — the avatar for somebody who has not set one. */
    Face(R.drawable.mascot_face, topInset = 0.06f, groundLine = 0.82f),

    /** 今日目標達成. The one pose that only appears when something went right. */
    Cheer(R.drawable.mascot_cheer, topInset = 0.06f, groundLine = 0.95f);

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

/**
 * The mascot's eye, as a mark: 瞳黃 iris, ink pupil, one catchlight.
 *
 * iOS draws the tab bar's 拍照 button this way — round where everything around
 * it is square, which is what separates it from the four tabs beside it. The
 * proportions are iOS's, measured off `mascot-face.png`: the pupil is seven
 * tenths of the eye, and the catchlight sits high and outboard.
 */
@Composable
fun MascotEye(size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val centre = Offset(w / 2f, this.size.height / 2f)
        drawCircle(TujiColor.BrandPrimary, radius = w / 2f, center = centre)
        drawCircle(TujiColor.Ink, radius = w * 0.35f, center = centre)
        drawCircle(
            TujiColor.Paper,
            radius = w * 0.10f,
            center = Offset(centre.x + w * 0.12f, centre.y - w * 0.14f),
        )
    }
}

/**
 * Nothing here yet: the sleeping cat, a title, and an optional second line.
 *
 * iOS's `MascotEmptyState`. It replaces a single grey sentence, which on a tall
 * screen reads as the page failing to finish rather than as an answer.
 */
@Composable
fun MascotEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    pose: MascotPose = MascotPose.Sleep,
    compact: Boolean = false,
) {
    Column(
        modifier.widthIn(max = 280.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MascotFigure(pose = pose, size = if (compact) 64.dp else 88.dp)
        Text(
            title,
            style = TujiType.h3,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (compact) TujiSpace.S3 else TujiSpace.S4),
        )
        message?.let {
            Text(
                it,
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TujiSpace.S2),
            )
        }
    }
}

/**
 * Where a full-page empty state sits: its top at 35% of the space, not centred.
 * A centred state lands under the thumb and reads lower than centre, because
 * the eye weights the top of a page. iOS's `tujiEmptyStatePlacement`.
 */
@Composable
fun EmptyStatePlacement(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = maxOf(TujiSpace.S5, maxHeight * 0.35f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            content()
        }
    }
}

/**
 * Something failed: a small 警示 square, a title, the reason, and a way to try
 * again. No cat — a mascot waving at a failure is flippant, which is why iOS
 * keeps it out of error states.
 */
@Composable
fun TujiErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.widthIn(max = 280.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(32.dp).background(TujiColor.Alert))
        Text(
            title,
            style = TujiType.h3,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TujiSpace.S4),
        )
        message?.let {
            Text(
                it,
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TujiSpace.S2),
            )
        }
        Box(Modifier.padding(top = TujiSpace.S4)) { actions() }
    }
}

/**
 * The cat, saying one line — iOS's `MascotSpeechBubble`.
 *
 * No drawn tail: the pill tucks under the cat, which reads as "this one is
 * talking" with one shape fewer. Kept for the few moments the cat is allowed
 * to speak — the start of a lesson, a wrong answer — not as a narrator.
 */
@Composable
fun MascotSpeechBubble(pose: MascotPose, text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 56.dp, height = 64.dp).zIndex(1f), contentAlignment = Alignment.Center) {
            MascotFigure(pose = pose, size = 64.dp)
        }
        Text(
            text,
            style = TujiType.body,
            color = TujiColor.Ink,
            modifier = Modifier
                .offset(x = (-12).dp)
                .background(TujiColor.Paper2, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .heightIn(min = 44.dp)
                .padding(start = TujiSpace.S4, end = TujiSpace.S3, top = TujiSpace.S2, bottom = TujiSpace.S2),
        )
    }
}
