package app.tuji.android.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.StudyStats
import app.tuji.android.core.study.TodayDecisions
import app.tuji.android.core.study.TodayHeroHint
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.core.study.TodayNewBlock
import app.tuji.android.core.study.TodaySubtitle
import java.time.LocalTime

/**
 * 今日 — what there is to do, and how far through it the day is.
 *
 * Every verdict on this screen comes from [TodayDecisions]; the screen owns the
 * words and nothing else. That is what keeps a wrong line from being a wrong
 * *rule* — and it is why the copy below is a `when` over an enum rather than
 * the seven-branch condition it replaced on iOS.
 */
@Composable
fun TodayScreen(
    inputs: TodayInputs,
    name: String?,
    onReview: () -> Unit,
    onLearnNew: () -> Unit,
) {
    val decisions = TodayDecisions(inputs)
    val stats = inputs.stats

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Greeting(name = name, subtitle = subtitleText(decisions, stats))

        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            TujiButton(
                text = stringResource(R.string.study_start_review),
                onClick = onReview,
                enabled = !decisions.reviewDisabled,
                modifier = Modifier.fillMaxWidth(),
            )
            TujiButton(
                text = stringResource(R.string.new_start),
                style = TujiButtonStyle.Secondary,
                onClick = onLearnNew,
                enabled = !decisions.newDisabled,
                modifier = Modifier.fillMaxWidth(),
            )
            // Exactly one of the three may speak, and none before the numbers
            // land — a hint about today, shown before today exists, is a guess.
            heroHintText(decisions, stats)?.let {
                Text(
                    it,
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!inputs.isGuest) {
            GoalCard(decisions = decisions, inputs = inputs)
        }
    }
}

@Composable
private fun Greeting(name: String?, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Text(
            stringResource(greetingPrefix()) + (name ?: ""),
            style = TujiType.h2,
            color = TujiColor.Ink,
        )
        Text(subtitle, style = TujiType.body, color = TujiColor.Ink2)
    }
}

/** Local time, because "morning" is about where the user is, not the server. */
private fun greetingPrefix(): Int = when (LocalTime.now().hour) {
    in 5..11 -> R.string.today_greeting_morning
    in 12..17 -> R.string.today_greeting_afternoon
    else -> R.string.today_greeting_evening
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

@Composable
private fun GoalCard(decisions: TodayDecisions, inputs: TodayInputs) {
    val stats = inputs.stats
    val done = stats?.todayNew ?: 0
    val goal = maxOf(1, inputs.dailyGoal)

    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper2)
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.today_goal), style = TujiType.bodySmStrong, color = TujiColor.Ink2)
            if (decisions.goalReached) {
                Text(
                    stringResource(R.string.today_goal_reached_badge),
                    style = TujiType.label,
                    color = TujiColor.Accumulation,
                )
            } else {
                Text(
                    stringResource(R.string.today_goal_progress, done, goal),
                    style = TujiType.monoLabel,
                    color = TujiColor.Ink3,
                )
            }
        }

        // Same 3dp selection rule the session header uses — 紙與墨 has one way
        // to draw "this far along".
        Box(Modifier.fillMaxWidth().height(3.dp).background(TujiColor.Rule)) {
            Box(
                Modifier
                    .fillMaxWidth(minOf(1f, done.toFloat() / goal))
                    .height(3.dp)
                    .background(if (decisions.goalReached) TujiColor.Accumulation else TujiColor.Current),
            )
        }

        stats?.let {
            Text(
                stringResource(R.string.today_seen, it.seen, it.total),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
            )
        }
    }
}
