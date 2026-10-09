package app.tuji.android.checkin

import app.tuji.android.form.TujiSheetHeader
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.account.CheckInStore
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiRollingNumber
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.CheckInDecision.Reward
import app.tuji.android.core.model.MonthGrid
import app.tuji.android.core.model.StudyCalendarMonth
import app.tuji.android.core.model.StudyStreak
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 打卡 — opened from 今日's streak chip. iOS's `CheckInSheet`.
 *
 * Three blocks: the streak, today's points, and the month. Studying is the
 * check-in (one word-card answer, the rule the streak already used), so the
 * calendar is the streak drawn out day by day, and the points card is only the
 * tap that collects what today's studying earned. The decisions are in
 * `CheckInDecision`; this file is the drawing.
 *
 * No flame, no gradient, no medal. A filled square is a day you studied, in
 * 累積 blue like every other "what you have built up" mark in the app, and
 * today wears the 瞳黃 focus stroke because it is the day still in play.
 */
@Composable
fun CheckInSheet(
    store: CheckInStore,
    /** The streak 今日 already has, shown until the calendar's own copy lands. */
    fallbackStreak: StudyStreak?,
    onUpgrade: () -> Unit,
    /** Close the sheet and start studying. */
    onStudy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by store.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberTujiHaptics()
    val streak = state.calendar?.streak ?: fallbackStreak
    val reward = state.reward(fallbackStudiedToday = (streak?.todayCount ?: 0) > 0)

    LaunchedEffect(Unit) {
        launch { store.loadReward() }
        launch { store.loadCalendar() }
    }

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
                    .heightIn(max = 640.dp)
                    .background(TujiColor.Paper)
                    // Swallows taps so one on the sheet does not dismiss it
                    // through the scrim underneath.
                    .tujiClickable {}
                    .navigationBarsPadding(),
            ) {
                TujiSheetHeader(title = stringResource(R.string.checkin_title), closeEnabled = true, onClose = onDismiss)
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3, bottom = TujiSpace.S5),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
                ) {
                    Hero(streak)
                    if (reward != Reward.Hidden) {
                        RewardCard(
                            reward = reward,
                            claiming = state.claiming,
                            claimFailed = state.claimFailed,
                            onClaim = { scope.launch { if (store.claim()) haptics.success() } },
                            onUpgrade = onUpgrade,
                            onStudy = onStudy,
                        )
                    }
                    CalendarSection(
                        month = state.calendar,
                        failed = state.calendarFailed,
                        canShowEarlier = state.canShowEarlier,
                        canShowLater = state.canShowLater,
                        onMonth = { offset -> scope.launch { store.showMonth(offset) } },
                        onRetry = { scope.launch { store.loadCalendar() } },
                    )
                }
            }
        }
    }
}

// Streak

@Composable
private fun Hero(streak: StudyStreak?) {
    val current = streak?.current ?: 0
    val unit = stringResource(R.string.checkin_streak_unit)
    val label = stringResource(R.string.checkin_streak_label)
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label $current $unit" },
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(label, style = TujiType.label.copy(letterSpacing = TujiType.label.letterSpacing * 4), color = TujiColor.Ink3)
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1), verticalAlignment = Alignment.Bottom) {
                TujiRollingNumber(
                    text = "$current",
                    style = TujiType.display,
                    color = if (current > 0) TujiColor.Accumulation else TujiColor.Ink3,
                )
                Text(unit, style = TujiType.h2, color = TujiColor.Ink, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        MascotFigure(pose = if ((streak?.todayCount ?: 0) > 0) MascotPose.Cheer else MascotPose.Wave, size = 88.dp)
    }
}

// Today's points

@Composable
private fun RewardCard(
    reward: Reward,
    claiming: Boolean,
    claimFailed: Boolean,
    onClaim: () -> Unit,
    onUpgrade: () -> Unit,
    onStudy: () -> Unit,
) {
    val title = when (reward) {
        is Reward.Locked, Reward.Hidden -> R.string.checkin_reward_title
        is Reward.NeedsStudy -> R.string.checkin_needs_study_title
        is Reward.Claimable, Reward.Claimed -> R.string.checkin_done_title
        is Reward.Capped -> R.string.checkin_capped_title
    }
    val detail = when (reward) {
        Reward.Hidden -> null
        is Reward.Locked -> stringResource(R.string.checkin_locked_detail, reward.daily)
        is Reward.NeedsStudy -> stringResource(R.string.checkin_needs_study_detail, reward.daily)
        is Reward.Claimable -> stringResource(R.string.checkin_claimable, reward.points)
        Reward.Claimed -> stringResource(R.string.checkin_claimed)
        is Reward.Capped -> stringResource(R.string.checkin_capped, reward.cap)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper2)
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        // One row while the sentence fits beside the button; the button drops
        // underneath once it doesn't (ja / en, large type) rather than
        // squeezing the sentence into a narrow column — iOS's ViewThatFits.
        LabelThenAction(
            label = {
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.credit_can), contentDescription = null, modifier = Modifier.size(44.dp), tint = TujiColor.Ink)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(title), style = TujiType.bodyStrong, color = TujiColor.Ink)
                        detail?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
                    }
                }
            },
        ) {
            when (reward) {
                is Reward.Locked -> Pill(stringResource(R.string.checkin_upgrade), primary = false, onClick = onUpgrade)
                is Reward.NeedsStudy -> Pill(stringResource(R.string.checkin_study), primary = true, onClick = onStudy)
                is Reward.Claimable -> Pill(stringResource(R.string.checkin_claim, reward.points), primary = true, enabled = !claiming, onClick = onClaim)
                Reward.Claimed -> {
                    val claimed = stringResource(R.string.checkin_claimed_mark)
                    TujiGlyph.Check(size = 18.dp, tint = TujiColor.Accumulation, modifier = Modifier.semantics { contentDescription = claimed })
                }
                Reward.Hidden, is Reward.Capped -> Unit
            }
        }
        if (claimFailed) {
            Text(stringResource(R.string.checkin_claim_failed), style = TujiType.label, color = TujiColor.Alert)
        }
    }
}

/**
 * [label] and [action] side by side when the label's natural width fits next
 * to the action; otherwise stacked, action underneath and left-aligned.
 */
@Composable
private fun LabelThenAction(label: @Composable () -> Unit, action: @Composable () -> Unit) {
    val gap = TujiSpace.S3
    val stackGap = TujiSpace.S3
    Layout(contents = listOf(label, action)) { (labelMeasurables, actionMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actions = actionMeasurables.map { it.measure(loose) }
        val actionWidth = actions.maxOfOrNull { it.width } ?: 0
        val actionHeight = actions.maxOfOrNull { it.height } ?: 0
        val gapPx = if (actions.isEmpty() || actionWidth == 0) 0 else gap.roundToPx()
        val natural = labelMeasurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val width = constraints.maxWidth
        if (natural + gapPx + actionWidth <= width) {
            val labels = labelMeasurables.map { it.measure(loose.copy(maxWidth = width - gapPx - actionWidth)) }
            val labelHeight = labels.maxOfOrNull { it.height } ?: 0
            val height = maxOf(labelHeight, actionHeight)
            layout(width, height) {
                labels.forEach { it.placeRelative(0, (height - it.height) / 2) }
                actions.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
            }
        } else {
            val labels = labelMeasurables.map { it.measure(loose) }
            val labelHeight = labels.maxOfOrNull { it.height } ?: 0
            val spacing = if (actionHeight == 0) 0 else stackGap.roundToPx()
            layout(width, labelHeight + spacing + actionHeight) {
                labels.forEach { it.placeRelative(0, 0) }
                actions.forEach { it.placeRelative(0, labelHeight + spacing) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodyStrong,
        color = TujiColor.Ink,
        maxLines = 1,
        modifier = Modifier
            .background(if (primary) TujiColor.BrandPrimary else TujiColor.Paper)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
    )
}

// Month

@Composable
private fun CalendarSection(
    month: StudyCalendarMonth?,
    failed: Boolean,
    canShowEarlier: Boolean,
    canShowLater: Boolean,
    onMonth: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { ConfigurationCompat.getLocales(configuration)[0] ?: Locale.ROOT }
    val shown = month?.month ?: currentMonth()
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(monthTitle(shown, locale), style = TujiType.h3, color = TujiColor.Ink, modifier = Modifier.weight(1f))
            MonthButton(left = true, label = stringResource(R.string.checkin_prev_month), enabled = canShowEarlier) { onMonth(-1) }
            MonthButton(left = false, label = stringResource(R.string.checkin_next_month), enabled = canShowLater) { onMonth(1) }
        }
        if (failed) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(stringResource(R.string.checkin_calendar_failed), style = TujiType.bodySm, color = TujiColor.Ink2)
                Text(
                    stringResource(R.string.checkin_retry),
                    style = TujiType.bodyStrong,
                    color = TujiColor.Ink,
                    modifier = Modifier.tujiClickable(onClick = onRetry),
                )
            }
        } else {
            StudyMonthGrid(month, shown, locale)
        }
    }
}

@Composable
private fun MonthButton(left: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) TujiColor.Ink2 else TujiColor.Ink3.copy(alpha = 0.4f)
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = label }
            .tujiClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // iOS's chevron.left / chevron.right: paging a month, not going back.
        TujiGlyph.ChevronUp(size = 18.dp, tint = tint, modifier = Modifier.rotate(if (left) -90f else 90f))
    }
}

/**
 * The month as squares. Before the first answer arrives it draws the current
 * month empty, so the sheet does not jump when the data lands.
 */
@Composable
private fun StudyMonthGrid(month: StudyCalendarMonth?, shown: String, locale: Locale) {
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val grid = remember(shown, firstDay) { MonthGrid.of(shown, firstDay) } ?: return
    val studied = remember(month) { month?.studiedDays.orEmpty().toSet() }
    val studiedLabel = stringResource(R.string.checkin_studied_day)
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            for (i in 0 until 7) {
                val day = firstDay.plus(i.toLong())
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        grid.cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    if (day == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = grid.date(day)
                        DayCell(
                            day = day,
                            studied = date in studied,
                            today = date == month?.today,
                            future = month?.let { date > it.today } ?: false,
                            studiedLabel = studiedLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, studied: Boolean, today: Boolean, future: Boolean, studiedLabel: String, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (studied) TujiColor.Accumulation else TujiColor.Paper)
            .then(if (today) Modifier.border(TujiBorder.Bw2, TujiColor.Current) else Modifier)
            .clearAndSetSemantics {
                contentDescription = "$day"
                if (studied) stateDescription = studiedLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = TujiType.bodySmStrong,
            color = when {
                studied -> TujiColor.Paper
                future -> TujiColor.Ink3.copy(alpha = 0.5f)
                else -> TujiColor.Ink2
            },
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** YYYY-MM on the phone's calendar — the zone every request states. */
private fun currentMonth(): String {
    val now = LocalDate.now()
    return String.format(Locale.ROOT, "%04d-%02d", now.year, now.monthValue)
}

private fun monthTitle(month: String, locale: Locale): String {
    val ym: YearMonth = MonthGrid.parse(month) ?: return month
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    return ym.atDay(15).format(DateTimeFormatter.ofPattern(pattern, locale))
}
