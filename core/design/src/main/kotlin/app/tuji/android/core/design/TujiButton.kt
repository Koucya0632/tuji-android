package app.tuji.android.core.design

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's button. Not Material's.
 *
 * Material's `Button` arrives with a 20dp corner radius, a tonal elevation and
 * a ripple — three things 紙與墨 rules out by name. [TujiTheme] already pins the
 * shape set to zero, but elevation and the ripple survive that, and a design
 * whose depth comes from *changing the ground* cannot also cast shadows.
 *
 * Pressed state is therefore a different ground, never a shadow and never a
 * scale. That is the same rule the ink and paper tokens state.
 */
enum class TujiButtonStyle {
    /** The one action a screen is asking for. */
    Primary,

    /** An alternative that is not the point of the screen. */
    Secondary,
}

@Composable
fun TujiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TujiButtonStyle = TujiButtonStyle.Primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val ground: Color = when {
        !enabled -> TujiColor.Paper3
        style == TujiButtonStyle.Primary && pressed -> TujiColor.CurrentDeep
        style == TujiButtonStyle.Primary -> TujiColor.Current
        pressed -> TujiColor.Paper2
        else -> TujiColor.Paper
    }
    val ink: Color = if (enabled) TujiColor.Ink else TujiColor.Ink3

    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .background(ground)
            .then(
                if (style == TujiButtonStyle.Secondary) {
                    Modifier.border(TujiBorder.Bw1, TujiColor.Rule, RoundedCornerShape(TujiRadius.R0))
                } else {
                    Modifier
                }
            )
            .tujiClickable(enabled = enabled, interactionSource = interaction, onClick = onClick)
            .padding(horizontal = TujiSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            leading?.invoke()
            Text(
                text,
                // Body-with-weight, not a heading: a button's label is a body
                // step carrying weight, which is the axis 84 iOS call sites
                // were bypassing the scale to get.
                style = TujiType.bodyStrong,
                color = ink,
            )
        }
    }
}
