package app.tuji.android.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What a prompt is asking, which decides its cat and its top edge. */
enum class TujiPromptStyle {
    /** A question with a safe answer. The thinking cat, an ink edge. */
    Confirmation,

    /** Something went right. The cheering cat. */
    Success,

    /** Something failed. No cat, an alert edge. */
    Error,

    /**
     * Something that cannot be taken back. No cat — asking somebody to confirm
     * losing their progress while a cat looks on is flippant — and an alert edge.
     */
    Destructive,
}

/**
 * Ask before doing something that cannot be taken back on this screen.
 *
 * iOS's `tujiPrompt`: centred, not a bottom sheet, because this asks for a
 * *decision* and a sheet is the shape the app uses for choosing among options.
 * The 3dp edge along the top is the same mark the tab bar and selected states
 * use.
 *
 * **The action runs before anything is dismissed.** iOS's equivalent hides
 * itself first, which nils the state its own primary action then reads — so an
 * action written the obvious way silently does nothing, and the fix there is to
 * copy the value into a local before presenting. Here the caller owns the
 * visibility and this only reports the choice, so there is no state to lose.
 *
 * Not a Material dialog: a rounded floating card with a tonal button is the
 * platform talking over a screen that has its own voice.
 *
 * @param cancel the quiet way out, drawn as text under the action. Null for a
 *   prompt that only reports (a failure, a success), which still closes on
 *   back and on a tap outside through [onCancel].
 * @param alternative a second, lesser answer that does part of what was asked
 *   — 改為取消公開 beside a delete that would reach other people's accounts.
 *   Drawn between the action and the way out, in the quiet style.
 */
@Composable
fun TujiPrompt(
    title: String,
    message: String?,
    confirm: String,
    cancel: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    style: TujiPromptStyle = TujiPromptStyle.Confirmation,
    detail: String? = null,
    alternative: String? = null,
    onAlternative: () -> Unit = {},
) {
    // A window of its own, not a Box over the caller's content — see TujiWindow.
    TujiWindow(onDismiss = onCancel) {
        PromptSurface(title, message, confirm, cancel, onConfirm, onCancel, style, detail, alternative, onAlternative)
    }
}

@Composable
private fun PromptSurface(
    title: String,
    message: String?,
    confirm: String,
    cancel: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    style: TujiPromptStyle,
    detail: String?,
    alternative: String?,
    onAlternative: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val shown = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) shown.animateTo(1f, TujiMotion.ease(TujiMotion.D2))
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(TujiColor.Scrim)
            // A tap outside is the cancel, which is the safe half of every
            // question this asks.
            .tujiClickable(onClick = onCancel)
            .padding(horizontal = TujiSpace.S4),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = 0.98f + 0.02f * shown.value
                    scaleX = scale
                    scaleY = scale
                }
                // The card swallows its own taps. Without this a tap on the
                // title fell through to the scrim's click and cancelled — the
                // question closed when somebody touched the words of it.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        waitForUpOrCancellation()?.consume()
                    }
                }
                .background(TujiColor.Paper)
                .semantics { paneTitle = title },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TujiBorder.Bw3)
                    .background(if (style.isAlarming) TujiColor.Alert else TujiColor.Ink),
            )
            Column(
                Modifier.fillMaxWidth().padding(TujiSpace.S4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                style.pose?.let {
                    MascotFigure(pose = it, size = 64.dp, modifier = Modifier.padding(bottom = TujiSpace.S3))
                }
                Text(title, style = TujiType.h2, color = TujiColor.Ink, textAlign = TextAlign.Center)
                message?.let {
                    Text(
                        it,
                        style = TujiType.body,
                        color = TujiColor.Ink2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = TujiSpace.S2),
                    )
                }
                detail?.let {
                    Text(
                        it,
                        style = TujiType.bodySm,
                        color = TujiColor.Ink2,
                        modifier = Modifier
                            .padding(top = TujiSpace.S3)
                            .fillMaxWidth()
                            .background(TujiColor.Paper2)
                            .padding(TujiSpace.S3),
                    )
                }
                // Stacked, not side by side: two buttons of equal width read as
                // equally weighted choices, and this has a primary and a way out.
                Column(
                    Modifier.fillMaxWidth().padding(top = TujiSpace.S4),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                ) {
                    PromptAction(confirm, destructive = style == TujiPromptStyle.Destructive, onClick = onConfirm)
                    alternative?.let { PromptAction(it, destructive = false, quiet = true, onClick = onAlternative) }
                    cancel?.let { PromptTextAction(it, onCancel) }
                }
            }
        }
    }
}

/**
 * 瞳黃 for an ordinary action. A destructive one stays quiet — 紙2 with alert
 * ink — and inverts under the finger, so the colour arrives with the weight of
 * the tap rather than before it.
 */
@Composable
private fun PromptAction(text: String, destructive: Boolean, onClick: () -> Unit, quiet: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val ground = when {
        destructive && pressed -> TujiColor.Alert
        destructive -> TujiColor.Paper2
        quiet && pressed -> TujiColor.Paper3
        quiet -> TujiColor.Paper2
        pressed -> TujiColor.CurrentDeep
        else -> TujiColor.Current
    }
    val ink = when {
        destructive && pressed -> TujiColor.Paper
        destructive -> TujiColor.Alert
        else -> TujiColor.Ink
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(ground)
            .tujiClickable(interactionSource = interaction) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TujiType.h3, color = ink)
    }
}

@Composable
private fun PromptTextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .tujiClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TujiType.label, color = TujiColor.Ink2)
    }
}

private val TujiPromptStyle.pose: MascotPose?
    get() = when (this) {
        TujiPromptStyle.Confirmation -> MascotPose.Think
        TujiPromptStyle.Success -> MascotPose.Cheer
        TujiPromptStyle.Error, TujiPromptStyle.Destructive -> null
    }

private val TujiPromptStyle.isAlarming: Boolean
    get() = this == TujiPromptStyle.Error || this == TujiPromptStyle.Destructive
