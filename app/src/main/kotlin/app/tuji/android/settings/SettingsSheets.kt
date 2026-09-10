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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSettingRow
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Category

/**
 * The bottom sheet every picker on this screen uses.
 *
 * Hand-rolled rather than Material's `ModalBottomSheet`: that component brings
 * a rounded top, a drag handle and a scrim of its own, and the first two are
 * Material's signature in a system whose every surface is a square on paper.
 * What survives is the part that matters — a scrim that dismisses, and content
 * that never grows past half the screen.
 */
@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
                .tujiClickable {},
        ) {
            Text(
                title,
                style = TujiType.h2,
                color = TujiColor.Ink,
                modifier = Modifier.padding(TujiSpace.S4),
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                content()
                Spacer(Modifier.height(TujiSpace.S6))
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
) {
    Sheet(title = title, onDismiss = onDismiss) {
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

/**
 * The themes to study. **Stays open** — picking themes is several taps, and a
 * sheet that closed after each one would have to be reopened five times.
 */
@Composable
fun ThemeSheet(
    selected: List<String>,
    categories: List<Category>,
    uiLang: String,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(title = stringResource(R.string.settings_themes), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.settings_themes_all_hint),
            style = TujiType.bodySm,
            color = TujiColor.Ink3,
            modifier = Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        )
        categories.forEachIndexed { index, category ->
            if (index > 0) TujiRowDivider()
            TujiSettingRow(
                label = CategoryShelf.title(category, uiLang),
                showsArrow = false,
                onClick = { onToggle(category.id) },
                trailing = {
                    TujiCheckbox(category.id in selected) { onToggle(category.id) }
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
