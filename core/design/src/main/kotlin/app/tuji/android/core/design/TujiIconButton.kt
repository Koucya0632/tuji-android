package app.tuji.android.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A square of ground with one glyph on it: 書籤, 發音, 返回.
 *
 * Six copies of this existed — the 發音 button alone was written out four times
 * across 複習, 學新字 and 詞條 — and every copy was the same eight lines with a
 * different size and ground. That duplication is why none of them answered a
 * press or a finger: the two things worth adding here would have had to be
 * added six times, and the sixth one is always the one that gets missed.
 *
 * [label] is mandatory and replaces whatever the glyph would have announced:
 * a screen reader hearing "star" learns nothing, and 書籤 is what the control
 * is. It is `clearAndSetSemantics`, so the glyph inside cannot add a second
 * announcement on top of it.
 */
@Composable
fun TujiIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    ground: Color = TujiColor.Paper2,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberTujiHaptics()
    val groundNow by animateColorAsState(
        if (pressed) TujiColor.pressed(ground) else ground,
        TujiMotion.ease(TujiMotion.D1),
        label = "iconButtonGround",
    )

    Box(
        modifier
            .size(size)
            .background(groundNow)
            .tujiClickable(enabled = enabled, interactionSource = interaction) {
                haptics.soft()
                onClick()
            }
            // After the clickable, never before it: clearing runs downwards, so
            // in front it would take the tap action with it.
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
