package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Ask before doing something that cannot be taken back on this screen.
 *
 * **The action runs before anything is dismissed.** iOS's equivalent hides
 * itself first, which nils the state its own primary action then reads — so an
 * action written the obvious way silently does nothing, and the fix there is to
 * copy the value into a local before presenting. Here the caller owns the
 * visibility and this only reports the choice, so there is no state to lose.
 *
 * Not a Material dialog: a rounded floating card with a tonal button is the
 * platform talking over a screen that has its own voice.
 */
@Composable
fun TujiPrompt(
    title: String,
    message: String?,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Scrim)
            // A tap outside is the cancel, which is the safe half of every
            // question this asks.
            .tujiClickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(TujiSpace.S4)
                .background(TujiColor.Paper)
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Text(title, style = TujiType.h3, color = TujiColor.Ink)
            message?.let {
                Text(it, style = TujiType.body, color = TujiColor.Ink2)
            }
            TujiButton(
                text = confirm,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
            TujiButton(
                text = cancel,
                style = TujiButtonStyle.Secondary,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
