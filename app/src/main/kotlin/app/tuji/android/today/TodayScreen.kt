package app.tuji.android.today

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import app.tuji.android.R
import app.tuji.android.assistant.AnimatedMascot
import app.tuji.android.assistant.AssistantCreditLink
import app.tuji.android.assistant.AssistantHomeFeature
import app.tuji.android.assistant.MoiraFeatureBand
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiRollingNumber
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.study.CompletionReadout
import app.tuji.android.core.study.TodayCall
import app.tuji.android.core.study.TodayDecisions
import app.tuji.android.core.study.TodayFooter
import app.tuji.android.core.study.TodayHeading
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.tour.TourTarget
import app.tuji.android.tour.tourAnchor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 今日 — iOS's `TodayView`, after 助手 was merged into it.
 *
 * 問候 → 墨塊 → Moira and her four ways in. Nothing below.
 *
 * The ink block was folded once. It used to be two bars (今日目標, 主題進度),
 * two buttons side by side (複習, 學新字) and a line explaining why one was
 * grey. Now it is a title, one total bar, one button and at most one line:
 *
 *  1. One button. [TodayDecisions.call] picks the mode — review when anything
 *     is due, new words otherwise. Someone with reviews due can no longer pick
 *     new words instead; the line under the button (`SwitchToNew`) is the way
 *     out.
 *  2. 今日目標 is gone and the progress bar counts every word in the picked
 *     themes. 今日 has no daily loop now — a known trade, not an omission.
 *  3. The line is [TodayDecisions.footer]: always another thing you can do,
 *     never why a button will not press.
 *
 * Three unlabelled icons ride the block: ⚙ beside the title changes the
 * themes, the trend line top-right opens 學習統計, ⓘ bottom-right opens
 * 記憶曲線.
 *
 * **No shared horizontal padding.** The ink block and the band bleed to the
 * screen edges and each section owns its own margin.
 */
@Composable
fun TodayScreen(
    inputs: TodayInputs,
    name: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onStudy: (StudyMode) -> Unit,
    onSearch: () -> Unit,
    /** 主題進度, answered the way 我 answers 完成度. */
    completion: CompletionReadout? = null,
    /** A theme id's name in the interface language, or null if unknown. */
    themeName: (String) -> String? = { null },
    /** 目前連勝, or 0 before the progress readout lands. */
    streak: Int = 0,
    /** The chip opens 打卡. */
    onStreak: () -> Unit = {},
    /** The chip's dot: points a tap would collect, and nothing else. */
    streakBadge: Boolean = false,
    onOpenStudyThemes: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenStatement: () -> Unit = {},
    /** 解鎖全部主題: the membership page. */
    onUnlock: () -> Unit = {},
    onOpenFeature: (AssistantHomeFeature) -> Unit = {},
    /** Another tab or a pushed screen is in front: the cat stops breathing. */
    mascotPaused: Boolean = false,
) {
    val decisions = TodayDecisions(inputs)
    var showingCurve by rememberSaveable { mutableStateOf(false) }

    // Padding, not spacers: a spacer is a child, so the column's gap would land
    // on either side of it as well. iOS pads the stack: s3 above, s4 below.
    Column(
        Modifier.padding(top = TujiSpace.S3, bottom = bottomPadding + TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        Greeting(
            name = name,
            streak = streak,
            onStreak = onStreak,
            streakBadge = streakBadge,
            onSearch = onSearch,
            onOpenStatement = onOpenStatement,
            modifier = Modifier.padding(horizontal = TujiSpace.S4),
        )
        Hero(
            decisions = decisions,
            themeName = themeName,
            onStudy = onStudy,
            onOpenStudyThemes = onOpenStudyThemes,
            onOpenStats = onOpenStats,
            onUnlock = onUnlock,
            onOpenCurve = { showingCurve = true },
        )
        MoiraSection(onOpenFeature, mascotPaused)
    }

    if (showingCurve) ReviewCurveSheet(onDismiss = { showingCurve = false })
}

/**
 * The merged 助手 home: a breathing cat over her four ways in. The cat says
 * whose they are, so the cells carry titles and no detail.
 */
@Composable
private fun MoiraSection(onOpenFeature: (AssistantHomeFeature) -> Unit, paused: Boolean) {
    Column(
        Modifier.fillMaxWidth().tourAnchor(TourTarget.Assistant),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        // Her 580-unit canvas has ~24dp of nothing under the contact shadow.
        // It did not matter at the bottom of 助手; here it pushes the band down,
        // so the slot is laid out 24dp shorter than she is drawn.
        AnimatedMascot(
            paused = paused,
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val height = 236.dp.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, height - 24.dp.roundToPx()) { placeable.place(0, 0) }
                },
        )
        MoiraFeatureBand(onOpen = onOpenFeature)
    }
}

@Composable
private fun Greeting(
    name: String?,
    streak: Int,
    onStreak: () -> Unit,
    streakBadge: Boolean,
    onSearch: () -> Unit,
    onOpenStatement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        // Search rides the date line, not the headline: the headline is one
        // flowing line precisely so a long localisation wraps.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dateLabel(),
                style = TujiType.label.copy(letterSpacing = 2.sp),
                // 墨2, not 墨3: 墨3 on paper is 3.6:1 for 13sp type.
                color = TujiColor.Ink2,
            )
            Spacer(Modifier.weight(1f).width(TujiSpace.S3))
            val searchLabel = stringResource(R.string.search_open)
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(TujiColor.Paper)
                    .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.3f), CircleShape)
                    .tujiClickable(onClick = onSearch)
                    .semantics { contentDescription = searchLabel }
                    .padding(TujiSpace.S2),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Search(size = 16.dp, tint = TujiColor.Ink2)
            }
            Spacer(Modifier.width(TujiSpace.S3))
            // The can beside the flame: both are your counters.
            AssistantCreditLink(onOpenStatement)
            Spacer(Modifier.width(TujiSpace.S3))
            StreakChip(streak, streakBadge, onStreak)
        }

        // One string with the name inside it, so the whole greeting wraps as
        // one line. The subtitle under it went: most of its cases spoke about
        // 今日目標, which is no longer on screen, and the rest moved into the
        // ink block's own title, button and line.
        Text(
            run {
                val shown = name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.today_fallback_name)
                val whole = stringResource(greetingPrefix(), shown)
                val at = whole.indexOf(shown)
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
    }
}

/**
 * The date in the interface language, as an overline. The locale comes from
 * the configuration, which is observable, not `Locale.getDefault()`.
 */
@Composable
private fun dateLabel(): String {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.ROOT
    }
    // A skeleton, not a pattern, so the locale places the parts.
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEMMMd") }
    return LocalDate.now()
        .format(DateTimeFormatter.ofPattern(pattern, locale))
        .uppercase(locale)
}

/**
 * 連勝, on the date line. The flame is 積累 once there is a run and 墨3 at
 * zero. It opens 打卡; its dot means points a tap would collect, nothing else.
 */
@Composable
private fun StreakChip(days: Int, badge: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.checkin_title)
    val value = stringResource(R.string.today_streak, days)
    val hint = if (badge) stringResource(R.string.checkin_badge_hint) else null
    Box(modifier) {
        Row(
            Modifier
                .background(TujiColor.Paper)
                .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.3f))
                .tujiClickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(title, value, hint).joinToString(", ")
                    role = Role.Button
                }
                .padding(horizontal = TujiSpace.S3, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TujiGlyph.Flame(size = 12.dp, tint = if (days > 0) TujiColor.Accumulation else TujiColor.Ink3)
            TujiRollingNumber("$days", style = TujiType.bodySmStrong, color = TujiColor.Ink)
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TujiColor.Alert),
            )
        }
    }
}

@Composable
private fun greetingPrefix(): Int = when (LocalTime.now().hour) {
    in 5..10 -> R.string.today_greeting_morning
    in 11..17 -> R.string.today_greeting_afternoon
    else -> R.string.today_greeting_evening
}

// MARK: - 墨塊

@Composable
private fun Hero(
    decisions: TodayDecisions,
    themeName: (String) -> String?,
    onStudy: (StudyMode) -> Unit,
    onOpenStudyThemes: () -> Unit,
    onOpenStats: () -> Unit,
    onUnlock: () -> Unit,
    onOpenCurve: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Ink)
            .tourAnchor(TourTarget.Hero)
            .padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        HeroHeading(decisions.heading, themeName, onOpenStudyThemes, onOpenStats)
        HeroProgress(decisions.completion)
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            StudyButton(decisions.call, onStudy, onOpenStudyThemes)
            HeroFootRow(decisions.footer, onStudy, onOpenStudyThemes, onUnlock, onOpenCurve)
        }
    }
}

/** Which themes, plus changing them and seeing the numbers. */
@Composable
private fun HeroHeading(
    heading: TodayHeading,
    themeName: (String) -> String?,
    onOpenStudyThemes: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val text = when (heading) {
        // Settings not here yet: 「還沒選學習主題」 would be contradicted a
        // moment later, so a noun that claims nothing.
        TodayHeading.Unknown -> stringResource(R.string.today_heading_unknown)
        TodayHeading.NoThemes -> stringResource(R.string.today_heading_no_themes)
        is TodayHeading.Themes -> {
            val name = themeName(heading.firstId)
            when {
                name == null -> stringResource(R.string.today_heading_count, heading.count)
                heading.count > 1 -> stringResource(R.string.today_heading_themes, name, heading.count)
                else -> name
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The title and its ⚙ share everything left of the trend icon, so the
        // ⚙ hugs the title and the trend icon stays in the corner, on the same
        // vertical line as ⓘ below. A long title wraps before it moves either.
        Row(Modifier.weight(1f).padding(end = TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = TujiType.h3, color = TujiColor.Paper, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
            InkIcon(stringResource(R.string.today_themes_all), onOpenStudyThemes) {
                TujiGlyph.Sliders(size = 16.dp, tint = TujiColor.Paper.copy(alpha = 0.75f))
            }
        }
        InkIcon(stringResource(R.string.today_stats), onOpenStats) {
            TujiGlyph.TrendChart(size = 17.dp, tint = TujiColor.Paper.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun InkIcon(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .tujiClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/**
 * Every word in the picked themes, bar left and number right. 積累 rather than
 * 瞳黃: it says how much has built up, and 瞳黃 on this block now means only
 * "the next step is here". The pale step, since deep teal on ink is 3.04:1.
 */
@Composable
private fun HeroProgress(readout: CompletionReadout?) {
    val label = stringResource(R.string.today_theme_progress)
    val value = stringResource(R.string.today_progress_words, readout?.seen ?: 0, readout?.total ?: 0)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = label
            stateDescription = value
        },
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TujiProgressBar(
            progress = readout?.ratio ?: 0.0,
            track = TujiColor.Paper.copy(alpha = 0.2f),
            fill = TujiColor.AccumulationSoft,
            modifier = Modifier.weight(1f),
        )
        TujiRollingNumber(value, style = TujiType.bodySmStrong, color = TujiColor.AccumulationSoft)
    }
}

/** One button. On its right, what it will do and how many. */
@Composable
private fun StudyButton(call: TodayCall, onStudy: (StudyMode) -> Unit, onOpenStudyThemes: () -> Unit) {
    val study = stringResource(R.string.today_study)
    when (call) {
        TodayCall.PickThemes ->
            StudyCall(stringResource(R.string.today_pick_themes), null, onClick = onOpenStudyThemes)
        // Stats not here: no number to give. The button still works — the
        // session fetches its own queue and has its own empty state.
        TodayCall.Unknown -> StudyCall(study, null, onClick = { onStudy(StudyMode.Review) })
        is TodayCall.Study -> StudyCall(
            study,
            when (call.mode) {
                StudyMode.Review -> stringResource(R.string.today_call_review, call.count)
                StudyMode.New -> stringResource(R.string.today_call_new, call.count)
            },
            onClick = { onStudy(call.mode) },
        )
        is TodayCall.Blocked -> StudyCall(study, null, onClick = null)
    }
}

/** 瞳黃 with ink type; a press changes the ground and nothing else. Null [onClick] is the greyed state. */
@Composable
private fun StudyCall(title: String, trailing: String?, onClick: (() -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val enabled = onClick != null
    val ink = if (enabled) TujiColor.Ink else TujiColor.Paper.copy(alpha = 0.4f)
    Row(
        Modifier
            .fillMaxWidth()
            .tourAnchor(TourTarget.HeroCtas)
            .background(
                when {
                    !enabled -> TujiColor.Paper.copy(alpha = 0.08f)
                    pressed -> TujiColor.CurrentDeep
                    else -> TujiColor.Current
                },
            )
            .tujiClickable(enabled = enabled, interactionSource = source, onClick = onClick ?: {})
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(title, trailing).joinToString(", ")
                if (enabled) role = Role.Button
            }
            .padding(vertical = TujiSpace.S3, horizontal = TujiSpace.S3 + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TujiType.h3, color = ink, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = TujiType.bodySmStrong, color = ink.copy(alpha = ink.alpha * 0.75f))
        }
    }
}

/**
 * Under the button: at most one line in the middle, 記憶曲線 bottom-right.
 * The row is always there, so the block's height does not jump with the day.
 */
@Composable
private fun HeroFootRow(
    footer: TodayFooter?,
    onStudy: (StudyMode) -> Unit,
    onOpenStudyThemes: () -> Unit,
    onUnlock: () -> Unit,
    onOpenCurve: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        if (footer != null) {
            val (text, action) = when (footer) {
                TodayFooter.SwitchToNew -> stringResource(R.string.today_learn_new) to { onStudy(StudyMode.New) }
                TodayFooter.ChangeThemes -> stringResource(R.string.today_change_themes) to onOpenStudyThemes
                // A non-member through every free theme: 換幾個主題 would lead to
                // a row of padlocks that each open the paywall. Open it here.
                TodayFooter.UnlockAllThemes -> stringResource(R.string.today_unlock_all_themes) to onUnlock
            }
            Text(
                text,
                style = TujiType.label,
                color = TujiColor.Paper.copy(alpha = 0.85f),
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    // Clear of the icon in the corner, at the largest text too.
                    .padding(horizontal = 40.dp)
                    .tujiClickable(onClick = action)
                    .semantics { role = Role.Button }
                    // 13sp type is 18dp tall; the padding makes the target 44.
                    .padding(horizontal = TujiSpace.S3, vertical = 13.dp),
            )
        }
        // It explains "why review first", so it sits beside the button that
        // says 「複習 26」. Pulled 6dp out so its glyph lines up with the
        // title row's icons; the extra lands in the block's own margin.
        val curveLabel = stringResource(R.string.today_review_curve)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp)
                .size(44.dp)
                .tujiClickable(onClick = onOpenCurve)
                .clearAndSetSemantics {
                    contentDescription = curveLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.ExclamationCircle(size = 17.dp, tint = TujiColor.Paper.copy(alpha = 0.75f))
        }
    }
}
