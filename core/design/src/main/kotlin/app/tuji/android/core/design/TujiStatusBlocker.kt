package app.tuji.android.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** What the app is busy with, which decides the panel's edge. */
enum class TujiStatusKind {
    /** The AI is looking at a photograph. 瞳黃, because it is 現在. */
    Working,

    /** Something is being taken away. 警示, because it cannot be taken back. */
    Removing;

    internal val edge: Color get() = when (this) {
        Working -> TujiColor.Current
        Removing -> TujiColor.Alert
    }
}

/**
 * Work the user must wait for, with the screen underneath sealed off.
 *
 * The point is the sealing, not the words. Android's version of these three
 * moments said 刪除中… on the button that started them and left the rest of
 * the page live: you could keep selecting cards while the ones you had already
 * chosen were being deleted, and re-run 高精度識別 on top of the 高精度識別
 * already in flight. A label is not a lock.
 *
 * **Not a port of iOS's `TujiStatusToast`.** That one predates 紙與墨 and still
 * carries what the redesign removed — 28pt rounded corners, a frosted material,
 * a white hairline, an angular-gradient spinner and a bouncy spring. The file
 * that hosts it says so itself: `AtlasCaptureView`'s recognising panel is
 * commented "**No spinner** — a determinate-looking bar for work of unknown
 * length is a lie, and a spinner is the platform's own idle mark", twenty lines
 * from the toast it also presents. On Android the frosted layer is not even
 * available: `Modifier.blur` is API 31 against a minSdk of 29, where it draws
 * the thing it was meant to obscure. So this says the same sentences through
 * this app's own vocabulary — square on paper, the 3dp edge a prompt uses, and
 * the sweeping rule the app already shows for work of unknown length.
 *
 * @param slowLine what to add once the wait passes [SLOW_AFTER] — C.11's
 *   "waiting > 3s" clause. Before that the wait is short enough that a sentence
 *   about it would be the slower thing on screen. Null for work that is
 *   supposed to be quick either way.
 */
@Composable
fun TujiStatusBlocker(
    visible: Boolean,
    title: String,
    detail: String,
    kind: TujiStatusKind = TujiStatusKind.Working,
    slowLine: String? = null,
) {
    if (!visible) return
    // A window rather than a Box over the caller: an overlay drawn inside a
    // screen leaves the shell's back arrow and the tab bar above it live, which
    // is most of what this is for. Dismissal is deliberately a no-op — a status
    // that back closes is a label again.
    TujiWindow(onDismiss = {}) {
        StatusSurface(title, detail, kind, slowLine)
    }
}

@Composable
private fun StatusSurface(title: String, detail: String, kind: TujiStatusKind, slowLine: String?) {
    val reduceMotion = rememberReduceMotion()
    val shown = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) shown.animateTo(1f, TujiMotion.ease(TujiMotion.D2))
    }

    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(slowLine) {
        slow = false
        if (slowLine == null) return@LaunchedEffect
        delay(SLOW_AFTER)
        slow = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            // Lighter than a prompt's scrim: this is not asking anything, so it
            // should not put the page as far away as a question does.
            .background(TujiColor.Ink.copy(alpha = 0.16f))
            .padding(horizontal = TujiSpace.S4),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = 0.98f + 0.02f * shown.value
                    scaleX = scale
                    scaleY = scale
                }
                .background(TujiColor.Paper)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw3).background(kind.edge))
            Column(
                Modifier.fillMaxWidth().padding(TujiSpace.S4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                Text(title, style = TujiType.bodyStrong, color = TujiColor.Ink, textAlign = TextAlign.Center)
                Text(detail, style = TujiType.label, color = TujiColor.Ink3, textAlign = TextAlign.Center)
                // Work with no known end, so a rule that sweeps rather than a
                // percentage — the same mark 上傳中 already uses two steps
                // earlier in the same flow.
                TujiIndeterminateBar(
                    modifier = Modifier.padding(top = TujiSpace.S1),
                    fill = kind.edge,
                    label = title,
                )
            }
            if (slow && slowLine != null) {
                MascotSpeechBubble(
                    pose = MascotPose.Think,
                    text = slowLine,
                    modifier = Modifier.padding(start = TujiSpace.S2, end = TujiSpace.S3, bottom = TujiSpace.S3),
                )
            }
        }
    }
}

/** C.11: a wait only earns a sentence about itself after three seconds. */
private const val SLOW_AFTER = 3_000L
