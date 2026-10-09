package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** One line of a [TujiMenu]. [destructive] is iOS's `role: .destructive`: red. */
data class TujiMenuItem(
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * ⋯'s menu, dropped from the ⋯ itself — iOS's `Menu`, which opens where it was
 * tapped rather than rising from the bottom edge as a sheet. Draw it inside the
 * box that holds ⋯ so it anchors there.
 *
 * Labels arrive resolved: a popup is a view of its own and would read the
 * device's language rather than the app's (see [TujiWindow]).
 */
@Composable
fun TujiMenu(items: List<TujiMenuItem>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 44.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 200.dp)
                .background(TujiColor.Paper)
                .border(TujiBorder.Bw1, TujiColor.Rule),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
                val color: Color = when {
                    !item.enabled -> TujiColor.Ink3
                    item.destructive -> TujiColor.Alert
                    else -> TujiColor.Ink
                }
                Text(
                    item.label,
                    style = TujiType.body,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tujiClickable(enabled = item.enabled, onClick = item.onClick)
                        .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                )
            }
        }
    }
}
