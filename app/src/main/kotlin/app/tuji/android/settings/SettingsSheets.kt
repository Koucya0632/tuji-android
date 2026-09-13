package app.tuji.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSettingRow
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable

/**
 * The bottom sheet every picker on this screen uses.
 *
 * Hand-rolled rather than Material's `ModalBottomSheet`: that component brings
 * a rounded top, a drag handle and a scrim of its own, and the first two are
 * Material's signature in a system whose every surface is a square on paper.
 * What survives is the part that matters — a scrim that dismisses, and content
 * that never grows past half the screen.
 *
 * **A window of its own** — see `TujiWindow`.
 */
@Composable
private fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    TujiWindow(onDismiss = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TujiColor.Scrim)
                .tujiClickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .background(TujiColor.Paper)
                    // The 3dp top edge is a selection indicator, which is the one
                    // thing that weight means in this system.
                    .padding(top = TujiBorder.Bw3)
                    // Swallows taps so a tap on the sheet itself does not dismiss
                    // it through the scrim underneath.
                    .tujiClickable {}
                    .navigationBarsPadding(),
            ) {
                Text(
                    title,
                    style = TujiType.h2,
                    color = TujiColor.Ink,
                    modifier = Modifier.padding(TujiSpace.S4),
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    content()
                    footer?.let {
                        Text(
                            it,
                            style = TujiType.label,
                            color = TujiColor.Ink3,
                            modifier = Modifier.padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3),
                        )
                    }
                    Spacer(Modifier.height(TujiSpace.S6))
                }
            }
        }
    }
}

/** One choice out of a few. Picking closes the sheet — the change is done. */
@Composable
fun OptionSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    Sheet(title = title, onDismiss = onDismiss, footer = footer) {
        options.forEachIndexed { index, (value, label) ->
            if (index > 0) TujiRowDivider()
            TujiSettingRow(
                label = label,
                showsArrow = false,
                onClick = { onPick(value) },
                trailing = {
                    // A mark, not a radio: the chosen row is the one carrying
                    // ink, the same way a selected chip is.
                    if (value == selected) {
                        Text("✓", style = TujiType.h3, color = TujiColor.Ink)
                    }
                },
            )
        }
    }
}

/** 關於 — what this app is, and the two links a store listing has to carry. */
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    Sheet(title = stringResource(R.string.settings_about), onDismiss = onDismiss) {
        Column(
            Modifier.padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Text(
                stringResource(R.string.about_body),
                style = TujiType.body,
                color = TujiColor.Ink2,
            )
        }
    }
}
