package app.tuji.android.atlas

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.core.study.MasteryLevel
import app.tuji.android.core.study.ReviewSchedule

/**
 * What the mastery widgets say, in the reader's language.
 *
 * Here rather than in `core:design` because that module has no `values/`
 * the localisation gate reads — a string added there would compile, render in
 * Chinese for every locale, and never appear in the 漏譯 check. Copy lives in
 * the app module; the design layer takes it already resolved.
 */
@Composable
fun MasteryLevel.label(): String = stringResource(
    when (this) {
        MasteryLevel.NotLearned -> R.string.mastery_not_learned
        MasteryLevel.Know -> R.string.mastery_know
        MasteryLevel.Familiar -> R.string.mastery_familiar
        MasteryLevel.Proficient -> R.string.mastery_proficient
        MasteryLevel.Expert -> R.string.mastery_expert
    },
)

/** What a screen reader is told the badge's value is. */
@Composable
fun masterySpoken(score: Int?): String =
    if (score == null) {
        stringResource(R.string.mastery_no_record)
    } else {
        stringResource(R.string.mastery_score, score)
    }

/**
 * 「下次複習 · 約 3 週後」.
 *
 * One string per unit rather than one interpolated sentence: 「3 天後」 and
 * 「約 3 週後」 differ by more than a number in every language this app speaks —
 * Japanese puts the 約 elsewhere and English needs "in", not a suffix.
 */
@Composable
fun nextReviewLabel(untilMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val when_ = when (val c = ReviewSchedule.countdown(untilMs, nowMs)) {
        ReviewSchedule.Countdown.Overdue -> stringResource(R.string.review_due_now)
        is ReviewSchedule.Countdown.Minutes -> stringResource(R.string.review_in_minutes, c.value)
        is ReviewSchedule.Countdown.Hours -> stringResource(R.string.review_in_hours, c.value)
        is ReviewSchedule.Countdown.Days -> stringResource(R.string.review_in_days, c.value)
        is ReviewSchedule.Countdown.Weeks -> stringResource(R.string.review_in_weeks, c.value)
        is ReviewSchedule.Countdown.Months -> stringResource(R.string.review_in_months, c.value)
        is ReviewSchedule.Countdown.Years ->
            stringResource(R.string.review_in_years, String.format("%.1f", c.value))
    }
    return stringResource(R.string.review_next, when_)
}
