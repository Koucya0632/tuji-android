package app.tuji.android.settings

import app.tuji.android.form.TujiSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSettingRow
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType

/**
 * The sheet every picker on this screen uses — iOS's `tujiSheet`: the shared
 * [TujiSheet] head, then the options, then an optional footer.
 */
@Composable
private fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    TujiSheet(title = title, onDismiss = onDismiss, maxHeight = 520.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(top = TujiSpace.S3)) {
            content()
            footer?.let {
                Text(
                    it,
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    modifier = Modifier.padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S4),
                )
            }
            Spacer(Modifier.height(TujiSpace.S5))
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
