package app.tuji.android.account

import androidx.compose.foundation.background
import java.util.Locale
import java.time.format.TextStyle
import java.time.DayOfWeek
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.atlas.label
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.ground
import app.tuji.android.core.model.HeatmapCell
import app.tuji.android.core.model.StudyStats
import app.tuji.android.core.study.CategoryStat
import app.tuji.android.core.study.HeatmapBand
import app.tuji.android.core.study.MasteryDistribution
import app.tuji.android.core.study.MasteryLevel
import androidx.compose.ui.res.stringResource

/**
 * 我的 · 進度.
 *
 * The information order is deliberate: how far you have come (the ink block) →
 * how deep it goes (熟練度) → whether you keep showing up (連勝, 熱力圖) → where
 * exactly (分類明細). Width, then depth, then habit, then detail.
 *
 * Only the first is an ink block. It is "the one number on this screen", and a
 * second dark slab would leave the page with two of them and no hierarchy.
 */
@Composable
fun CompletionCard(stats: StudyStats?) {
    val seen = stats?.seen ?: 0
    val total = stats?.total ?: 0
    val percent = if (total > 0) (seen * 100) / total else 0
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Ink)
            .padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Text(
            stringResource(R.string.me_completion),
            style = TujiType.label,
            color = TujiColor.Paper.copy(alpha = 0.6f),
        )
        Text("$percent%", style = TujiType.display, color = TujiColor.AccumulationSoft)
        Text(
            stringResource(R.string.me_completion_detail, seen, total),
            style = TujiType.bodySm,
            color = TujiColor.Paper.copy(alpha = 0.7f),
        )
        TujiProgressBar(
            progress = if (total > 0) seen.toDouble() / total else 0.0,
            track = TujiColor.Paper.copy(alpha = 0.15f),
            // The pale step: on ink the deep teal reaches 3.04:1 and the pale
            // one 13.58:1, and they carry the same meaning.
            fill = TujiColor.AccumulationSoft,
            modifier = Modifier.padding(top = TujiSpace.S2),
        )
    }
}

/**
 * 熟練度 — 精通 as the headline, the spread as the evidence.
 *
 * Three states, and the middle one is why this is not a plain `isEmpty` branch.
 * An empty score map means "nothing studied" only *after* the store has
 * answered; before that it means "not known yet", and printing 還沒有學習紀錄
 * over it states something about the account that may be false — on a slow
 * network, for exactly the long-standing user this section exists to reassure.
 */
@Composable
fun MasterySection(spread: MasteryDistribution, loaded: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Text(stringResource(R.string.me_mastery), style = TujiType.label, color = TujiColor.Ink3)
        when {
            !loaded -> MasteryStackedBar(MasteryDistribution.empty)
            spread.isEmpty -> Notice(stringResource(R.string.me_no_records))
            else -> {
                StatColumn(
                    label = MasteryLevel.Expert.label(),
                    value = spread.expert,
                    unit = stringResource(R.string.me_unit_words),
                )
                MasteryStackedBar(spread)
                MasteryLegend(spread)
            }
        }
    }
}

/**
 * The spread as one bar, each tier as wide as its share.
 *
 * An empty distribution draws the track alone — that is the "not answered yet"
 * state, and a bar with nothing in it says less than a sentence would.
 */
@Composable
private fun MasteryStackedBar(spread: MasteryDistribution) {
    // A fixed height, not `heightIn(min =)`. The segments below ask to fill
    // the row's height, and a row whose own height is only a *minimum* passes
    // its children an unbounded max — where `fillMaxHeight` resolves to zero,
    // not to the minimum. The bar drew nothing but its own track, which is
    // exactly what "you have studied nothing" looks like.
    Row(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .background(TujiColor.Paper3),
    ) {
        spread.segments.forEach { segment ->
            if (segment.words > 0) {
                Box(
                    Modifier
                        .weight(segment.words.toFloat())
                        .fillMaxHeight()
                        .background(segment.level.ground),
                )
            }
        }
        // Nothing counted leaves the whole width as track, which is what the
        // unanswered state looks like.
        if (spread.isEmpty) Box(Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * Swatch, tier, count — two columns, read left to right and then down, so the
 * ladder order survives the wrap.
 *
 * Two columns rather than one row because four tiers do not fit on a line
 * outside 繁中: English and Japanese both overflow a narrow phone. Development
 * happens in 繁中 and CI runs English, which is precisely the pairing that
 * ships an overflow nobody saw.
 */
@Composable
private fun MasteryLegend(spread: MasteryDistribution) {
    val rows = spread.segments.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        rows.forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                pair.forEach { segment ->
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(segment.level.ground))
                        Text(
                            segment.level.label(),
                            style = TujiType.label,
                            color = TujiColor.Ink3,
                        )
                        Text(
                            "${segment.words}",
                            style = TujiType.monoLabel,
                            color = TujiColor.Ink2,
                        )
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 連勝, two columns on paper.
 *
 * No tile, no border, no flame. A streak is a fact about the account, and those
 * three were three ways of insisting on it. When it breaks the number is 0 and
 * one line explains — it does not turn red or send the cat out.
 */
@Composable
fun StreakRow(current: Int, longest: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S5),
    ) {
        StatColumn(
            label = stringResource(R.string.me_streak_current),
            value = current,
            unit = stringResource(R.string.me_unit_days),
            note = if (current == 0) stringResource(R.string.me_streak_restart) else null,
        )
        StatColumn(
            label = stringResource(R.string.me_streak_longest),
            value = longest,
            unit = stringResource(R.string.me_unit_days),
        )
        Box(Modifier.weight(1f))
    }
}

/** Label, display number, unit — one shape for every plain accumulated fact. */
@Composable
private fun StatColumn(label: String, value: Int, unit: String, note: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Text(label, style = TujiType.label, color = TujiColor.Ink3)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("$value", style = TujiType.display, color = TujiColor.Ink)
            Text(
                unit,
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        note?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink3) }
    }
}

/** 最近 6 週 — 42 squares, seven to a row, plus how many of them were active. */
@Composable
fun HeatmapSection(cells: List<HeatmapCell>, activeDays: Int) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.me_recent_weeks),
                style = TujiType.label,
                color = TujiColor.Ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.me_active_days, activeDays),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
        }
        if (cells.isEmpty()) {
            Notice(stringResource(R.string.me_no_records))
        } else {
            WeekdayHeader()
            HeatmapGrid(cells)
            HeatmapLegend()
        }
    }
}

/**
 * 日 一 二 三 四 五 六 — Sunday first, matching the column order the server
 * fills.
 *
 * Read from the composition's configuration rather than `Locale.getDefault()`:
 * the latter is not observable state, so the header would keep yesterday's
 * language after the user switched. Keyed by index, because English's narrow
 * symbols repeat — two "S" and two "T".
 */
@Composable
private fun WeekdayHeader() {
    val configuration = LocalConfiguration.current
    val labels = remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.ROOT
        // Sunday first, so the list starts where the grid does rather than on
        // DayOfWeek's own Monday.
        (0..6).map {
            DayOfWeek.SUNDAY.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        labels.forEach { label ->
            Text(
                label,
                style = TujiType.label,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeatmapGrid(cells: List<HeatmapCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        cells.chunked(COLUMNS).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                week.forEach { cell ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            // Square, like everything else. A rounded heat cell
                            // is the GitHub contribution graph's own signature.
                            .background(tint(cell)),
                    )
                }
                // A short final week keeps its columns, so the grid stays a
                // grid rather than stretching its last row across the width.
                repeat(COLUMNS - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.me_less), style = TujiType.label, color = TujiColor.Ink3)
        HeatmapBand.entries.forEach { band ->
            Box(Modifier.size(10.dp).background(bandTint(band)))
        }
        Text(stringResource(R.string.me_more), style = TujiType.label, color = TujiColor.Ink3)
    }
}

/**
 * A cell after today is not a day the user failed to study, so it is drawn as
 * paper rather than as the empty band — which is the same colour a *missed* day
 * gets, and would put six weeks of failure on a Monday.
 */
private fun tint(cell: HeatmapCell) =
    if (cell.future) TujiColor.Paper else bandTint(HeatmapBand.of(cell.count))

private fun bandTint(band: HeatmapBand) = when (band) {
    HeatmapBand.None -> TujiColor.Paper3
    HeatmapBand.Light -> TujiColor.AccumulationSoft
    HeatmapBand.Medium -> TujiColor.Accumulation
    HeatmapBand.Heavy -> TujiColor.AccumulationDeep
}

/** 分類明細 — where the completion above actually came from. */
@Composable
fun CategoryBreakdown(stats: List<CategoryStat>) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Text(stringResource(R.string.me_breakdown), style = TujiType.label, color = TujiColor.Ink3)
        if (stats.isEmpty()) {
            Notice(stringResource(R.string.me_no_records))
        } else {
            stats.forEach { stat ->
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stat.name,
                            style = TujiType.h3,
                            color = TujiColor.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.study_count, stat.learned, stat.total),
                            style = TujiType.monoLabel,
                            color = TujiColor.Ink3,
                        )
                    }
                    TujiProgressBar(
                        progress = stat.ratio,
                        track = TujiColor.Paper3,
                        fill = TujiColor.Accumulation,
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String) {
    Text(
        message,
        style = TujiType.label,
        color = TujiColor.Ink3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = TujiSpace.S3),
    )
}

/** Seven days to a row, Sunday first — the order the server fills them in. */
private const val COLUMNS = 7

private val BAR_HEIGHT = 12.dp
