package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.tuji.android.core.design.TujiPrompt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.StudyOptionRow
import app.tuji.android.core.study.StudyOptionState
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.study.SpellSubject
import coil3.compose.AsyncImage

/**
 * 學新字 — one interleaved session of 認識 → 選字 → 拼字.
 *
 * The three stages are three composables over one state, not three screens
 * with their own navigation: the ladder decides which one is on top, and a
 * word's stages have other words' stages between them.
 */
@Composable
fun NewFlowScreen(vm: NewFlowViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf(false) }
    val insets = WindowInsets.systemBars.asPaddingValues()

    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(top = insets.calculateTopPadding()),
    ) {
        when (val s = state) {
            is NewFlowViewModel.State.Loading ->
                Centered(stringResource(R.string.study_loading))

            is NewFlowViewModel.State.Failed ->
                Centered(s.message.ifBlank { stringResource(R.string.study_failed) })

            is NewFlowViewModel.State.Done -> NewDoneView(
                learned = s.learned,
                unsynced = s.unsynced,
                bottomPadding = insets.calculateBottomPadding(),
                onClose = onClose,
            )

            is NewFlowViewModel.State.Studying -> Column {
                StudyHeader(
                    progress = s.ladder.progress,
                    unsynced = s.unsynced,
                    // Ask first — see ReviewScreen.
                    onClose = { leaving = true },
                )
                StageBody(
                    stage = s.stage,
                    vm = vm,
                    bottomPadding = insets.calculateBottomPadding(),
                )
            }
        }

        if (leaving) {
            TujiPrompt(
                title = stringResource(R.string.new_leave_title),
                message = stringResource(R.string.new_leave_message),
                confirm = stringResource(R.string.study_leave_confirm),
                cancel = stringResource(R.string.new_leave_cancel),
                onConfirm = {
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
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3)
    }
}

@Composable
private fun StageBody(
    stage: NewFlowViewModel.Stage,
    vm: NewFlowViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Spacer(Modifier.height(TujiSpace.S3))
        when (stage) {
            is NewFlowViewModel.Stage.Recognize -> RecognizeCard(stage, vm::rateRecognize)
            is NewFlowViewModel.Stage.Identify -> IdentifyCard(stage, vm::pickIdentify, vm::continueFromWrong)
            is NewFlowViewModel.Stage.Spell -> SpellCard(stage, vm::tapTile, vm::undoTile, vm::continueFromWrong)
        }
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}

// 認識

/**
 * The teach card: the picture, the word, what it means — and three
 * self-ratings.
 *
 * Nothing is hidden here, because nothing is being asked yet. The rating is a
 * statement about what the user already knew, and the stages after it are what
 * decide whether that statement was true (see `LearnedRating`).
 */
@Composable
private fun RecognizeCard(
    stage: NewFlowViewModel.Stage.Recognize,
    onRate: (SRSRating) -> Unit,
) {
    val item = stage.item
    Hero(item)
    Headword(item)

    (item.word.definition ?: item.word.chinese)?.let {
        Text(
            it,
            style = TujiType.body,
            color = TujiColor.Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Text(
        stringResource(R.string.new_recognize_prompt),
        style = TujiType.bodySmStrong,
        color = TujiColor.Ink3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = TujiSpace.S2),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        listOf(
            R.string.new_rate_again to SRSRating.Again,
            R.string.new_rate_hard to SRSRating.Hard,
            R.string.new_rate_good to SRSRating.Good,
        ).forEach { (label, rating) ->
            TujiButton(
                text = stringResource(label),
                onClick = { onRate(rating) },
                // The tapped one fills while the beat runs, so the tap
                // registers before the card underneath changes.
                style = if (stage.rated == rating) TujiButtonStyle.Primary else TujiButtonStyle.Secondary,
                enabled = stage.rated == null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// 選字

@Composable
private fun IdentifyCard(
    stage: NewFlowViewModel.Stage.Identify,
    onPick: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val item = stage.item
    Hero(item)

    stage.choices.forEachIndexed { i, label ->
        StudyOptionRow(
            label = label,
            letter = ('A' + i).toString(),
            state = StudyOptionState.forOption(
                label = label,
                answer = item.word.word,
                picked = stage.picked,
                revealed = stage.revealed,
                wrongPicks = emptySet(),
            ),
            enabled = stage.picked == null,
            onClick = { onPick(label) },
        )
    }

    if (stage.revealed) {
        WrongFooter(answer = item.word.word, onContinue = onContinue)
    }
}

// 拼字

@Composable
private fun SpellCard(
    stage: NewFlowViewModel.Stage.Spell,
    onTap: (Int) -> Unit,
    onUndo: () -> Unit,
    onContinue: () -> Unit,
) {
    Hero(stage.item)

    Text(
        stringResource(
            // 排出讀音 and 拼出字形 are different questions, and a word whose
            // 振假名 is itself is asking the second one — see [SpellSubject].
            if (stage.subject is SpellSubject.Reading) {
                R.string.new_spell_reading
            } else {
                R.string.new_spell_term
            },
        ),
        style = TujiType.bodySmStrong,
        color = TujiColor.Ink3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    // The slots: one row per whitespace token, so "cutting board" reads as two
    // words rather than one run of letters.
    var consumed = 0
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        stage.board.tokenUnits.forEach { token ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
            ) {
                token.indices.forEach { _ ->
                    val slot = consumed++
                    Slot(text = stage.picks.getOrNull(slot)?.let { stage.tiles[it] })
                }
            }
        }
    }

    FlowRow(
        Modifier.fillMaxWidth().padding(top = TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        stage.tiles.forEachIndexed { index, unit ->
            val spent = index in stage.picks
            Box(
                Modifier
                    .background(if (spent) TujiColor.Paper3 else TujiColor.Paper2)
                    .tujiClickable(enabled = !spent && stage.correct == null) { onTap(index) }
                    // Merged so the tile's letter and its spent-ness are one
                    // node. Without it the label sits on the inner Text and the
                    // enabled state on this Box, and a screen reader is handed
                    // eight identical-sounding tiles with no way to tell which
                    // are already used — the dimming says it only to the eye.
                    .semantics(mergeDescendants = true) { if (spent) disabled() }
                    .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
            ) {
                // A spent tile stays in place and dims rather than vanishing:
                // the pool jumping under the thumb is how the next tap lands on
                // the wrong one.
                Text(
                    unit,
                    style = TujiType.h3,
                    color = if (spent) TujiColor.Ink3 else TujiColor.Ink,
                )
            }
        }
    }

    if (stage.correct == null && stage.picks.isNotEmpty()) {
        Text(
            stringResource(R.string.new_spell_undo),
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .tujiClickable(onClick = onUndo)
                .padding(TujiSpace.S2),
        )
    }

    if (stage.correct == false) {
        WrongFooter(answer = stage.board.target, onContinue = onContinue)
    }
}

@Composable
private fun Slot(text: String?) {
    Box(
        Modifier
            .width(44.dp)
            .height(52.dp)
            .background(if (text == null) TujiColor.Paper2 else TujiColor.Current),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.orEmpty(), style = TujiType.h3, color = TujiColor.Ink)
    }
}

// Shared pieces

@Composable
private fun Hero(item: StudyQueueItem) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(TujiColor.Paper2),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.word.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(TujiSpace.S3),
        )
    }
}

@Composable
private fun Headword(item: StudyQueueItem) {
    val segments = item.word.readingSegments
    if (!segments.isNullOrEmpty()) {
        FuriganaHeadword(
            segments = segments,
            modifier = Modifier.fillMaxWidth(),
            arrangement = Arrangement.Center,
        )
    } else {
        Text(
            item.word.word,
            style = TujiType.h2,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What a wrong answer leaves on screen.
 *
 * The answer, and one button. It does not advance on its own: the task is
 * about to be requeued a few positions back, and the only moment the user can
 * read what they missed is now.
 */
@Composable
private fun WrongFooter(answer: String, onContinue: () -> Unit) {
    Text(
        stringResource(R.string.new_wrong_answer_is, answer),
        style = TujiType.bodyStrong,
        color = TujiColor.Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = TujiSpace.S2),
    )
    TujiButton(
        text = stringResource(R.string.study_next),
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewDoneView(
    learned: Int,
    unsynced: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(TujiSpace.S4)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.new_done_title), style = TujiType.h1, color = TujiColor.Ink)
        Spacer(Modifier.height(TujiSpace.S2))
        Text(
            stringResource(R.string.new_done_count, learned),
            style = TujiType.body,
            color = TujiColor.Ink2,
        )
        if (unsynced > 0) {
            Spacer(Modifier.height(TujiSpace.S2))
            Text(
                stringResource(R.string.study_unsynced_done, unsynced),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(TujiSpace.S5))
        TujiButton(text = stringResource(R.string.study_close), onClick = onClose)
    }
}
