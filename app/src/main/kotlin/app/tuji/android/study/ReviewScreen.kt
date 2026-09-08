package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.StudyOptionRow
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.headwordDisplay
import app.tuji.android.core.study.ReviewFlash
import app.tuji.android.core.study.ReviewPhase
import app.tuji.android.core.study.ReviewRevealMode
import app.tuji.android.core.study.ReviewSession
import app.tuji.android.core.study.StudyOptionState
import coil3.compose.AsyncImage

/**
 * 複習. A picture, four words, and — when the answer is not obvious — a sheet
 * asking the user how it felt.
 *
 * Every decision on this screen belongs to `ReviewSession`; this reads its
 * state and calls back. That is why there is no `if` here about ratings,
 * requeues or progress.
 */
@Composable
fun ReviewScreen(
    vm: ReviewViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val insets = WindowInsets.systemBars.asPaddingValues()

    Box(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        when (val s = state) {
            is ReviewViewModel.State.Loading -> Centered(stringResource(R.string.study_loading))
            is ReviewViewModel.State.Failed ->
                Centered(stringResource(R.string.study_failed), TujiColor.Alert)

            is ReviewViewModel.State.Done -> CompleteView(
                session = s.session,
                unsynced = s.unsynced,
                topPadding = insets.calculateTopPadding(),
                bottomPadding = insets.calculateBottomPadding(),
                onClose = onClose,
            )

            is ReviewViewModel.State.Studying -> {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(insets.calculateTopPadding()))
                    Header(
                        progress = s.session.progress,
                        unsynced = s.unsynced,
                        onClose = {
                            // Drop the pending beat first: leaving during the
                            // pause must not raise a sheet over the screen the
                            // user just went to.
                            vm.leave()
                            onClose()
                        },
                    )
                    QuestionBody(
                        state = s,
                        onPick = vm::pick,
                        onToggleHint = vm::toggleHint,
                        bottomPadding = insets.calculateBottomPadding(),
                    )
                }
                s.revealMode?.let { mode ->
                    RevealSheet(
                        session = s.session,
                        mode = mode,
                        bottomPadding = insets.calculateBottomPadding(),
                        onRate = vm::rate,
                        onContinue = vm::continueFromReveal,
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String, color: androidx.compose.ui.graphics.Color = TujiColor.Ink3) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = color)
    }
}

@Composable
private fun Header(progress: Double, unsynced: Int, onClose: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.study_leave),
                style = TujiType.bodySmStrong,
                color = TujiColor.Ink2,
                modifier = Modifier.tujiClickable(onClick = onClose).padding(TujiSpace.S1),
            )
            if (unsynced > 0) {
                // Said out loud rather than swallowed: these answers are on
                // disk and will replay, and a silent count is how iOS's
                // predecessor lost them.
                Text(
                    stringResource(R.string.study_unsynced, unsynced),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                )
            }
        }
        // 3dp, the selection width — a progress bar is a selection of how far
        // along the session is, and 紙與墨 has no other way to draw one.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TujiBorder.Bw3)
                .background(TujiColor.Paper3),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.toFloat())
                    .height(TujiBorder.Bw3)
                    .background(TujiColor.Current),
            )
        }
    }
}

@Composable
private fun QuestionBody(
    state: ReviewViewModel.State.Studying,
    onPick: (String) -> Unit,
    onToggleHint: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val question = state.session.question ?: return
    val item = question.item
    val revealed = question.phase == ReviewPhase.Review

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Spacer(Modifier.height(TujiSpace.S3))

        HeroCard(item = item, faceUp = question.hintFaceUp, onFlip = onToggleHint)

        state.flash?.let { FlashLine(it) }

        state.choices.forEachIndexed { i, label ->
            StudyOptionRow(
                label = label,
                letter = ('A' + i).toString(),
                state = StudyOptionState.forOption(
                    label = label,
                    answer = item.word.word,
                    picked = question.picked?.label,
                    revealed = revealed,
                    wrongPicks = question.wrongPicks,
                ),
                enabled = !revealed && label !in question.wrongPicks,
                onClick = { onPick(label) },
            )
        }
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}

/**
 * The picture, and the gloss on its back.
 *
 * The flip is 求救提示: it costs the wrong-answer rating table (ADR-0007), which
 * is why it is a deliberate tap on the picture rather than a button that could
 * be pressed by accident.
 */
@Composable
private fun HeroCard(item: StudyQueueItem, faceUp: Boolean, onFlip: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onFlip),
        contentAlignment = Alignment.Center,
    ) {
        if (faceUp) {
            Text(
                // The 釋義 when the catalogue has one; the gloss otherwise. For
                // a zh reader the gloss alone is the answer translated, which
                // is why the definition is preferred.
                item.word.definition ?: item.word.chinese,
                style = TujiType.body,
                color = TujiColor.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(TujiSpace.S4),
            )
        } else {
            AsyncImage(
                model = item.word.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(TujiSpace.S3),
            )
        }
    }
}

@Composable
private fun FlashLine(flash: ReviewFlash) {
    val text = when (flash) {
        is ReviewFlash.RetestPassed -> stringResource(R.string.study_retest_passed)
        is ReviewFlash.AutoRated -> stringResource(R.string.study_auto_rated, flash.rating.label())
    }
    Text(text, style = TujiType.label, color = TujiColor.Ink3)
}

/**
 * The sheet. Its buttons come from `ReviewQuestion.availableRatings`, which
 * refuses to offer 穩定/熟練 for an answer the user needed a hint for.
 */
@Composable
private fun RevealSheet(
    session: ReviewSession,
    mode: ReviewRevealMode,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onRate: (SRSRating) -> Unit,
    onContinue: () -> Unit,
) {
    val question = session.question ?: return
    Box(Modifier.fillMaxSize().background(TujiColor.Scrim), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                // The sheet's top edge is a selection indicator, which is the
                // one thing a 3dp border means.
                .padding(top = TujiBorder.Bw3)
                .padding(horizontal = TujiSpace.S4)
                .padding(top = TujiSpace.S4, bottom = bottomPadding + TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Headword(question.item)
            Text(question.item.word.chinese, style = TujiType.bodySm, color = TujiColor.Ink3)
            Spacer(Modifier.height(TujiSpace.S2))

            when (mode) {
                ReviewRevealMode.ContinueOnly -> TujiButton(
                    text = stringResource(R.string.study_next),
                    onClick = onContinue,
                )
                ReviewRevealMode.Rate -> question.availableRatings.forEach { rating ->
                    TujiButton(
                        text = rating.label(),
                        style = if (rating == question.suggested) {
                            TujiButtonStyle.Primary
                        } else {
                            TujiButtonStyle.Secondary
                        },
                        onClick = { onRate(rating) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Headword(item: StudyQueueItem) {
    when (val display = item.word.headwordDisplay(item.word.targetLanguage ?: TargetLanguage.EN)) {
        is HeadwordDisplay.Ruby -> FuriganaHeadword(display.segments)
        is HeadwordDisplay.Line -> {
            Text(item.word.word, style = TujiType.headword(26.sp()), color = TujiColor.Ink)
            Text(display.text, style = TujiType.bodySm, color = TujiColor.Ink2)
        }
        HeadwordDisplay.Plain ->
            Text(item.word.word, style = TujiType.headword(26.sp()), color = TujiColor.Ink)
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp,
)

@Composable
private fun SRSRating.label(): String = stringResource(
    when (this) {
        SRSRating.Again -> R.string.rating_again
        SRSRating.Hard -> R.string.rating_hard
        SRSRating.Good -> R.string.rating_good
        SRSRating.Easy -> R.string.rating_easy
    }
)

@Composable
private fun CompleteView(
    session: ReviewSession,
    unsynced: Int,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Spacer(Modifier.height(topPadding + TujiSpace.S6))
        Text(stringResource(R.string.study_done_title), style = TujiType.h1, color = TujiColor.Ink)
        Text(
            stringResource(R.string.study_done_count, session.passedCount),
            style = TujiType.body,
            color = TujiColor.Ink2,
        )
        if (unsynced > 0) {
            Text(
                stringResource(R.string.study_unsynced_done, unsynced),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
            )
        }
        Spacer(Modifier.height(TujiSpace.S3))
        session.answered.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = TujiSpace.S1),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                Text(item.word.word, style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(item.word.chinese, style = TujiType.bodySm, color = TujiColor.Ink3)
                if (item.word.id in session.retriedIds) {
                    Text(
                        stringResource(R.string.study_was_wrong),
                        style = TujiType.label,
                        color = TujiColor.Alert,
                    )
                }
            }
        }
        Spacer(Modifier.height(TujiSpace.S4))
        TujiButton(text = stringResource(R.string.study_close), onClick = onClose)
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}
