package app.tuji.android.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What the app is busy with, which decides the mark and its tint. */
enum class TujiStatusKind {
    /** The AI is looking at a photograph. 瞳黃, because it is 現在. */
    Working,

    /** Something is being taken away. 警示, because it cannot be taken back. */
    Removing;

    internal val tint: Color get() = when (this) {
        Working -> TujiColor.Current
        Removing -> TujiColor.Alert
    }
}

/**
 * Work the user must wait for, with the screen underneath sealed off.
 *
 * A port of iOS's `TujiStatusToast`, down to the 136pt square, the 28pt
 * corners, the 48pt tinted disc, the 58pt sweeping ring at 2.75pt, the 7pt
 * 瞳黃 dot pinned at (18, −15), and the `.spring(duration: 0.24, bounce: 0.12)`
 * it arrives on from 0.94 scale.
 *
 * Two things cannot be copied exactly and are named here rather than left to be
 * discovered. iOS lays `.regularMaterial` over 紙 at 0.86 alpha; Compose has no
 * backdrop blur at all, and `Modifier.blur` — which blurs a composable's own
 * content, not what is behind it — is API 31 against a minSdk of 29 anyway. So
 * the material degrades to the 紙 underneath it, which is the colour it was
 * tinting. And iOS's corners are `.continuous`, a squircle Compose's
 * `RoundedCornerShape` does not have.
 *
 * The point is still the sealing. Android's version of these moments said
 * 刪除中… on the button that started them and left the rest of the page live:
 * you could keep selecting cards while the ones already chosen were being
 * deleted. A label is not a lock.
 */
@Composable
fun TujiStatusBlocker(visible: Boolean, title: String, detail: String, kind: TujiStatusKind = TujiStatusKind.Working) {
    // Kept mounted through the exit, so leaving is animated the way arriving
    // is — iOS's `.animation(value: isPresented)` runs in both directions.
    var present by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) { if (visible) present = true }
    if (!present) return

    // A window rather than a Box over the caller: an overlay drawn inside a
    // screen leaves the shell's back arrow and the tab bar above it live, which
    // is most of what this is for. Dismissal is deliberately a no-op — a status
    // that back closes is a label again.
    TujiWindow(onDismiss = {}) {
        StatusSurface(visible, title, detail, kind) { present = false }
    }
}

@Composable
private fun StatusSurface(
    visible: Boolean,
    title: String,
    detail: String,
    kind: TujiStatusKind,
    onHidden: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val shown = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(visible, reduceMotion) {
        val target = if (visible) 1f else 0f
        if (reduceMotion) shown.snapTo(target) else shown.animateTo(target, TujiMotion.spring(ENTER_SECONDS, ENTER_BOUNCE))
        if (!visible) onHidden()
    }

    Box(
        Modifier
            .fillMaxSize()
            // 16%, not the 40% a prompt uses: this is not asking anything, so
            // it should not put the page as far away as a question does.
            .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
            .background(TujiColor.Ink.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val scale = 0.94f + 0.06f * shown.value
                    scaleX = scale
                    scaleY = scale
                }
                .size(CARD)
                // **Two of iOS's layers rendered as one.** There, 紙 at 0.86
                // sits under `.regularMaterial`, and the material is itself
                // most of the way to opaque — so the card a reader sees is
                // ~0.96, not 0.86. Drawing only the base left a translucent
                // square with the photograph showing through it, which is not
                // what that stack looks like on either platform.
                .background(TujiColor.Paper.copy(alpha = CARD_ALPHA), RoundedCornerShape(CORNER))
                .border(1.dp, Color.White.copy(alpha = 0.74f), RoundedCornerShape(CORNER))
                .padding(horizontal = TujiSpace.S2)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusMark(kind, reduceMotion)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title,
                    style = TujiType.bodyStrong,
                    color = TujiColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    detail,
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The disc, the ring, the glyph and the dot.
 *
 * The ring is a sweep from fully transparent to the tint, turning once every
 * 1.1s — SF Symbols has nothing like it, so iOS draws it by hand too. Under
 * 移除動畫 it is not slowed down, it is gone: a ring that turns forever is the
 * definition of motion nobody asked for.
 */
@Composable
private fun StatusMark(kind: TujiStatusKind, reduceMotion: Boolean) {
    val tint = kind.tint
    val spin = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "statusSpin")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(SPIN_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "statusSpinAngle",
        )
        value
    }
    // iOS's `.variableColor.iterative.nonReversing` on the sparkles, and
    // `.pulse` on the trash: the same clock, read two ways.
    val beat = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "statusBeat")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(BEAT_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "statusBeatPhase",
        )
        value
    }

    Box(Modifier.size(RING), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(RING)) {
            val w = size.width
            drawCircle(tint.copy(alpha = 0.12f), radius = DISC_FRACTION * w / 2f)
            if (!reduceMotion) {
                rotate(spin) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to tint.copy(alpha = 0f),
                            1f to tint,
                            center = Offset(w / 2f, w / 2f),
                        ),
                        radius = (w - RING_STROKE.toPx()) / 2f,
                        style = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        when (kind) {
            TujiStatusKind.Working -> TujiGlyph.Sparkles(size = GLYPH, tint = tint) { index ->
                // Each star lights in turn and none of them goes fully dark,
                // which is what "iterative, non-reversing" looks like.
                val lead = (beat.toInt() % 3)
                if (index == lead) 1f else 0.35f
            }
            TujiStatusKind.Removing -> TujiGlyph.Trash(
                size = GLYPH,
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    // .pulse: one breath per beat, never all the way out.
                    val t = beat % 1f
                    alpha = 0.55f + 0.45f * kotlin.math.abs(1f - 2f * t)
                },
            )
        }
        Box(
            Modifier
                .offset(x = 18.dp, y = (-15).dp)
                .size(7.dp)
                .background(TujiColor.Current, RoundedCornerShape(50)),
        )
    }
}

/** iOS: `.frame(width: 136, height: 136)` with 28pt continuous corners. */
private val CARD = 136.dp
private val CORNER = 28.dp

/** 紙 at 0.86 under a material that is itself ~0.75 opaque: 1 − 0.14 × 0.25. */
private const val CARD_ALPHA = 0.96f

/** iOS: a 48pt disc inside a 58pt ring stroked at 2.75pt, glyph at 18pt. */
private val RING = 58.dp
private val RING_STROKE = 2.75.dp
private val GLYPH = 18.dp
private const val DISC_FRACTION = 48f / 58f

/** iOS: `.linear(duration: 1.1).repeatForever(autoreverses: false)`. */
private const val SPIN_MS = 1100

/** Three steps of the variable-colour cycle, at the ring's own pace. */
private const val BEAT_MS = 1100

/** iOS: `.spring(duration: 0.24, bounce: 0.12)`, from `.scale(0.94)`. */
private const val ENTER_SECONDS = 0.24f
private const val ENTER_BOUNCE = 0.12f
