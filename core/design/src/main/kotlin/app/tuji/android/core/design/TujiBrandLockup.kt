package app.tuji.android.core.design

import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Where the mark is in its entrance. Mirrors iOS's `TujiBrandLockup.Entrance`.
 *
 * [Start] exists for exactly one caller: the thing that renders the native
 * launch image. It is the frame the window shows *before* Compose has drawn
 * anything, so it has to come from this composable rather than be drawn by
 * hand — a hand-drawn approximation is a visible jump at the handover, in the
 * one place in the app where nothing else is happening to hide it.
 */
enum class LockupEntrance {
    /** The settled mark. A letterhead. */
    Finished,

    /**
     * The cat leaning out of the hole, once, on the screen that is the first
     * thing anyone sees. Off everywhere else: the mark on 歡迎 is a letterhead,
     * and a letterhead that performs every time the screen appears is a
     * different thing from a launch.
     */
    Animated,

    /** Frozen on the first frame: hole shut, cat still inside it. */
    Start,
}

/**
 * 「Tuji.」 with the cat behind it — the app's mark, and the first thing anyone
 * sees.
 *
 * Ported from `Tuji/Components/TujiBrandLockup.swift`. Only one piece of it is
 * an image (`mascot-peek`); the portal and the card are drawn, so the numbers
 * are copied rather than re-derived: an ellipse of 176×48 at a specific offset
 * is artwork, and "close enough" is visible.
 *
 * **This is where the radius rule has its exception.** [TujiRadius] is zero
 * everywhere in the app, but the wordmark card is 24dp round on both platforms
 * — because it is a drawn mark, not a UI surface. The same applies to the ink
 * plate behind it, which is a *drawn* shadow rather than an elevation.
 */
@Composable
fun TujiBrandLockup(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    entrance: LockupEntrance = LockupEntrance.Finished,
) {
    // peek: visibleHeightRatio 0.89 × 150dp cat, less a 16dp overlap, is how far
    // down the card sits — the paws have to land *on* it.
    val catSize = 150.dp
    val lift = catSize * MascotPose.Peek.visibleHeightRatio - 16.dp

    // Timed off `TujiBrandLockup.playEntranceIfNeeded()` on iOS, beat for beat:
    // hold the native launch frame, open the hole, wait, then let the cat up on
    // a spring that overshoots by 0.28. The pause between the two is the part
    // that makes it read as a cat coming *out of* something rather than two
    // things fading in together.
    val reduceMotion = rememberReduceMotion()
    // 移除動畫 resolves Animated straight to the finished mark, the way iOS's
    // `entranceFinished` does — the entrance is skipped, not slowed.
    val settled = when (entrance) {
        LockupEntrance.Finished -> true
        LockupEntrance.Animated -> reduceMotion
        LockupEntrance.Start -> false
    }
    val entered = entrance == LockupEntrance.Animated && !reduceMotion
    val hole = remember { Animatable(if (settled) 1f else 0f) }
    val rise = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(entered) {
        if (!entered) return@LaunchedEffect
        delay(HOLE_DELAY)
        hole.animateTo(1f, tween(HOLE_MS, easing = TujiMotion.EaseOut))
        delay(RISE_DELAY)
        rise.animateTo(1f, TujiMotion.spring(RISE_SECONDS, RISE_BOUNCE))
    }

    Box(
        modifier.size(width = 232.dp * scale, height = 230.dp * scale),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                // `requiredSize`, not `size`: the outer frame is already the
                // scaled-down box, so a plain `size` gets clamped to it *before*
                // graphicsLayer scales — which shrank the card twice and left it
                // at 179dp against iOS's 195pt.
                .requiredSize(width = 232.dp, height = 230.dp)
                // The whole mark scales, not just the box around it. Sizing the
                // outer frame alone left every inner dimension at full size —
                // the card measured 204dp against iOS's 195pt, which is the
                // kind of "nearly right" that only a measurement finds.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Portal(
                Modifier
                    .offset(y = lift - 30.dp)
                    .graphicsLayer {
                        // Widthways first and further: a hole opening is wider
                        // than it is taller.
                        scaleX = 0.58f + 0.42f * hole.value
                        scaleY = 0.72f + 0.28f * hole.value
                    },
            )

            // Clipped to where the hole is, the way iOS frames the figure at
            // `lift + 17` and clips. Without it the cat is drawn across the
            // wordmark card on its way up, because the rise is a translation
            // and nothing above it clips.
            Box(
                Modifier
                    .width(catSize)
                    .height(lift + 17.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                MascotFigure(
                    pose = MascotPose.Peek,
                    size = catSize,
                    modifier = Modifier.graphicsLayer {
                        // The spring overshoots past 1, which is the whole
                        // point of it — but an alpha above 1 is not a brighter
                        // cat, it is undefined.
                        alpha = rise.value.coerceIn(0f, 1f)
                        // Anchored at the feet, so it grows *out of* the hole
                        // rather than towards it from the middle.
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = 0.82f + 0.18f * rise.value
                        translationY = (1f - rise.value) * (lift + 10.dp).toPx()
                    },
                )
            }

            WordmarkCard(Modifier.offset(y = lift))
        }
    }
}

/** The hole the cat leans out of. */
@Composable
private fun Portal(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 176.dp, height = 48.dp)
            .drawBehind {
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(
                            TujiColor.BrandSecondary.copy(alpha = 0.78f),
                            TujiColor.BrandSecondary,
                        )
                    ),
                    size = Size(size.width, size.height),
                    style = Fill,
                )
            }
    )
}

@Composable
private fun WordmarkCard(modifier: Modifier = Modifier) {
    // The one place GenSenRounded's own Latin is the face we *want*.
    //
    // iOS sets the wordmark in `Font.system(size: 54, weight: .black, design:
    // .rounded)` — SF Rounded, which Android has no equivalent of; Roboto is
    // not round and Plus Jakarta is not either. GenSenRounded's Latin is a
    // rounded Source Sans derivative, and it is already bundled. Everywhere
    // else in the app it is the wrong answer and ADR-0003 exists to keep it out
    // (see TujiTypeface); a drawn logotype is the exception, because here the
    // face *is* the artwork rather than a step on a scale.
    val rounded = remember { FontFamily(Font(R.font.gensenrounded2tw_b)) }

    Box(modifier.size(width = 224.dp, height = 78.dp), contentAlignment = Alignment.Center) {
        // A drawn plate, not an elevation. 紙與墨 has no shadow token; this is
        // part of the mark.
        Box(
            Modifier
                .offset(y = 5.dp)
                .size(width = 220.dp, height = 76.dp)
                .background(TujiColor.Ink.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
        )
        Box(
            Modifier
                .size(width = 224.dp, height = 78.dp)
                .background(TujiColor.BrandSecondary, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tuji",
                    fontFamily = rounded,
                    fontSize = 54.sp,
                    letterSpacing = (-2.5).sp,
                    color = TujiColor.BrandPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    ".",
                    fontFamily = rounded,
                    fontSize = 54.sp,
                    letterSpacing = (-2.5).sp,
                    color = TujiColor.Alert,
                )
            }
        }
    }
}

/** Long enough for the screen to be there before anything moves on it. */
private const val HOLE_DELAY = 70L

/** iOS: `withAnimation(.easeOut(duration: 0.14)) { holeOpen = true }`. */
private const val HOLE_MS = 140

/** iOS sleeps 100ms between the hole and the cat. */
private const val RISE_DELAY = 100L

/** iOS: `.spring(duration: 0.34, bounce: 0.28)`. */
private const val RISE_SECONDS = 0.34f
private const val RISE_BOUNCE = 0.28f
