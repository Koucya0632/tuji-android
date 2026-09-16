package app.tuji.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Category
import app.tuji.android.core.study.SettingsRules

/**
 * 學習主題 — which themes feed 學新字 and every 完成度 number.
 *
 * A page, not the sheet it replaces (iOS's `StudyCategoriesPickerView`).
 * Choosing themes is several taps over a dozen names; a sheet capped at half
 * the screen showed six of them at a time with no way to take all or none, and
 * no count of what was already ticked. It is also something 今日 links to
 * directly, and a sheet has no address to link to.
 *
 * Every tap writes through [onChange] — no save button, as on iOS.
 */
@Composable
fun StudyThemesScreen(
    selected: List<String>,
    categories: List<Category>,
    uiLang: String,
    onChange: (List<String>) -> Unit,
    readiness: SettingsReadiness,
    onRetryLoad: () -> Unit,
) {
    val picked = selected.toSet()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3, bottom = TujiSpace.S6),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.study_themes_intro),
                style = TujiType.label,
                color = TujiColor.Ink3,
                modifier = Modifier.padding(bottom = TujiSpace.S2),
            )
        }

        // The grid computes each new selection from the one on screen, so a
        // grid drawn from the seed would turn 「add one」 into 「replace them all」.
        if (readiness != SettingsReadiness.Ready) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SettingsReadinessLine(readiness, onRetryLoad, Modifier.padding(vertical = TujiSpace.S2), inset = 0.dp)
            }
            return@LazyVerticalGrid
        }

        if (categories.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TujiPageLoading(label = stringResource(R.string.study_themes_loading))
            }
            return@LazyVerticalGrid
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = TujiSpace.S2),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(stringResource(R.string.study_themes_select_all)) {
                    onChange(SettingsRules.selection(categories.map { it.id }))
                }
                TextAction(stringResource(R.string.study_themes_clear)) { onChange(emptyList()) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.study_themes_selected, picked.size),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                )
            }
        }

        items(categories, key = { it.id }) { category ->
            val on = category.id in picked
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (on) TujiColor.Current.copy(alpha = 0.18f) else TujiColor.Paper)
                    .border(if (on) 1.5.dp else TujiBorder.Bw1, if (on) TujiColor.Current else TujiColor.Rule)
                    .tujiClickable {
                        onChange(SettingsRules.selection(SettingsRules.toggleCategory(selected, category.id)))
                    }
                    .semantics {
                        role = Role.Checkbox
                        this.selected = on
                    }
                    .padding(horizontal = TujiSpace.S1, vertical = TujiSpace.S4),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    CategoryShelf.title(category, uiLang),
                    style = TujiType.label,
                    color = TujiColor.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 全選 and 清除: words, in the 棕 iOS tints them, with a full-height target. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).tujiClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.bodySmStrong, color = TujiColor.BrandSecondary)
    }
}
