package app.tuji.android.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.billing.PurchaseGate
import app.tuji.android.core.design.HeroPill
import app.tuji.android.core.design.HeroPillRole
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.StudyStats
import app.tuji.android.core.study.TodayDecisions
import app.tuji.android.core.study.TodayHeroHint
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.core.study.TodayNewBlock
import app.tuji.android.core.study.TodaySubtitle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 今日 — what there is to do, and how far through it the day is.
 *
 * **No shared horizontal padding.** The ink block bleeds to the screen edges
 * and each section owns its own margin: a block with paper on either side is
 * "a card", one that reaches the edges is "this part of the screen", and the
 * difference in weight is large. The Android version was a light card for its
 * first four milestones; this is the iOS shape.
 *
 * Every verdict still comes from [TodayDecisions]; this owns the words and the
 * ink.
 */
@Composable
fun TodayScreen(
    inputs: TodayInputs,
    name: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onReview: () -> Unit,
    onLearnNew: () -> Unit,
    onSearch: () -> Unit,
    onCreateAccount: () -> Unit,
    shelves: List<CategoryShelf.Shelf> = emptyList(),
    uiLang: String = "zh-Hant",
    onOpenShelf: (String) -> Unit = {},
    onOpenAtlas: () -> Unit = {},
) {
    val decisions = TodayDecisions(inputs)

    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S5)) {
        Spacer(Modifier.height(TujiSpace.S3))
        Greeting(
            decisions = decisions,
            stats = inputs.stats,
            name = name,
            onSearch = onSearch,
            modifier = Modifier.padding(horizontal = TujiSpace.S4),
        )
        Hero(
            decisions = decisions,
            inputs = inputs,
            onReview = onReview,
            onLearnNew = onLearnNew,
            onCreateAccount = onCreateAccount,
        )
        if (shelves.isNotEmpty()) {
            Themes(
                shelves = shelves,
                uiLang = uiLang,
                onOpenShelf = onOpenShelf,
                onOpenAtlas = onOpenAtlas,
            )
        }
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}

/**
 * The themes strip.
 *
 * **Horizontal, not a grid.** Three tiles fill a row and the screen simply
 * stops; scrolling says "there is more" and hands the vertical space back to
 * the ink block above it.
 *
 * The link is named for **where it goes**. iOS's used to say 「全部 →」, which
 * promises the whole catalogue and delivered a settings multi-select; here it
 * does open the whole catalogue, so it says 圖鑑.
 */
@Composable
private fun Themes(
    shelves: List<CategoryShelf.Shelf>,
    uiLang: String,
    onOpenShelf: (String) -> Unit,
    onOpenAtlas: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.today_themes),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.today_themes_all),
                style = TujiType.label,
                color = TujiColor.Ink,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.tujiClickable(onClick = onOpenAtlas),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = TujiSpace.S4),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            items(shelves, key = { it.category.id }) { shelf ->
                ThemeTile(shelf = shelf, uiLang = uiLang) { onOpenShelf(shelf.category.id) }
            }
        }
    }
}

@Composable
private fun ThemeTile(shelf: CategoryShelf.Shelf, uiLang: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Paper3)
            .tujiClickable(onClick = onClick)
            .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            CategoryShelf.title(shelf.category, uiLang),
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.today_theme_words, shelf.count),
            style = TujiType.label,
            color = TujiColor.Ink3,
        )
    }
}

@Composable
private fun Greeting(
    decisions: TodayDecisions,
    stats: StudyStats?,
    name: String?,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        // Search rides the date line, not the headline: the headline is one
        // flowing line precisely so a long localisation wraps, and taking ~90dp
        // off its right edge would cost it a line — more than the row saves.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dateLabel(),
                style = TujiType.label.copy(letterSpacing = 2.sp),
                color = TujiColor.Ink3,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.today_search),
                style = TujiType.bodySmStrong,
                color = TujiColor.Ink2,
                modifier = Modifier.tujiClickable(onClick = onSearch).padding(TujiSpace.S1),
            )
        }

        // One string with the name inside it, so the whole greeting wraps as
        // one line. The name is the only part in full ink.
        Text(
            run {
                val shown = name.orEmpty()
                val whole = stringResource(greetingPrefix(), shown)
                val at = if (shown.isEmpty()) -1 else whole.indexOf(shown)
                buildAnnotatedString {
                    if (at < 0) {
                        append(whole)
                    } else {
                        append(whole.substring(0, at))
                        withStyle(SpanStyle(color = TujiColor.Ink)) { append(shown) }
                        append(whole.substring(at + shown.length))
                    }
                }
            },
            style = TujiType.h2,
            color = TujiColor.Ink,
        )

        Text(subtitleText(decisions, stats), style = TujiType.bodySm, color = TujiColor.Ink3)
    }
}

/**
 * The date in the interface language, as an overline.
 *
 * The locale comes from the configuration, not `Locale.getDefault()`: the
 * latter is not observable state, so a language switch would leave this line
 * formatted in the old one until something else happened to recompose it.
 * Lint calls this out by name (`NonObservableLocale`) — it is the same mistake
 * as hard-coding `uiLang`, one layer down.
 */
@Composable
private fun dateLabel(): String {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.ROOT
    }
    return LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
        .uppercase(locale)
}

@Composable
private fun greetingPrefix(): Int = when (LocalTime.now().hour) {
    in 5..10 -> R.string.today_greeting_morning
    in 11..17 -> R.string.today_greeting_afternoon
    else -> R.string.today_greeting_evening
}

@Composable
private fun Hero(
    decisions: TodayDecisions,
    inputs: TodayInputs,
    onReview: () -> Unit,
    onLearnNew: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Ink)
                .padding(TujiSpace.S4)
                .padding(top = TujiSpace.S5),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Column(
                // Room for the cat, which straddles the top-right edge.
                Modifier.padding(end = 96.dp),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                if (!inputs.isGuest) DailyGoal(decisions, inputs)
                ThemeProgress(inputs.stats)
            }

            if (inputs.isGuest) {
                // A guest cannot study — the SRS is account-scoped — so instead
                // of two permanently dead buttons the hero offers the one
                // action that works.
                HeroPill(
                    text = stringResource(R.string.today_guest_cta),
                    role = HeroPillRole.Primary,
                    onClick = onCreateAccount,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.today_guest_why),
                    style = TujiType.label,
                    color = TujiColor.Paper.copy(alpha = 0.6f),
                )
            } else {
                // One control, so one height. 複習 is two characters in every
                // language and its neighbour is not; the moment the longer
                // label wraps, an unconstrained row draws a short button beside
                // a tall one and the pair reads as two unrelated things.
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                ) {
                    HeroPill(
                        text = stringResource(R.string.today_review),
                        role = if (decisions.reviewDisabled) HeroPillRole.Secondary else HeroPillRole.Primary,
                        enabled = !decisions.reviewDisabled,
                        onClick = onReview,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    HeroPill(
                        text = stringResource(R.string.today_learn_new),
                        // 瞳 marks the recommended action: when there is nothing
                        // to review, learning new words is the thing to do.
                        role = if (decisions.reviewDisabled && !decisions.newDisabled) {
                            HeroPillRole.Primary
                        } else {
                            HeroPillRole.Secondary
                        },
                        enabled = !decisions.newDisabled,
                        onClick = onLearnNew,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                heroHintText(decisions, inputs.stats)?.let {
                    Text(it, style = TujiType.label, color = TujiColor.Paper.copy(alpha = 0.6f))
                }
            }
        }

        // Over the ink rather than inside it, so the cat straddles the top edge:
        // its body is ink, so the overlapping half reads as a silhouette and
        // only the eyes come forward — the gesture the logo already uses.
        MascotFigure(
            pose = if (decisions.goalReached) MascotPose.Cheer else MascotPose.Wave,
            size = 96.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = TujiSpace.S4)
                // Straddling the edge, not floating above it: the body is ink,
                // so the overlapping half reads as a silhouette and only the
                // eyes come forward. MascotFigure has already cropped the
                // artwork's transparent margin, so this shifts the *drawn* cat
                // rather than its frame — half of 96 is what puts the waist on
                // the line.
                .offset(y = (-48).dp),
        )
    }
}

@Composable
private fun DailyGoal(decisions: TodayDecisions, inputs: TodayInputs) {
    val done = inputs.stats?.todayNew ?: 0
    val goal = maxOf(1, inputs.dailyGoal)
    HeroMeter(
        label = stringResource(R.string.today_goal),
        trailing = if (decisions.goalReached) {
            stringResource(R.string.today_goal_reached_badge)
        } else {
            stringResource(R.string.today_goal_progress, done, goal)
        },
        trailingColor = if (decisions.goalReached) TujiColor.Current else TujiColor.Paper.copy(alpha = 0.7f),
        progress = done.toDouble() / goal,
        // Always 瞳黃. The unreached state was Alert once, which said "you are
        // failing" — not hitting today's goal at 10am is not an error, it is
        // the thing in progress.
        fill = TujiColor.Current,
    )
}

@Composable
private fun ThemeProgress(stats: StudyStats?) {
    val seen = stats?.seen ?: 0
    val total = stats?.total ?: 0
    HeroMeter(
        label = stringResource(R.string.today_theme_progress),
        trailing = "$seen / $total",
        trailingColor = TujiColor.Paper.copy(alpha = 0.7f),
        progress = if (total > 0) seen.toDouble() / total else 0.0,
        // The pale step, not the deep teal: on ink the deep one reaches 3.04:1
        // and the pale one 13.58:1, and they mean the same thing.
        fill = TujiColor.AccumulationSoft,
    )
}

@Composable
private fun HeroMeter(
    label: String,
    trailing: String,
    trailingColor: androidx.compose.ui.graphics.Color,
    progress: Double,
    fill: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = TujiType.label.copy(letterSpacing = 2.sp),
                color = TujiColor.Paper.copy(alpha = 0.6f),
            )
            Text(trailing, style = TujiType.label, color = trailingColor)
        }
        TujiProgressBar(
            progress = progress,
            track = TujiColor.Paper.copy(alpha = 0.2f),
            fill = fill,
        )
    }
}

@Composable
private fun subtitleText(decisions: TodayDecisions, stats: StudyStats?): String =
    when (decisions.subtitle) {
        TodaySubtitle.GuestBrowsing -> stringResource(R.string.today_sub_guest)
        TodaySubtitle.Unknown -> stringResource(R.string.today_sub_unknown)
        TodaySubtitle.ReviewDue -> stringResource(R.string.today_sub_review_due, stats?.due ?: 0)
        TodaySubtitle.GoalReached -> stringResource(R.string.today_sub_goal_reached)
        TodaySubtitle.NewDoneToday ->
            stringResource(R.string.today_sub_new_done, stats?.todayNew ?: 0)
        TodaySubtitle.NewAvailable -> stringResource(R.string.today_sub_new_available)
        TodaySubtitle.AllLearned -> stringResource(R.string.today_sub_all_learned)
    }

@Composable
private fun heroHintText(decisions: TodayDecisions, stats: StudyStats?): String? =
    when (decisions.heroHint) {
        null -> null
        TodayHeroHint.NewBlocked -> when (decisions.newBlock) {
            TodayNewBlock.AllLearned -> stringResource(R.string.today_hint_all_learned)
            TodayNewBlock.ReviewBacklog -> stringResource(R.string.today_hint_backlog)
            TodayNewBlock.None -> null
        }
        TodayHeroHint.QuotaAdjusted -> decisions.quotaAdjustment?.let { (due, limit) ->
            stringResource(R.string.today_hint_quota, due, limit)
        }
        TodayHeroHint.NothingToReview -> stringResource(R.string.today_hint_nothing_to_review)
    }
