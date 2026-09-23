package app.tuji.android.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.catalog.WordDetailContent
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.MascotCelebrationCard
import app.tuji.android.core.design.MascotSpeechBubble
import app.tuji.android.core.design.StudyOptionRow
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiDetentSheet
import app.tuji.android.core.design.TujiPullUpHint
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.core.model.headwordDisplay
import app.tuji.android.core.model.language
import app.tuji.android.core.study.NewStageStep
import app.tuji.android.core.study.NewTaskKind
import app.tuji.android.core.study.SpellGaps
import app.tuji.android.core.study.SpellSubject
import app.tuji.android.core.study.StudyOptionState
import app.tuji.android.gloss.GlossCardHost
import app.tuji.android.gloss.InteractiveSentenceText
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 學新字 — iOS's `NewFlowView`.
 *
 * **It opens on today's words, not on the first card.** Scanning the grid
 * before any question is itself a teach pass, and an explicit 開始學習 makes
 * the lesson a unit the user chose rather than an ambush. Nothing is written
 * before that tap, so leaving from there needs no question.
 *
 * The three stages are three composables over one state, not three screens
 * with their own navigation: the ladder decides which one is on top, and a
 * word's stages have other words' stages between them — which is why the dots
 * under the header say where *this* word is on its own ladder.
 */
@Composable
fun NewFlowScreen(
    vm: NewFlowViewModel,
    showChinese: Boolean,
    /** The deck being studied — which language an untagged card is asking for. */
    session: TargetLanguage,
    uiLang: String,
    onClose: () -> Unit,
    /** A word's whole entry, for the half of the wrong-answer card a drag opens. */
    fullDetail: @Composable (String) -> Unit,
    bookmarked: (String) -> Boolean = { false },
    onBookmark: ((String) -> Unit)? = null,
    /** Says a tapped 詞塊 out loud. Always synthesised — a 詞塊 has no clip. */
    speech: WordSpeaking? = null,
    accent: String = "us",
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val milestone by vm.milestone.collectAsStateWithLifecycle()
    var started by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // System back asks too, as ✕ does — once there is something to lose.
    // Popping straight out skipped `leave()`, so a beat still in flight fired
    // after the screen was gone.
    BackHandler(enabled = state is NewFlowViewModel.State.Studying && started && !leaving) { leaving = true }
    val insets = WindowInsets.systemBars.asPaddingValues()

    // No 看完整詳情 from inside a session: the card is a glance at one word, and
    // pushing a catalogue page out of a lesson would leave the lesson behind.
    GlossCardHost(
        partOfSpeech = { WordDetailContent.partOfSpeech(it, uiLang) },
        speech = speech,
        accent = accent,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(top = insets.calculateTopPadding()),
    ) {
        when (val s = state) {
            is NewFlowViewModel.State.Loading ->
                TujiPageLoading(label = stringResource(R.string.study_loading))

            is NewFlowViewModel.State.Failed ->
                Centered(s.message.ifBlank { stringResource(R.string.study_failed) })

            // A streak milestone wins over the summary, as on 複習.
            is NewFlowViewModel.State.Done -> milestone?.let {
                MilestoneView(streak = it.streak, topPadding = 0.dp, bottomPadding = insets.calculateBottomPadding(), onFinish = onClose)
            } ?: NewDoneView(
                queue = s.queue,
                mistakes = s.mistakes,
                unsynced = s.unsynced,
                showChinese = showChinese,
                bottomPadding = insets.calculateBottomPadding(),
                onClose = onClose,
            )

            is NewFlowViewModel.State.Studying -> if (!started) {
                Preview(
                    // Nothing has been answered yet, so the ladder still holds
                    // every word — in the order the session will teach them.
                    items = remember(s.total) { s.ladder.tasks.map { it.item }.distinctBy { it.word.id } },
                    showChinese = showChinese,
                    bottomPadding = insets.calculateBottomPadding(),
                    onClose = onClose,
                    onStart = { started = true },
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    StudyHeader(
                        label = stringResource(R.string.study_new_label),
                        count = stringResource(R.string.study_new_count, s.ladder.clearedWords, s.total),
                        progress = s.ladder.progress,
                        unsynced = s.unsynced,
                        onClose = { leaving = true },
                    )
                    StagePips(s.steps)
                    Spacer(Modifier.height(TujiSpace.S3))
                    Box(Modifier.weight(1f)) {
                        StageBody(
                            studying = s,
                            vm = vm,
                            showChinese = showChinese,
                            session = session,
                            bottomPadding = insets.calculateBottomPadding(),
                        )
                        // A miss raises the word rather than printing a line
                        // under the board: the answer is the one thing worth
                        // reading at that moment, and the card gives it a
                        // pronunciation, a 書籤 and — one drag away — its whole
                        // entry, which is what the reader would otherwise have
                        // to leave the lesson to see.
                        missedWord(s.stage)?.let { item ->
                            WrongAnswerSheet(
                                item = item,
                                session = session,
                                showChinese = showChinese,
                                bottomPadding = insets.calculateBottomPadding(),
                                bookmarked = bookmarked,
                                onBookmark = onBookmark,
                                playing = s.playingWord,
                                canPlay = s.canPlayWord,
                                onPlay = vm::playWord,
                                onContinue = vm::continueFromWrong,
                                fullDetail = fullDetail,
                            )
                        }
                    }
                }
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
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3)
    }
}

// Preview

@Composable
private fun Preview(
    items: List<StudyQueueItem>,
    showChinese: Boolean,
    bottomPadding: Dp,
    onClose: () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CloseButton(onClose, Modifier.padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1))
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = TujiSpace.S3, bottom = TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
            ) {
                // 開始 is one of the few moments the cat is allowed to speak,
                // and the pose it asks for is wave: greeting the session.
                MascotSpeechBubble(
                    pose = MascotPose.Wave,
                    text = stringResource(R.string.new_preview_bubble),
                    modifier = Modifier.padding(horizontal = TujiSpace.S4),
                )
                Text(
                    stringResource(R.string.new_preview_title, items.size),
                    style = TujiType.h1,
                    color = TujiColor.Ink,
                    modifier = Modifier.padding(horizontal = TujiSpace.S4),
                )
                WordGrid(items, showChinese, Modifier.padding(horizontal = TujiSpace.S4))
            }
            // The grid runs out under the button rather than being sliced off
            // mid-row by an opaque edge.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(TujiSpace.S4)
                    .background(Brush.verticalGradient(listOf(TujiColor.Paper.copy(alpha = 0f), TujiColor.Paper))),
            )
        }
        TujiButton(
            text = stringResource(R.string.new_preview_start),
            onClick = onStart,
            modifier = Modifier
                .padding(horizontal = TujiSpace.S4)
                .padding(bottom = bottomPadding + TujiSpace.S4),
        )
    }
}

/** Today's words, two across: the picture and its name, no card around either. */
@Composable
private fun WordGrid(items: List<StudyQueueItem>, showChinese: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                row.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(TujiColor.Paper2)) {
                            WordPicture(
                                url = item.word.imageUrl,
                                kind = WordImageKind.of(item.word.category),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            item.word.word,
                            style = TujiType.h3,
                            color = TujiColor.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TujiSpace.S2),
                        )
                        if (showChinese) {
                            Text(item.word.chinese, style = TujiType.label, color = TujiColor.Ink3, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.study_close_label)
    Box(
        modifier
            .size(44.dp)
            .semantics { contentDescription = label }
            .tujiClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TujiGlyph.Close(tint = TujiColor.Ink)
    }
}

// Header dots

/** The current word's 認識 → 選字 → 拼字, with a tick between each. */
@Composable
private fun StagePips(steps: List<NewStageStep>) {
    if (steps.isEmpty()) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) Box(Modifier.size(width = 14.dp, height = 2.dp).background(TujiColor.Paper3))
            Pip(step)
        }
    }
}

@Composable
private fun Pip(step: NewStageStep) {
    val label = stringResource(
        when (step.kind) {
            NewTaskKind.Recognize -> R.string.new_stage_recognize
            NewTaskKind.Identify -> R.string.new_stage_identify
            NewTaskKind.Spell -> R.string.new_stage_spell
        },
    )
    // The dots are the one thing on the screen that says how far along this
    // word is, and they change under a thumb that has just answered. iOS gives
    // them 200ms of easeInOut rather than the D1 state step — slow enough to
    // be followed, which is what a progress mark is for.
    val pip by animateColorAsState(
        when (step.state) {
            NewStageStep.State.Done -> TujiColor.Accumulation
            NewStageStep.State.Skipped -> TujiColor.Accumulation.copy(alpha = 0.35f)
            NewStageStep.State.Active -> TujiColor.Current.copy(alpha = 0.18f)
            NewStageStep.State.Pending -> TujiColor.Paper3
        },
        TujiMotion.easeInOut(PIP_MS),
        label = "pip",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(16.dp)
                .background(pip, CircleShape)
                .then(
                    if (step.state == NewStageStep.State.Active) Modifier.border(2.dp, TujiColor.Current, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (step.state) {
                NewStageStep.State.Done, NewStageStep.State.Skipped -> TujiGlyph.Check(size = 9.dp, tint = TujiColor.Paper)
                NewStageStep.State.Active -> Box(Modifier.size(6.dp).background(TujiColor.Current, CircleShape))
                NewStageStep.State.Pending -> Unit
            }
        }
        Text(
            label,
            style = TujiType.label,
            color = when (step.state) {
                NewStageStep.State.Active -> TujiColor.Ink
                NewStageStep.State.Pending -> TujiColor.Paper3
                else -> TujiColor.Ink3
            },
        )
    }
}

// Stages

@Composable
private fun StageBody(
    studying: NewFlowViewModel.State.Studying,
    vm: NewFlowViewModel,
    showChinese: Boolean,
    session: TargetLanguage,
    bottomPadding: Dp,
) {
    val speaker: @Composable (Dp, Color) -> Unit = { size, ground ->
        if (studying.canPlayWord) SpeakerButton(size, ground, studying.playingWord, vm::playWord)
    }
    when (val stage = studying.stage) {
        is NewFlowViewModel.Stage.Recognize -> {
            // Hearing it is the cheapest teach signal, so the word is said as
            // the card settles — the delay keeps the transition from eating
            // the start of the clip. Once per word: 認識 never requeues.
            LaunchedEffect(stage.item.word.id) {
                delay(300)
                vm.playWord()
            }
            RecognizeCard(stage, studying.teach, showChinese, session, bottomPadding, speaker, vm::rateRecognize)
        }
        is NewFlowViewModel.Stage.Identify ->
            IdentifyCard(stage, showChinese, session, bottomPadding, speaker, vm::pickIdentify, vm::continueFromWrong)
        is NewFlowViewModel.Stage.Spell ->
            SpellCard(stage, showChinese, bottomPadding, speaker, vm::pickSpell, vm::unpickSpell, vm::undoSpell, vm::continueFromWrong)
    }
}

// 認識

/**
 * The teach card, and three self-ratings pinned under it.
 *
 * Nothing is hidden here, because nothing is being asked yet. The rating is a
 * statement about what the user already knew, and the stages after it are what
 * decide whether that statement was true (see `LearnedRating`).
 */
@Composable
private fun RecognizeCard(
    stage: NewFlowViewModel.Stage.Recognize,
    teach: WordDetail?,
    showChinese: Boolean,
    session: TargetLanguage,
    bottomPadding: Dp,
    speaker: @Composable (Dp, Color) -> Unit,
    onRate: (SRSRating) -> Unit,
) {
    val word = stage.item.word
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StageHero(stage.item, height = 220.dp)
            Column(
                Modifier.padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3, bottom = TujiSpace.S3),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { StudyHeadword(stage.item, session) }
                    speaker(48.dp, TujiColor.Paper2)
                }
                (word.headwordDisplay(session) as? HeadwordDisplay.Line)?.let {
                    Text(it.text, style = TujiType.bodySm, color = TujiColor.Ink3)
                }
                if (showChinese) Text(word.chinese, style = TujiType.bodySm, color = TujiColor.Ink2)
                word.definition?.takeIf { it.isNotBlank() && it != word.chinese }?.let {
                    Text(it, style = TujiType.label, color = TujiColor.Ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // The queue sends the sentence bare — no translation, no 詞塊 —
                // so the fetched entry supplies both. Matched **by the sentence
                // itself**: an annotation describes one string, and hanging it
                // on a different one would underline the wrong words. Until the
                // fetch lands this is exactly the line it always was.
                val taught = teach?.examples?.firstOrNull { !it.target.isNullOrBlank() }
                val queued = stage.item.examples?.firstOrNull { it.sentence.isNotBlank() }?.sentence
                val sentence = queued ?: taught?.target
                val annotated = taught?.takeIf { it.target == sentence }
                if (sentence != null) {
                    Column(
                        Modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                    ) {
                        InteractiveSentenceText(
                            sentence = sentence,
                            spans = annotated?.spans,
                            language = word.language(session),
                            style = TujiType.bodySm,
                        )
                        if (showChinese) {
                            annotated?.zh?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = TujiType.label, color = TujiColor.Ink3)
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S3, bottom = bottomPadding + TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(
                stringResource(R.string.new_recognize_prompt),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                listOf(
                    R.string.new_rate_again to SRSRating.Again,
                    R.string.new_rate_hard to SRSRating.Hard,
                    R.string.new_rate_good to SRSRating.Good,
                ).forEach { (label, rating) ->
                    RateButton(
                        text = stringResource(label),
                        rating = rating,
                        chosen = stage.rated == rating,
                        enabled = stage.rated == null,
                        onClick = { onRate(rating) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One ground, one ink, and a 3dp edge that says which end of the scale the
 * button is — the same mark 複習's rating rows use. Three kinds of button in a
 * row pretending to be a set was what this replaced. The tapped one inverts for
 * the beat before the next card, so the tap registers before the card changes.
 */
@Composable
private fun RateButton(
    text: String,
    rating: SRSRating,
    chosen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(56.dp)
            .background(if (chosen) TujiColor.Ink else TujiColor.Paper2)
            .tujiClickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TujiBorder.Bw3).fillMaxHeight().background(rating.edge()))
        Text(
            text,
            style = TujiType.h3,
            color = if (chosen) TujiColor.Paper else TujiColor.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = TujiSpace.S1),
        )
    }
}

internal fun SRSRating.edge(): Color = when (this) {
    SRSRating.Again -> TujiColor.Alert
    SRSRating.Hard -> TujiColor.Current
    SRSRating.Good -> TujiColor.AccumulationSoft
    SRSRating.Easy -> TujiColor.Accumulation
}

// 選字

@Composable
private fun IdentifyCard(
    stage: NewFlowViewModel.Stage.Identify,
    showChinese: Boolean,
    session: TargetLanguage,
    bottomPadding: Dp,
    speaker: @Composable (Dp, Color) -> Unit,
    onPick: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val item = stage.item
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding + TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        // The question, as a line. Which language the options are in is
        // information — a Japanese 自製 card asks for 日文 inside an English
        // session — so it follows the card's own language.
        StagePrompt(
            stringResource(
                if (item.word.language(session) == TargetLanguage.JA) R.string.new_identify_prompt_ja
                else R.string.new_identify_prompt_en,
            ),
        )
        Box(Modifier.fillMaxWidth().height(200.dp).background(TujiColor.Paper2)) {
            WordPicture(
                url = item.word.imageUrl,
                kind = WordImageKind.of(item.word.category),
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(TujiSpace.S3),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (showChinese) {
                    Text(
                        item.word.chinese,
                        style = TujiType.bodySm,
                        color = TujiColor.Ink,
                        maxLines = 2,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .background(TujiColor.Paper)
                            .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
                    )
                }
                Spacer(Modifier.weight(1f))
                speaker(48.dp, TujiColor.Paper)
            }
        }
        Column(Modifier.padding(horizontal = TujiSpace.S4), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
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
        }
    }
}

// 拼字

@Composable
private fun SpellCard(
    stage: NewFlowViewModel.Stage.Spell,
    showChinese: Boolean,
    bottomPadding: Dp,
    speaker: @Composable (Dp, Color) -> Unit,
    onTap: (Int) -> Unit,
    onUnpick: (Int) -> Unit,
    onUndo: () -> Unit,
    onContinue: () -> Unit,
) {
    val item = stage.item
    val gaps = stage.plan
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4)
            .padding(bottom = bottomPadding + TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        // The cat appears for exactly one reason: the answer was wrong. Getting
        // a word right is the expected outcome of a spelling task, and the
        // filled slots already say it landed.
        if (stage.correct == false) {
            MascotSpeechBubble(pose = MascotPose.Peek, text = stringResource(R.string.new_spell_close))
        } else {
            Text(
                stringResource(
                    when {
                        // 挖空拼字 shows the word already; what it asks for is
                        // the parts taken out of it.
                        gaps != null -> R.string.new_spell_gaps
                        // 排出讀音 and 拼出字形 are different questions, and a word
                        // whose 振假名 is itself is asking the second — see [SpellSubject].
                        stage.subject is SpellSubject.Reading -> R.string.new_spell_reading
                        else -> R.string.new_spell_term
                    },
                ),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
        }

        Column(Modifier.fillMaxWidth().background(TujiColor.Paper).border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth().height(168.dp).background(TujiColor.Paper)) {
                WordPicture(
                    url = item.word.imageUrl,
                    kind = WordImageKind.of(item.word.category),
                    inset = TujiSpace.S2,
                    ground = TujiColor.Paper,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = TujiSpace.S3, end = TujiSpace.S3, bottom = TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 拼字塊 hides the string it is asking for, so the gloss is the
                // only cue besides the picture and has to stay. 挖空拼字 already
                // shows the word with holes in it, so the gloss says nothing the
                // prompt has not — and on a word whose hole is most of a
                // syllable it edges towards handing over the answer.
                if (showChinese && gaps == null) {
                    Text(item.word.chinese, style = TujiType.bodySmStrong, color = TujiColor.Ink)
                }
                Spacer(Modifier.weight(1f).heightIn(min = 36.dp))
                speaker(36.dp, TujiColor.Paper2)
            }
        }

        Spacer(Modifier.height(TujiSpace.S2))

        if (gaps != null) {
            GapWordLine(stage, gaps, onUnpick)
        } else {
            // The slots: one row per whitespace token, so "cutting board" reads
            // as two words rather than one run of letters.
            var consumed = 0
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                stage.board?.tokenUnits?.forEach { token ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1, Alignment.CenterHorizontally)) {
                        token.indices.forEach { _ ->
                            val slot = consumed++
                            Slot(
                                text = stage.picks.getOrNull(slot)?.let { stage.pool[it] },
                                verdict = stage.correct,
                                onClick = { onUnpick(slot) }.takeIf { stage.correct == null && slot < stage.picks.size },
                            )
                        }
                    }
                }
            }
        }
        // iOS lays the gap-fill's chunks out three to a row
        // (`min(3, pool.count)`) and the tiles by the rule below. Three is
        // wider than a tile needs and exactly what a chunk needs: `tion` in a
        // sixth-of-a-screen column is a chunk you have to squint at.
        TilePool(stage, onTap, columns = if (gaps != null) minOf(3, stage.pool.size) else null)

        // 退一格 stays, though iOS has no visible delete: taking back the last
        // tile is the one correction a spelling board needs, and a board with
        // no way back makes every mis-tap a wrong answer.
        if (stage.correct == null && stage.picks.isNotEmpty()) {
            Text(
                stringResource(R.string.new_spell_undo),
                style = TujiType.bodySmStrong,
                color = TujiColor.Ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().tujiClickable(onClick = onUndo).padding(TujiSpace.S2),
            )
        }

    }
}

/**
 * 挖空拼字 — the word with its holes, on one line.
 *
 * iOS writes the fit as `.lineLimit(1).minimumScaleFactor(0.5)`, which Compose
 * has no equivalent of across a *row of separate composables* — so [ScaleToFit]
 * below is that modifier, done honestly: measure the row unbounded, then scale
 * it down to whatever room there is. Wrapping instead would break "air
 * conditioner" over two lines, and the point of the line is that the word keeps
 * one readable shape with pieces missing from it.
 */
@Composable
private fun GapWordLine(
    stage: NewFlowViewModel.Stage.Spell,
    gaps: SpellGaps,
    onUnpick: (Int) -> Unit,
) {
    val style = TujiType.headword(GAP_WORD_SP)
    // Sized to the pool's widest option rather than to its own answer: a box
    // that grows with the answer would tell you how many letters go in it,
    // which is half the question on a 3-vs-4-letter family.
    val widest = stage.pool.maxOfOrNull { it.length } ?: 0
    val slotWidth = maxOf(GAP_SLOT_MIN, (widest * GAP_SLOT_PER_CHAR).dp)
    val filled = stage.picks.map { stage.pool[it] }
    // Where the next tap lands — the view draws a thicker rule there.
    val active = if (stage.correct == null) filled.size.takeIf { it < gaps.gaps.size } else null

    ScaleToFit(Modifier.fillMaxWidth()) {
        // Every piece on one baseline, holes included — the word has to read as
        // a word with bits missing, not as a word with bits sitting on a shelf.
        Row {
            gaps.segments.forEachIndexed { index, segment ->
                Text(
                    segment,
                    style = style,
                    color = TujiColor.Ink,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                if (index < gaps.gaps.size) {
                    GapSlot(
                        text = filled.getOrNull(index),
                        answer = gaps.gaps[index].answer,
                        verdict = stage.correct,
                        active = active == index,
                        width = slotWidth,
                        onClick = { onUnpick(index) }.takeIf {
                            stage.correct == null && index < filled.size
                        },
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
    // iOS reveals 正解 under the board here as well, because over there the
    // sheet rests at a fixed `.fraction(0.34)` and there is room. Android's
    // sheet *measures* its rest height from its own content and lands higher,
    // right across this line — the first cut drew it behind the panel, clipped
    // halfway through its own descenders. The answer is not lost: it is the
    // headword at the top of that sheet, in larger type than this was.
}

/**
 * One hole. Marked per slot once the answer is out: a wrong board should say
 * *which* chunk missed, not just that the word came out wrong.
 *
 * **The rule is drawn, not laid out.** Stacking it under the text in a Column
 * makes the column taller than a plain letter by the gap plus the rule, and a
 * bottom-aligned row then lifts the chunk that much above the word it belongs
 * to — `p[ea]ch` with `ea` riding high. iOS avoids it by construction:
 * `.overlay(alignment: .bottom) { Rectangle()… .offset(y: 4) }` takes no space
 * at all. `drawBehind` is that, and it leaves the text a plain Row child so it
 * can share the word's baseline.
 */
@Composable
private fun GapSlot(
    text: String?,
    answer: String,
    verdict: Boolean?,
    active: Boolean,
    width: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val correct = text == answer
    val ink = when {
        verdict == null -> TujiColor.Ink
        correct -> TujiColor.Accumulation
        else -> TujiColor.Alert
    }
    val rule = when {
        verdict != null -> if (correct) TujiColor.Accumulation else TujiColor.Alert
        active -> TujiColor.Accumulation
        text == null -> TujiColor.Paper3
        else -> TujiColor.Accumulation.copy(alpha = 0.5f)
    }
    val thickness = if (active) GAP_RULE_ACTIVE else GAP_RULE
    Text(
        text ?: " ",
        style = TujiType.headword(GAP_WORD_SP),
        color = ink,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .then(if (onClick != null) Modifier.tujiClickable(onClick = onClick) else Modifier)
            .padding(horizontal = TujiSpace.S1)
            .widthIn(min = width)
            .drawBehind {
                // Below the text box rather than inside it, so the hole is as
                // wide as whatever lands in it — [width] is only the minimum.
                drawRect(
                    color = rule,
                    topLeft = Offset(0f, size.height + GAP_RULE_DROP.toPx()),
                    size = Size(size.width, thickness.toPx()),
                )
            },
    )
}

/**
 * Lay the content out with no width limit, then shrink it to fit.
 *
 * This is `minimumScaleFactor` for a row of composables. Estimating the natural
 * width instead — measuring the text and adding the slots — is what the first
 * cut did, and it under-counted by the few dp a filled chunk measures past its
 * slot's minimum: the row got pinned a hair too narrow and dropped the `y` off
 * the end of `strawberry`. Measuring the real thing cannot be off by a hair.
 */
@Composable
private fun ScaleToFit(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val scale =
            if (placeable.width > constraints.maxWidth && placeable.width > 0) {
                constraints.maxWidth.toFloat() / placeable.width
            } else {
                1f
            }
        layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** iOS: `.tujiHeadword(30)` on the gap line. */
private val GAP_WORD_SP = 30.sp

/** iOS: `max(34, widestOption.count * 19)`. */
private val GAP_SLOT_MIN = 34.dp
private const val GAP_SLOT_PER_CHAR = 19

/** iOS: `.offset(y: 4)` on the rule, and 3 under the cursor against 2 elsewhere. */
private val GAP_RULE_DROP = 4.dp
private val GAP_RULE = 2.dp
private val GAP_RULE_ACTIVE = 3.dp

/**
 * The scrambled tiles, in flexible columns: wider units (a chunked long word,
 * merged 拗音) get fewer of them. A spent tile stays in place and dims rather
 * than vanishing — the pool jumping under the thumb is how the next tap lands
 * on the wrong one.
 */
@Composable
private fun TilePool(
    stage: NewFlowViewModel.Stage.Spell,
    onTap: (Int) -> Unit,
    columns: Int? = null,
) {
    val wide = stage.pool.any { it.length > 1 }
    @Suppress("NAME_SHADOWING")
    val columns = columns
        ?: if (wide) stage.pool.size.coerceIn(3, 5) else stage.pool.size.coerceIn(4, 6)
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        stage.pool.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                row.forEach { index ->
                    val spent = index in stage.picks
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .alpha(if (spent) 0.45f else 1f)
                            .background(TujiColor.Paper)
                            .border(1.5.dp, TujiColor.Rule.copy(alpha = if (spent) 0.15f else 0.35f))
                            .tujiClickable(enabled = !spent && stage.correct == null) { onTap(index) }
                            // Merged so the tile's letter and its spent-ness are
                            // one node: a screen reader otherwise gets eight
                            // identical-sounding tiles with no way to tell
                            // which are used.
                            .semantics(mergeDescendants = true) { if (spent) disabled() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stage.pool[index],
                            style = TujiType.headword(22.sp),
                            color = if (spent) TujiColor.Ink3 else TujiColor.Ink,
                            maxLines = 1,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Slot(text: String?, verdict: Boolean?, onClick: (() -> Unit)? = null) {
    val filled = text != null
    val (ground, edge, ink) = when (verdict) {
        true -> Triple(TujiColor.Accumulation.copy(alpha = 0.12f), TujiColor.Accumulation, TujiColor.Accumulation)
        false -> Triple(TujiColor.Alert.copy(alpha = 0.12f), TujiColor.Alert, TujiColor.Alert)
        null -> if (filled) {
            Triple(TujiColor.AccumulationSoft, TujiColor.Accumulation.copy(alpha = 0.5f), TujiColor.Ink)
        } else {
            Triple(TujiColor.Paper, TujiColor.Paper3, TujiColor.Ink)
        }
    }
    Box(
        Modifier
            .widthIn(min = 40.dp, max = 52.dp)
            .width(IntrinsicSize.Max)
            .height(46.dp)
            .background(ground)
            .border(1.5.dp, edge)
            .then(if (onClick != null) Modifier.tujiClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text ?: " ", style = TujiType.headword(22.sp), color = ink, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

// Shared pieces

@Composable
private fun ColumnScope.StagePrompt(text: String) {
    Text(
        text,
        style = TujiType.label,
        color = TujiColor.Ink3,
        modifier = Modifier.padding(horizontal = TujiSpace.S4),
    )
}

@Composable
private fun StageHero(item: StudyQueueItem, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).background(TujiColor.Paper2)) {
        WordPicture(
            url = item.word.imageUrl,
            kind = WordImageKind.of(item.word.category),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The phonetic line under the headword — IPA for English, kana for a Japanese
 * word whose reading could not be split over its characters.
 *
 * [HeadwordDisplay] answers Ruby *or* Line, never both, and that is the whole
 * reason it exists: a screen that drew the ruby and the line printed バスマット
 * over the word and again underneath it. So this draws nothing whenever
 * [StudyHeadword] already put the reading on top.
 */
@Composable
private fun StudyReadingLine(item: StudyQueueItem, session: TargetLanguage) {
    val display = item.word.headwordDisplay(session)
    if (display !is HeadwordDisplay.Line) return
    Text(
        display.text,
        // iOS: `.tujiMono` for an IPA transcription, `.tujiBodySm` for kana.
        style = if (item.word.language(session) == TargetLanguage.JA) {
            TujiType.bodySm
        } else {
            TujiType.monoLabel
        },
        color = TujiColor.Ink3,
    )
}

/** Left-aligned, at the page margin — the headword is part of the text, not a title floating over it. */
@Composable
private fun StudyHeadword(item: StudyQueueItem, session: TargetLanguage) {
    when (val display = item.word.headwordDisplay(session)) {
        is HeadwordDisplay.Ruby -> FuriganaHeadword(segments = display.segments)
        else -> Text(item.word.word, style = TujiType.headword(26.sp), color = TujiColor.Ink)
    }
}

@Composable
private fun SpeakerButton(size: Dp, ground: Color, playing: Boolean, onClick: () -> Unit) {
    TujiIconButton(
        label = stringResource(R.string.word_play),
        onClick = onClick,
        size = size,
        ground = if (playing) TujiColor.Current else ground,
    ) {
        TujiGlyph.Speaker(tint = TujiColor.Ink)
    }
}

/** The word a miss is still sitting on, or null while the question is open. */
private fun missedWord(stage: NewFlowViewModel.Stage): StudyQueueItem? = when (stage) {
    is NewFlowViewModel.Stage.Identify -> stage.item.takeIf { stage.revealed }
    is NewFlowViewModel.Stage.Spell -> stage.item.takeIf { stage.correct == false }
    is NewFlowViewModel.Stage.Recognize -> null
}

/**
 * What a wrong answer raises: the word, said aloud, markable — and one button.
 *
 * It does not advance on its own. The task is about to be requeued a few
 * positions back, and the only moment the reader can study what they missed is
 * now; 下一題 is the single exit, so the queue moves exactly once however the
 * card is left.
 *
 * Two heights, like 複習's reveal: the whole entry is one drag up rather than a
 * trip out of the lesson.
 */
@Composable
private fun WrongAnswerSheet(
    item: StudyQueueItem,
    session: TargetLanguage,
    showChinese: Boolean,
    bottomPadding: Dp,
    bookmarked: (String) -> Boolean,
    onBookmark: ((String) -> Unit)?,
    playing: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onContinue: () -> Unit,
    fullDetail: @Composable (String) -> Unit,
) {
    val wordId = item.word.id
    TujiDetentSheet(
        expandedContent = { fullDetail(wordId) },
        collapsedHint = {
            TujiPullUpHint(
                stringResource(R.string.sheet_pull_up_detail),
                modifier = Modifier.padding(top = TujiSpace.S4, bottom = TujiSpace.S3),
            )
        },
        actions = {
            // Pinned, so 下一題 is reachable at both heights — iOS pins the same
            // button with `.safeAreaInset(edge: .bottom)`.
            TujiButton(
                text = stringResource(R.string.study_next),
                onClick = onContinue,
                // iOS: `BBtn(… icon: "arrow.right")`, and the icon leads the
                // label there too. It is the only button in the lesson that
                // moves the queue, which is what the arrow is saying.
                leading = { TujiGlyph.ArrowRight(tint = TujiColor.Ink) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S4)
                    .padding(top = TujiSpace.S2, bottom = bottomPadding + TujiSpace.S4),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4)
                .padding(top = TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                    StudyHeadword(item, session)
                    StudyReadingLine(item, session)
                    if (showChinese) {
                        Text(
                            item.word.chinese,
                            style = TujiType.bodySm,
                            color = TujiColor.Ink2,
                            // iOS puts 2 here and nowhere else in the stack: the
                            // gloss is a different kind of line from the reading
                            // above it, and s1 between all three flattens that.
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    // No star on a 自製 card: 書籤 filters the catalogue, which
                    // has never heard of it, so the mark would go nowhere.
                    if (onBookmark != null && !CardsSourceRules.isCustom(wordId)) {
                        TujiIconButton(
                            label = stringResource(R.string.word_bookmark),
                            onClick = { onBookmark(wordId) },
                        ) {
                            TujiGlyph.Star(filled = bookmarked(wordId), tint = TujiColor.Ink)
                        }
                    }
                    if (canPlay) {
                        TujiIconButton(
                            label = stringResource(R.string.word_play),
                            onClick = onPlay,
                            ground = if (playing) TujiColor.Current else TujiColor.Paper2,
                        ) {
                            TujiGlyph.Speaker(tint = TujiColor.Ink)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 學完了 — what the session taught, and one way out.
 *
 * iOS's `NewDoneView`: the cheering cat on an ink block, then every word as a
 * picture tile. A count in a sentence is a receipt; the grid is the thing
 * worth looking at, and a word that took retries says so on its own tile
 * rather than being counted up somewhere else.
 */
@Composable
private fun NewDoneView(
    queue: List<StudyQueueItem>,
    mistakes: Map<String, Int>,
    unsynced: Int,
    showChinese: Boolean,
    bottomPadding: Dp,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            Spacer(Modifier.height(TujiSpace.S5))
            MascotCelebrationCard(title = stringResource(R.string.study_done_learned, queue.size)) {
                Text(
                    stringResource(R.string.study_done_added),
                    style = TujiType.bodySm,
                    color = TujiColor.Paper.copy(alpha = 0.7f),
                )
            }
            UnsyncedAnswersNotice(unsynced, Modifier.padding(horizontal = TujiSpace.S4))
            StudyWordGrid(items = queue, showChinese = showChinese, mistakeCounts = mistakes)
            Spacer(Modifier.height(TujiSpace.S5))
        }
        // Pinned, as iOS pins it with `.safeAreaInset(edge: .bottom)`: the way
        // out of a finished session should not be below a scroll.
        TujiButton(
            text = stringResource(R.string.study_done_finish),
            onClick = onClose,
            leading = { TujiGlyph.Check(size = 16.dp, tint = TujiColor.Ink) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4)
                .padding(bottom = bottomPadding + TujiSpace.S3),
        )
    }
}

/** iOS: `.animation(.easeInOut(duration: 0.2), value: self.steps)` in `NewFlowView`. */
private const val PIP_MS = 200
