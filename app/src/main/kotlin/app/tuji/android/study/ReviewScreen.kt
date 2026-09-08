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
import app.tuji.android.core.design.TujiGlyph
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
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import app.tuji.android.core.model.ReviewQuestionKind
import app.tuji.android.core.model.StudyExample
import app.tuji.android.core.study.ImageChoiceOption
import app.tuji.android.core.study.ReviewQuestion
import app.tuji.android.core.study.maskedSentence
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
                    StudyHeader(
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
                        onPickImage = vm::pickImage,
                        onToggleHint = vm::toggleHint,
                        onReplay = vm::replaySentence,
                        onReveal = vm::revealSentence,
                        onOptOut = vm::optOutOfListening,
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
private fun QuestionBody(
    state: ReviewViewModel.State.Studying,
    onPick: (String) -> Unit,
    onPickImage: (ImageChoiceOption) -> Unit,
    onToggleHint: () -> Unit,
    onReplay: (Float) -> Unit,
    onReveal: () -> Unit,
    onOptOut: () -> Unit,
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

        val example = question.example
        val imageOptions = question.imageOptions
        if (question.kind == ReviewQuestionKind.HearSentence &&
            example != null && imageOptions != null
        ) {
            ListenCard(
                question = question,
                example = example,
                onReplay = onReplay,
                onReveal = onReveal,
            )

            state.flash?.let { FlashLine(it) }

            ImagePair(options = imageOptions, question = question, onPick = onPickImage)

            // An "I cannot hear right now" escape, not an "this is too hard"
            // one — 聽句 is the only question in the app that cannot be
            // answered without audio, and no headphones on a train is not a
            // difficulty problem. Which is also why it carries no rating cost.
            if (!revealed) {
                Text(
                    stringResource(R.string.study_listen_opt_out),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tujiClickable(onClick = onOptOut)
                        .padding(vertical = TujiSpace.S3),
                )
            }
        } else {
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

// MARK: - 聽句

/**
 * 聽句's question: the sentence, hidden, with its audio — and the two pictures
 * to choose between.
 *
 * The sentence is drawn with a plain [Text], never anything tappable. The queue
 * deliberately carries no 詞塊 spans for it, and a sentence that looks tappable
 * and is not is worse than one that never pretended.
 */
@Composable
private fun ListenCard(
    question: ReviewQuestion,
    example: StudyExample,
    onReplay: (Float) -> Unit,
    onReveal: () -> Unit,
) {
    // Readable once the answer is in, or once the eye bought it. Answering
    // removes the reason to hide it: from that moment the sentence is study
    // material, exactly like the answer on the reveal sheet.
    val legible = question.sentenceRevealed || question.phase == ReviewPhase.Review
    val maskedLabel = stringResource(R.string.study_listen_masked)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(TujiColor.Paper2),
    ) {
        // Three states, not two. The blur is the design — it leaves the shape
        // of the words, the line count, where it breaks — but it needs
        // RenderEffect, which is API 31, and `minSdk` is 29. Below that
        // `Modifier.blur` silently draws nothing at all, so those two API
        // levels replace the glyphs instead of covering them.
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Text(
            when {
                legible -> example.sentence
                canBlur -> example.sentence
                else -> maskedSentence(example.sentence)
            },
            style = TujiType.body,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = TujiSpace.S4)
                // 12dp, not something gentler: enough to say "there is a
                // sentence here" without leaving one letter legible.
                .then(if (!legible && canBlur) Modifier.blur(12.dp) else Modifier)
                // The blur is a *visual* effect and a screen reader does not
                // see through it — it reads the sentence out, handing over the
                // answer without the eye ever being pressed and therefore
                // without the rating cost the eye carries. Worse here than a
                // missing label: the sentence names the word the two pictures
                // are asking about. So the sentence is not in the
                // accessibility tree until it is legible, and 顯示例句 next to
                // it is a real labelled control for anyone who wants it.
                .then(
                    if (legible) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics {
                            contentDescription = maskedLabel
                        }
                    },
                ),
        )

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(TujiSpace.S3),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            // 慢讀, drawn as a sibling of the speaker rather than hidden behind
            // a long-press: an affordance nobody can see is found only by the
            // people who did not need it, and someone who needs a sentence read
            // slowly is the least likely to go hunting for a gesture. The label
            // is the number, which needs no translating.
            ListenButton(
                label = stringResource(R.string.study_listen_slow),
                onClick = { onReplay(SLOW_RATE) },
            ) {
                Text("0.8×", style = TujiType.monoLabel, color = TujiColor.Ink2)
            }
            // Always available, and unlimited: a replay does not reset the
            // clock, it spends time that honestly means the word was hard, so
            // there is no reason to make hearing it again feel expensive.
            ListenButton(
                label = stringResource(R.string.study_listen_replay),
                background = if (question.isPlayingSentence) TujiColor.Current else TujiColor.Paper,
                onClick = { onReplay(1f) },
            ) {
                TujiGlyph.Speaker(tint = TujiColor.Ink)
            }
        }

        // Drawn from the first frame, unlike 選字's hint which is deliberately
        // invisible for 8 seconds. That delay compensates for an affordance
        // with nothing on screen to announce it; this one is on screen.
        if (!legible) {
            ListenButton(
                label = stringResource(R.string.study_listen_reveal),
                onClick = onReveal,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(TujiSpace.S3),
            ) {
                TujiGlyph.Eye(tint = TujiColor.Ink2)
            }
        }

        if (question.audioFailed) {
            Text(
                stringResource(R.string.study_listen_failed),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                modifier = Modifier.align(Alignment.TopEnd).padding(TujiSpace.S3),
            )
        }
    }
}

@Composable
private fun ListenButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: androidx.compose.ui.graphics.Color = TujiColor.Paper,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(48.dp)
            .background(background)
            .semantics { contentDescription = label }
            .tujiClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The two pictures, side by side as equal squares.
 *
 * Two rather than four is the whole reason 聽句 never auto-rates: the trade is a
 * 50% floor on guessing, bought so the options can be pictures at a size worth
 * looking at.
 *
 * Each keeps its **word** as its accessibility label. Hiding the sentence
 * removes a shortcut — reading the answer instead of hearing it — but the
 * pictures are not a shortcut, they are the options; labelling them 「選項一／
 * 選項二」 would leave a blind user flipping a coin while the SRS kept score.
 */
@Composable
private fun ImagePair(
    options: List<ImageChoiceOption>,
    question: ReviewQuestion,
    onPick: (ImageChoiceOption) -> Unit,
) {
    val revealed = question.phase == ReviewPhase.Review
    val answerId = question.item.word.id

    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        options.forEach { option ->
            val picked = question.picked?.id == option.id
            val border = when {
                !revealed -> null
                option.id == answerId -> TujiColor.Current
                picked -> TujiColor.Alert
                else -> null
            }
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(TujiColor.Paper2)
                    .then(border?.let { Modifier.border(3.dp, it) } ?: Modifier)
                    .semantics { contentDescription = option.word }
                    .tujiClickable(enabled = !revealed) { onPick(option) },
            ) {
                AsyncImage(
                    model = option.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(TujiSpace.S3),
                )
            }
        }
    }
}

/** 慢讀's multiplier. Slow enough to separate the syllables, not so slow it warbles. */
private const val SLOW_RATE = 0.8f
