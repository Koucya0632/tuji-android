package app.tuji.android.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.tuji.android.core.study.StudyOptionState

/**
 * The colours for one option row.
 *
 * The *decision* is `StudyOptionState` in `core:study`, where it is tested;
 * this is only what each verdict looks like.
 *
 * Ink inversion and a 3dp edge are **shape** differences, not colour-only ones,
 * so the language survives colour blindness. 積累 marks the right one rather
 * than a new green: the palette has six meanings and none of them is green.
 */
val StudyOptionState.ground: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Ink
        else -> TujiColor.Paper2
    }

val StudyOptionState.foreground: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Paper
        StudyOptionState.Dim -> TujiColor.Ink2
        else -> TujiColor.Ink
    }

val StudyOptionState.letterGround: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Current
        else -> TujiColor.Paper3
    }

val StudyOptionState.letterForeground: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Ink
        else -> TujiColor.Ink2
    }

/**
 * Which row the user tapped, and how it went. [StudyOptionState.Answer]
 * deliberately has none: it is the answer they did *not* pick, and the ink
 * ground already says so — a frame there would claim a tap that never happened.
 */
val StudyOptionState.borderColor: Color?
    get() = when (this) {
        StudyOptionState.Right -> TujiColor.Accumulation
        StudyOptionState.Wrong -> TujiColor.Alert
        else -> null
    }

/** The unrelated options recede rather than disappear — the user may reread them. */
val StudyOptionState.dimAlpha: Float
    get() = if (this == StudyOptionState.Dim) 0.4f else 1f

@Composable
fun StudyOptionRow(
    label: String,
    letter: String,
    state: StudyOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescriptionText: String? = null,
) {
    val shape = RoundedCornerShape(TujiRadius.R0)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Two independent signals, deliberately: the **ground** says which one is
    // the answer (ink inversion), the **frame** says which one the user tapped.
    // Under the finger an unanswered row takes 紙3 — the app changes the ground
    // rather than casting a shadow, and this row was the last control that
    // answered a press with nothing at all.
    val ground = if (pressed && state == StudyOptionState.Idle) TujiColor.Paper3 else state.ground

    // **Every colour on the same spec, not just the ground.** Half-animating
    // the reveal is worse than not animating it: the ink arrives over 350ms
    // while 紙 text snaps to 紙 in the first frame, and for that frame the
    // label is invisible against its own ground.
    //
    // Which spec depends on what moved. iOS animates the recolour on the
    // question's *phase* with `.spring(duration: 0.35)`; a press is not a phase
    // change and stays on the D1 state step. The two never overlap, because the
    // press ground only exists while the row is still Idle — which is also why
    // reading the state is enough to tell them apart.
    val recolour: FiniteAnimationSpec<Color> = if (state == StudyOptionState.Idle) {
        TujiMotion.ease(TujiMotion.D1)
    } else {
        TujiMotion.spring(PICK_SECONDS)
    }
    val groundNow by animateColorAsState(ground, recolour, label = "optionGround")
    val inkNow by animateColorAsState(state.foreground, recolour, label = "optionInk")
    val letterGroundNow by animateColorAsState(
        state.letterGround,
        recolour,
        label = "optionLetterGround",
    )
    val letterInkNow by animateColorAsState(
        state.letterForeground,
        recolour,
        label = "optionLetterInk",
    )
    // Ruling an option out does not move the question's phase, so without this
    // the alert frame would snap in with no motion at all.
    //
    // The *alpha* animates and the hue does not. Fading between two colours
    // would take 積累 out of a transparent 警示紅 and show red on its way to
    // teal; holding the target hue and raising it from nothing is the same
    // arrival with nothing borrowed from the other verdict.
    val borderHue = state.borderColor ?: TujiColor.Alert
    val borderAlpha by animateFloatAsState(
        if (state.borderColor != null) 1f else 0f,
        TujiMotion.ease(TujiMotion.D1),
        label = "optionBorder",
    )
    val dimNow by animateFloatAsState(
        state.dimAlpha,
        if (state == StudyOptionState.Idle) {
            TujiMotion.ease(TujiMotion.D1)
        } else {
            TujiMotion.spring(PICK_SECONDS)
        },
        label = "optionDim",
    )

    val nudge = rememberWrongPickShake(state)

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = nudge.value.dp.toPx() }
            .alpha(dimNow)
            .defaultMinSize(minHeight = 64.dp)
            .background(groundNow, shape)
            .border(TujiBorder.Bw3, borderHue.copy(alpha = borderAlpha), shape)
            .tujiClickable(enabled = enabled, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = TujiSpace.S3)
            .semantics { stateDescriptionText?.let { stateDescription = it } },
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(letterGroundNow, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = TujiType.label, color = letterInkNow)
        }
        // h3, as iOS sets it: the option *is* the word being recalled, and at
        // body size a 長い外来語 was the smallest thing on a card whose whole
        // question is which of these four it is.
        Text(label, style = TujiType.h3, color = inkNow)
    }
}

/**
 * A wrong pick shakes once, the moment it happens.
 *
 * The frame is a *state*: it says "this one is out" for as long as the question
 * lasts, and a state that is simply there is easy to not notice arriving —
 * especially in 複習, where the picked row is the only thing on screen that
 * changed and the question otherwise carries on exactly as before. The shake is
 * the *event*.
 *
 * **Keyed on entering [StudyOptionState.Wrong]**, so the rows ruled out earlier
 * hold still when the answer finally lands; re-shaking them would report old
 * mistakes as if they had just been made. A row that arrives already wrong —
 * the same question recomposed after a rotation — never shakes either, for the
 * same reason: nothing just happened.
 *
 * **One timeline, not four hops.** iOS reached this shape by measuring: a
 * phase-based version animates *to* a phase and then waits to be told the next
 * one, so the gap lands exactly where the motion has stopped — at the extreme —
 * and it read as a sideways jump that parks, followed by a shake. Each leg here
 * runs straight into the next, 195ms end to end. Linear for the throws (at
 * 50–60ms a leg there is nothing to ease), and only the settle is eased,
 * because coming to rest is the one part slow enough to see.
 *
 * Deliberately not [TujiMotion] tokens: that scale is the three durations the
 * whole app moves at, and this is one component's error feedback. A fourth
 * entry there would invite the next person to animate something else at "shake
 * speed".
 */
@Composable
private fun rememberWrongPickShake(state: StudyOptionState): Animatable<Float, *> {
    val nudge = remember { Animatable(0f) }
    // 移除動畫 is a request for no events, not a request for smaller ones. The
    // 3dp alert frame still says which row is out.
    val reduceMotion = rememberReduceMotion()
    var wasWrong by remember { mutableStateOf(state == StudyOptionState.Wrong) }

    LaunchedEffect(state) {
        val nowWrong = state == StudyOptionState.Wrong
        val entering = nowWrong && !wasWrong
        // Set before the suspend, so a state change mid-shake cancels this
        // coroutine without leaving the flag behind at the old answer.
        wasWrong = nowWrong
        if (!entering || reduceMotion) return@LaunchedEffect

        nudge.snapTo(0f)
        nudge.animateTo(-Shake.AMPLITUDE, tween(Shake.OUT, easing = LinearEasing))
        nudge.animateTo(Shake.AMPLITUDE * 0.7f, tween(Shake.BACK, easing = LinearEasing))
        nudge.animateTo(-Shake.AMPLITUDE * 0.25f, tween(Shake.BACK, easing = LinearEasing))
        nudge.animateTo(0f, tween(Shake.SETTLE, easing = LinearOutSlowInEasing))
    }
    return nudge
}

/** The wrong-pick nudge: one lateral shake out, back, and done. */
private object Shake {
    /**
     * How far the first throw goes, in dp. Everything after it is a fraction of
     * this, so the shake damps by construction rather than by four hand-picked
     * numbers that could drift apart.
     */
    const val AMPLITUDE = 7f

    const val OUT = 50
    const val BACK = 60
    const val SETTLE = 50
}

/** iOS `ReviewFlowView`: `.animation(.spring(duration: 0.35), value: coord.question?.phase)`. */
private const val PICK_SECONDS = 0.35f
