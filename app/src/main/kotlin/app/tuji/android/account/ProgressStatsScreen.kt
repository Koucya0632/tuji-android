package app.tuji.android.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.model.Category
import app.tuji.android.core.study.CategoryStat
import app.tuji.android.core.study.CompletionReadout
import app.tuji.android.core.study.MasteryDistribution

/**
 * 學習統計 — 我's numbers, opened straight from 今日. iOS's `ProgressStatsView`.
 *
 * Not one new number: these are 我's sections, in 我's order, on a second host.
 * 今日's ink block shrank to one total bar, and "how far have I got" is a
 * question that comes up on 今日, not on 我. Pushed rather than a tab jump, so
 * back lands on 今日.
 */
@Composable
fun ProgressStatsScreen(
    completion: CompletionReadout,
    progress: ProgressStore.Snapshot,
    spread: MasteryDistribution,
    masteryLoaded: Boolean,
    categories: List<Category>,
    studyCategories: List<String>,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(onLeading = onBack, leadingLabel = stringResource(R.string.atlas_back))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            TujiScreenTitle(stringResource(R.string.today_stats))
            CompletionCard(completion)
            MasterySection(spread = spread, loaded = masteryLoaded)
            StreakRow(current = progress.streak?.current ?: 0, longest = progress.streak?.longest ?: 0)
            HeatmapSection(cells = progress.heatmap, activeDays = progress.activeDays)
            CategoryBreakdown(
                remember(progress.categories, categories, studyCategories) {
                    CategoryStat.breakdown(
                        progress = progress.categories,
                        selected = studyCategories,
                        categoryOrder = categories,
                    )
                },
            )
            Spacer(Modifier.height(TujiSpace.S6))
        }
    }
}
