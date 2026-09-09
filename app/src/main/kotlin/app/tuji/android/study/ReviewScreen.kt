package app.tuji.android.study

import androidx.compose.foundation.background
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.rememberReduceMotion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import app.tuji.android.core.design.TujiPrompt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.StudyOptionRow
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordImageKind
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
import app.tuji.android.core.study.HintFace
import app.tuji.android.core.study.ImageChoiceOption
import app.tuji.android.core.study.ReviewQuestion
import app.tuji.android.core.study.maskedSentence

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
    var leaving by remember { mutableStateOf(false) }
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
                        label = stringResource(R.string.study_review_label),
                        count = stringResource(
                            R.string.study_count,
                            s.session.passedCount,
                            s.session.originalCount,
                        ),
                        progress = s.session.progress,
                        unsynced = s.unsynced,
                        // Ask, do not leave. The ✕ sits a thumb's width from
                        // the answer buttons, and one mis-tap would otherwise
                        // end a session the user was halfway through.
                        onClose = { leaving = true },
                    )
                    QuestionBody(
                        state = s,
                        onPick = vm::pick,
                        onPickImage = vm::pickImage,
                        onToggleHint = vm::toggleHint,
                        onReplay = vm::replaySentence,
                        onReveal = vm::revealSentence,
                        onOptOut = vm::optOutOfListening,
                        onPlayWord = vm::playWord,
                        bottomPadding = insets.calculateBottomPadding(),
                    )
                }
                s.revealMode?.let { mode ->
                    RevealSheet(
                        session = s.session,
                        mode = mode,
                        bottomPadding = insets.calculateBottomPadding(),
                        playing = s.playingWord,
                        canPlay = s.canPlayWord,
                        onPlay = vm::playWord,
                        onRate = vm::rate,
                        onContinue = vm::continueFromReveal,
                    )
                }
            }
        }

        (state as? ReviewViewModel.State.Studying)?.flash?.let {
            FlashCapsule(flash = it, bottomPadding = insets.calculateBottomPadding())
        }

        if (leaving) {
            TujiPrompt(
                title = stringResource(R.string.study_leave_title),
                message = stringResource(R.string.study_leave_message),
                confirm = stringResource(R.string.study_leave_confirm),
                cancel = stringResource(R.string.study_leave_cancel),
                onConfirm = {
                    // Drop the pending beat first, or it fires after teardown
                    // and raises a sheet over the screen the user went to.
                    leaving = false
                    vm.leave()
                    onClose()
                },
                onCancel = { leaving = false },
            )
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
    onPlayWord: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val question = state.session.question ?: return
    val item = question.item
    val revealed = question.phase == ReviewPhase.Review

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The picture takes what the four options leave, between a floor and a
        // ceiling. A fixed aspect ratio cannot do this: on a short phone a 4:3
        // picture pushes the last option under the fold, and on a tall one it
        // stops short of the space that would have made the rice grains and
        // bottle profiles legible.
        val hero = heroHeight(available = maxHeight - bottomPadding)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            val example = question.example
            val imageOptions = question.imageOptions
            if (question.kind == ReviewQuestionKind.HearSentence &&
                example != null && imageOptions != null
            ) {
                ListenCard(
                    question = question,
                    example = example,
                    height = hero,
                    onReplay = onReplay,
                    onReveal = onReveal,
                )

                Box(Modifier.padding(horizontal = TujiSpace.S4)) {
                    ImagePair(options = imageOptions, question = question, onPick = onPickImage)
                }

                // An "I cannot hear right now" escape, not an "this is too
                // hard" one — 聽句 is the only question in the app that cannot
                // be answered without audio, and no headphones on a train is
                // not a difficulty problem. Which is also why it carries no
                // rating cost. Drawn under the options rather than up by the
                // play button: it is the last resort, and should read after
                // them rather than compete with them.
                if (!revealed) {
                    Text(
                        stringResource(R.string.study_listen_opt_out),
                        style = TujiType.label,
                        color = TujiColor.Ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tujiClickable(onClick = onOptOut)
                            .padding(vertical = TujiSpace.S2),
                    )
                }
            } else {
                // 求助提示 is deliberately invisible — nothing is drawn on the
                // card — so a stalled item offers a line instead. Without it
                // the affordance is undiscoverable, which is what this screen
                // shipped with: `canNudge` existed, had tests, and no view
                // read it.
                //
                // Past the 7s mark where the suggestion has already dropped to
                // 困難, so by the time the line appears flipping only costs the
                // 穩定/熟練 option.
                var nudging by remember(question.item.id, question.startedAtMs) {
                    mutableStateOf(false)
                }
                LaunchedEffect(question.item.id, question.startedAtMs) {
                    nudging = false
                    delay(NUDGE_DELAY_MS)
                    nudging = true
                }

                HeroCard(
                    item = item,
                    faceUp = question.hintFaceUp,
                    height = hero,
                    nudging = nudging && question.canNudge && !state.hintTaught,
                    playing = state.playingWord,
                    canPlay = state.canPlayWord,
                    onPlay = onPlayWord,
                    onFlip = onToggleHint,
                )

                Column(
                    Modifier.padding(horizontal = TujiSpace.S4),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                ) {
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
            }
            Spacer(Modifier.height(bottomPadding + TujiSpace.S3))
        }
    }
}

/**
 * How tall the picture may be, given the height the question surface actually
 * got.
 *
 * The reserved figure is the rest of the surface measured, not guessed: four
 * 60dp options with 8dp between them (264), the 16dp between the picture and
 * the first of them, and the 16dp under the last.
 */
private fun heroHeight(available: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val reserved = 296.dp
    val room = available - reserved
    return when {
        room < HERO_MIN -> HERO_MIN
        room > HERO_MAX -> HERO_MAX
        else -> room
    }
}

private val HERO_MIN = 200.dp
private val HERO_MAX = 360.dp

/**
 * How long an item may sit unanswered before the card offers the hint.
 *
 * Deliberately past the 7s mark where the suggestion has already dropped to
 * 困難 — by the time the line appears, the only thing flipping still costs is
 * the 穩定/熟練 option.
 */
private const val NUDGE_DELAY_MS = 8_000L

/**
 * The picture, and what 求救提示 turns it over to.
 *
 * The flip costs the wrong-answer rating table (ADR-0007), which is why it is a
 * deliberate tap on the picture rather than a button that could be pressed by
 * accident. Nothing is drawn to say the card is tappable — [nudging] is the
 * only announcement, and it waits eight seconds.
 *
 * Full-bleed on 紙2, not an inset white rectangle: an image framed inside the
 * page margins puts a box around the one thing the whole screen is asking
 * about.
 *
 * The hint *replaces* the picture rather than sitting beside it. Once the
 * meaning is given the question is no longer 「這張圖是什麼字」 but 「這個意思是
 * 哪個字」, and a card asks one question at a time.
 */
@Composable
private fun HeroCard(
    item: StudyQueueItem,
    faceUp: Boolean,
    height: androidx.compose.ui.unit.Dp,
    nudging: Boolean,
    playing: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onFlip: () -> Unit,
) {
    // Reduce Motion keeps the opacity swap and drops the rotation, so the turn
    // becomes a crossfade rather than nothing at all.
    val reduceMotion = rememberReduceMotion()
    val angle by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        // D3 — the turn is meant to be *watched*; that is the whole reason it
        // is a turn and not a swap. Under 移除動畫 it becomes a crossfade at D1,
        // because the two faces still have to change places.
        animationSpec = TujiMotion.ease(if (reduceMotion) TujiMotion.D1 else TujiMotion.D3),
        label = "hintFlip",
    )
    val density = LocalDensity.current
    val flipLabel = stringResource(if (faceUp) R.string.study_hint_see_image else R.string.study_hint_see_hint)
    val faceLabel = if (faceUp) HintFace.of(item.word).text else stringResource(R.string.study_what_is_this)

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(TujiColor.Paper2)
            // One element with one action, not a clickable box wrapping an
            // unlabelled image: a screen reader that cannot see the picture
            // also cannot poke at it to find out that it turns over.
            .semantics(mergeDescendants = true) {
                contentDescription = faceLabel
                onClick(label = flipLabel) { onFlip(); true }
            }
            .tujiClickable(onClick = onFlip),
        contentAlignment = Alignment.Center,
    ) {
        // Two faces on one axis. Each is hidden for the half-turn where it
        // would be seen from behind, which is what makes it read as one card
        // turning rather than two cards crossfading.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reduceMotion) {
                        rotationY = angle
                        cameraDistance = 12f * density.density
                    }
                    alpha = if (angle < 90f) 1f else 0f
                },
            contentAlignment = Alignment.Center,
        ) {
            WordPicture(
                url = item.word.imageUrl,
                kind = WordImageKind.of(item.word.category),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reduceMotion) {
                        rotationY = angle - 180f
                        cameraDistance = 12f * density.density
                    }
                    alpha = if (angle >= 90f) 1f else 0f
                },
            contentAlignment = Alignment.Center,
        ) {
            // A 釋義 is prose and sets as body text; a gloss is a word and sets
            // as a headline. Which of the two this card has is [HintFace]'s
            // decision, asked once — the same call that produced the label.
            val face = HintFace.of(item.word)
            Text(
                face.text,
                style = if (face is HintFace.Definition) TujiType.body else TujiType.h2,
                color = TujiColor.Ink,
                textAlign = TextAlign.Center,
                maxLines = if (face is HintFace.Definition) 6 else 4,
                modifier = Modifier.padding(horizontal = TujiSpace.S5),
            )
        }

        if (canPlay) {
            val label = stringResource(R.string.word_play)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(TujiSpace.S3)
                    .size(48.dp)
                    .background(if (playing) TujiColor.Current else TujiColor.Paper)
                    .semantics { contentDescription = label }
                    .tujiClickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Speaker(tint = TujiColor.Ink)
            }
        }

        // The same slot the gloss occupies during 學新字 — the place the user
        // already associates with "the meaning lives here". An overlay, so it
        // costs no layout and the options do not shift under the thumb when it
        // appears.
        if (nudging) {
            Text(
                stringResource(R.string.study_nudge),
                style = TujiType.bodySm,
                color = TujiColor.Ink2,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(TujiSpace.S3)
                    .background(TujiColor.Paper)
                    .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
            )
        }
    }
}

/**
 * The capsule that acknowledges an answer which advanced **without** the rating
 * sheet — a fast correct answer that auto-rated, or a passed re-test.
 *
 * At the bottom, over everything, for the ~700ms of the advance beat. Inline in
 * the column it competed with the options for the same reading and moved them
 * down as it appeared; the one thing it has to do is be noticed without being
 * in the way.
 */
@Composable
private fun FlashCapsule(flash: ReviewFlash, bottomPadding: androidx.compose.ui.unit.Dp) {
    val text = when (flash) {
        is ReviewFlash.RetestPassed -> stringResource(R.string.study_retest_passed)
        is ReviewFlash.AutoRated -> stringResource(R.string.study_auto_rated, flash.rating.label())
    }
    val tint = when (flash) {
        is ReviewFlash.RetestPassed -> TujiColor.Accumulation
        is ReviewFlash.AutoRated -> TujiColor.CurrentDeep
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding + TujiSpace.S5),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text,
            style = TujiType.bodyStrong,
            color = TujiColor.Paper,
            modifier = Modifier
                .background(tint)
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
        )
    }
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
    playing: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
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
            // The word, and it said aloud. The sheet is the first moment the
            // answer is admitted, which is the moment worth hearing it — and
            // the same button, on the same seam, as the one on the picture.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Headword(question.item)
                    Text(
                        question.item.word.chinese,
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                    )
                }
                if (canPlay) {
                    val label = stringResource(R.string.word_play)
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(if (playing) TujiColor.Current else TujiColor.Paper2)
                            .semantics { contentDescription = label }
                            .tujiClickable(onClick = onPlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        TujiGlyph.Speaker(tint = TujiColor.Ink)
                    }
                }
            }
            Spacer(Modifier.height(TujiSpace.S2))

            when (mode) {
                ReviewRevealMode.ContinueOnly -> {
                    Text(
                        stringResource(R.string.reveal_again_later),
                        style = TujiType.label,
                        color = TujiColor.Ink3,
                    )
                    TujiButton(
                        text = stringResource(R.string.study_next),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ReviewRevealMode.Rate -> {
                    Text(
                        stringResource(
                            if (question.wasCorrect) {
                                R.string.reveal_how_well
                            } else {
                                R.string.reveal_mark_it
                            },
                        ),
                        style = TujiType.label,
                        color = TujiColor.Ink3,
                    )
                    question.availableRatings.forEach { rating ->
                        RatingRow(
                            rating = rating,
                            // Pre-inverted rather than badged: the ink block is
                            // already this app's "this is the one", so a 建議
                            // caption over the label was a second, weaker way
                            // of saying the same thing.
                            filled = rating == question.suggested,
                            onClick = { onRate(rating) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One level of the rating ladder.
 *
 * **Stacked full width, not four boxes side by side.** Laid out as a row the
 * levels read as a slider and the hand drifts rightward — labels are all that
 * fit, and 困難 beside 熟練 is just the worse-sounding one. Stacked, each level
 * gets a line saying what it *means*, and picking becomes a judgement about
 * yourself rather than a position on a scale.
 *
 * The 3dp leading edge is a ladder of its own: alert → 瞳黃 → the two teal
 * steps, because teal means accumulation and the further along the confidence
 * scale, the deeper it goes.
 */
@Composable
private fun RatingRow(rating: SRSRating, filled: Boolean, onClick: () -> Unit) {
    val ground = if (filled) TujiColor.Ink else TujiColor.Paper2
    val ink = if (filled) TujiColor.Paper else TujiColor.Ink
    val sub = if (filled) TujiColor.Paper.copy(alpha = 0.7f) else TujiColor.Ink3
    val edge = when (rating) {
        SRSRating.Again -> TujiColor.Alert
        SRSRating.Hard -> TujiColor.Current
        SRSRating.Good -> TujiColor.AccumulationSoft
        SRSRating.Easy -> TujiColor.Accumulation
    }
    Row(
        Modifier
            .fillMaxWidth()
            // `IntrinsicSize.Min` first: the leading edge below asks to fill
            // the row's height, and in a row whose height is unbounded that
            // means the whole screen — which is what it did, pushing the
            // second rating off the bottom where nobody could see it.
            .height(IntrinsicSize.Min)
            .heightIn(min = 56.dp)
            .background(ground)
            .tujiClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TujiBorder.Bw3).fillMaxHeight().background(edge))
        Column(
            Modifier.padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(rating.label(), style = TujiType.h3, color = ink)
            Text(rating.explanation(), style = TujiType.bodySm, color = sub)
        }
    }
}

@Composable
private fun SRSRating.explanation(): String = stringResource(
    when (this) {
        SRSRating.Again -> R.string.rating_again_why
        SRSRating.Hard -> R.string.rating_hard_why
        SRSRating.Good -> R.string.rating_good_why
        SRSRating.Easy -> R.string.rating_easy_why
    }
)

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
    height: androidx.compose.ui.unit.Dp,
    onReplay: (Float) -> Unit,
    onReveal: () -> Unit,
) {
    // Readable once the answer is in, or once the eye bought it. Answering
    // removes the reason to hide it: from that moment the sentence is study
    // material, exactly like the answer on the reveal sheet.
    val legible = question.sentenceRevealed || question.phase == ReviewPhase.Review
    val maskedLabel = stringResource(R.string.study_listen_masked)

    // The same height the picture gets on 選字, for the same reason: the two
    // questions must not be tellable apart before either is asked.
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
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
                WordPicture(
                    url = option.imageUrl,
                    kind = option.imageKind,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 慢讀's multiplier. Slow enough to separate the syllables, not so slow it warbles. */
private const val SLOW_RATE = 0.8f
