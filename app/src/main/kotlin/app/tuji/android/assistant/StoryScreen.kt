package app.tuji.android.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.study.ThemeStatus
import app.tuji.android.core.design.FuriganaSentence
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberReduceMotion
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Story
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.study.SentenceSpeakerButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 情境故事 — iOS's `StoryBuilderView`. One page, top to bottom in the order of
 * the decision: 單字, 場地, 情景, 難度. The button at the bottom is the only thing
 * that spends points, and it says what it costs before it is tapped. Tapping it
 * hands the page to the story at once; closing it comes back to the same choices.
 */
@Composable
fun StoryScreen(
    model: StoryBuilderModel,
    offerRevision: Int,
    speech: WordSpeaking?,
    accent: String,
    shelves: List<CategoryShelf.Shelf>,
    themeStatus: (String) -> ThemeStatus,
    bookmarked: Set<String>,
    uiLang: String,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model, offerRevision) { model.loadOffer() }
    DisposableEffect(model) { onDispose { model.stop() } }

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            title = stringResource(R.string.assistant_feature_story),
            showsRule = true,
        )
        if (state.reading) {
            StoryReader(
                story = state.story,
                language = state.storyLanguage,
                picked = state.picked,
                writing = state.writing,
                speech = speech,
                accent = accent,
                onOpenWord = onOpenWord,
                onClose = model::closeStory,
                modifier = Modifier.weight(1f),
            )
        } else {
            Form(state, model, onPick = { picking = true }, onRetry = { scope.launch { model.loadOffer() } }, modifier = Modifier.weight(1f))
            WriteBar(state) { model.start(scope) }
        }
    }

    if (picking) {
        StoryItemPicker(
            model = model,
            shelves = shelves,
            themeStatus = themeStatus,
            bookmarked = bookmarked,
            uiLang = uiLang,
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Form(
    state: StoryBuilderModel.State,
    model: StoryBuilderModel,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S5),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
            MascotFigure(MascotPose.Face, 36.dp)
            Text(stringResource(R.string.story_prompt), style = TujiType.body, color = TujiColor.Ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Heading(stringResource(R.string.story_words_heading, StoryBuilderModel.MAX_ITEMS))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                state.picked.forEach { word ->
                    val label = stringResource(R.string.story_remove_word, word.word)
                    Chip(selected = true, onClick = { model.toggle(word) }, modifier = Modifier.semantics { contentDescription = label }) {
                        Text(word.word, style = TujiType.bodySmStrong, color = TujiColor.Paper)
                        TujiGlyph.Close(size = 11.dp, tint = TujiColor.Paper)
                    }
                }
                if (state.canPickMore) {
                    Chip(selected = false, onClick = onPick) {
                        TujiGlyph.Plus(size = 13.dp)
                        Text(stringResource(R.string.story_pick_words), style = TujiType.bodySmStrong, color = TujiColor.Ink)
                    }
                }
            }
        }
        when (state.offerPhase) {
            StoryBuilderModel.OfferPhase.Failed -> Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(stringResource(R.string.story_load_failed), style = TujiType.bodySm, color = TujiColor.Ink3)
                TextAction(stringResource(R.string.credit_statement_retry), onRetry)
            }
            StoryBuilderModel.OfferPhase.Loaded -> state.offer?.let { offer ->
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Heading(stringResource(R.string.story_scene))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                        offer.scenes.forEach { scene ->
                            LabelChip(scene.label, scene.id == state.sceneId) { model.chooseScene(scene.id) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Heading(stringResource(R.string.story_situation))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                        state.situations.forEach { situation ->
                            LabelChip(situation.label, situation.id == state.situationId) { model.chooseSituation(situation.id) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Heading(stringResource(R.string.story_level))
                    Column {
                        offer.levels.forEach { level ->
                            val selected = level.id == state.levelId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .tujiClickable { model.chooseLevel(level.id) }
                                    .semantics { this.selected = selected },
                                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(level.label, style = TujiType.bodyStrong, color = TujiColor.Ink)
                                    Text(level.hint, style = TujiType.bodySm, color = TujiColor.Ink3)
                                }
                                if (selected) TujiGlyph.Check(size = 15.dp)
                            }
                            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
                        }
                    }
                }
            }
            StoryBuilderModel.OfferPhase.Loading -> TujiIndeterminateBar(Modifier.width(40.dp))
        }
    }
}

@Composable
private fun WriteBar(state: StoryBuilderModel.State, onWrite: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        Column(
            Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            // The price before the tap, or why the last tap produced nothing.
            val writing = state.writing
            val note = when {
                writing is StoryBuilderModel.Writing.Failed -> stringResource(failureSentence(writing.failure))
                state.offerReady -> state.offer?.let { stringResource(R.string.story_price, it.minPoints, it.maxPoints) }
                else -> null
            }
            note?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
            TujiButton(text = stringResource(R.string.story_write), onClick = onWrite, enabled = state.canWrite)
        }
    }
}

internal fun failureSentence(failure: StoryBuilderModel.Failure): Int = when (failure) {
    StoryBuilderModel.Failure.InsufficientPoints -> R.string.story_failure_points
    StoryBuilderModel.Failure.Busy -> R.string.story_failure_busy
    StoryBuilderModel.Failure.Offline -> R.string.story_failure_offline
    StoryBuilderModel.Failure.Unavailable -> R.string.story_failure_unavailable
    StoryBuilderModel.Failure.ItemsChanged -> R.string.story_failure_items
    StoryBuilderModel.Failure.Failed -> R.string.story_failure_failed
}

@Composable
private fun Heading(text: String) = Text(text, style = TujiType.label, color = TujiColor.Ink3)

/** The 助手 chip: a ground, not an outline; a chosen one is ink. */
@Composable
private fun Chip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .background(if (selected) TujiColor.Ink else TujiColor.Paper2)
            .tujiClickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun LabelChip(label: String, selected: Boolean, onClick: () -> Unit) = Chip(selected, onClick) {
    Text(label, style = TujiType.bodySmStrong, color = if (selected) TujiColor.Paper else TujiColor.Ink)
}

// MARK: - 讀故事

/**
 * Reading one — iOS's `StoryReaderView`. Narration sits on the paper; Moira's
 * lines beside her face, the person's on the right. The story is read a
 * segment at a time: 繼續 brings in the next one that has arrived and reads it
 * aloud when it is spoken; when the reader has caught up with the writing the
 * bar says so instead. 全部顯示 skips ahead. The words and the way out wait
 * until the last segment is in.
 */
@Composable
private fun StoryReader(
    story: Story?,
    language: TargetLanguage,
    picked: List<Word>,
    writing: StoryBuilderModel.Writing,
    speech: WordSpeaking?,
    accent: String,
    onOpenWord: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val segments = story?.segments.orEmpty()
    var shown by rememberSaveable { mutableIntStateOf(1) }
    val caughtUp = shown >= segments.size
    val finished = caughtUp && writing != StoryBuilderModel.Writing.InProgress
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var speaking by remember { mutableStateOf<Job?>(null) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(shown, finished) { scroll.animateScrollTo(scroll.maxValue) }
    DisposableEffect(Unit) { onDispose { speaking?.cancel() } }

    fun advance() {
        if (caughtUp) return
        val next = segments[shown]
        shown += 1
        speaking?.cancel()
        // Narration is in the interface language, so it is not read aloud.
        if (next.speaker != Story.Speaker.Narrator && speech?.canSpeak(language) == true) {
            speaking = scope.launch { speech.speak(next.text, language, accent) }
        }
    }

    Column(modifier) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            story?.let {
                Text(it.title, style = TujiType.h2, color = TujiColor.Ink)
                Text(it.intro, style = TujiType.body, color = TujiColor.Ink2)
            }
            segments.take(shown).forEachIndexed { index, segment ->
                AnimatedVisibility(
                    visible = true,
                    enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { it / 3 },
                    label = "segment$index",
                ) {
                    SegmentRow(segment, language, speech, accent) { speaking?.cancel() }
                }
            }
            if (finished) {
                if (writing is StoryBuilderModel.Writing.Failed) {
                    Text(stringResource(failureSentence(writing.failure)), style = TujiType.bodySm, color = TujiColor.Ink3)
                }
                Words(story, picked, onOpenWord)
                TujiButton(
                    text = stringResource(R.string.story_another),
                    onClick = {
                        speaking?.cancel()
                        onClose()
                    },
                    style = TujiButtonStyle.Secondary,
                    modifier = Modifier.padding(top = TujiSpace.S2),
                )
            }
        }
        if (!finished) {
            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
            Row(
                Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (caughtUp) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TujiIndeterminateBar(Modifier.width(40.dp))
                        Text(stringResource(R.string.story_writing), style = TujiType.bodySm, color = TujiColor.Ink)
                    }
                } else {
                    Text(
                        stringResource(R.string.story_show_all),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .tujiClickable {
                                speaking?.cancel()
                                shown = segments.size
                            }
                            .padding(vertical = TujiSpace.S3),
                    )
                    TujiButton(text = stringResource(R.string.story_continue), onClick = ::advance, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(
    segment: Story.Segment,
    language: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    onReplay: () -> Unit,
) {
    when (segment.speaker) {
        Story.Speaker.Narrator -> Text(
            segment.text,
            style = TujiType.bodySm.copy(fontStyle = FontStyle.Italic),
            color = TujiColor.Ink3,
        )
        Story.Speaker.Cat -> Row(
            Modifier.fillMaxWidth().padding(end = TujiSpace.S5),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            verticalAlignment = Alignment.Top,
        ) {
            MascotFigure(MascotPose.Face, 32.dp, Modifier.semantics { contentDescription = "Moira" })
            Line(segment, language, speech, accent, onReplay, Modifier.weight(1f, fill = false))
        }
        Story.Speaker.You -> Box(
            Modifier.fillMaxWidth().padding(start = TujiSpace.S5 + 32.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Line(segment, language, speech, accent, onReplay)
        }
    }
}

@Composable
private fun Line(
    segment: Story.Segment,
    language: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(if (segment.speaker == Story.Speaker.Cat) TujiColor.Paper2 else TujiColor.BrandPrimary.copy(alpha = 0.35f))
            .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val furigana = segment.furigana
            if (!furigana.isNullOrEmpty()) {
                FuriganaSentence(furigana)
            } else {
                Text(segment.text, style = TujiType.bodyStrong, color = TujiColor.Ink)
                segment.reading?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink3) }
            }
            segment.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
        }
        SentenceSpeakerButton(segment.text, language, speech, accent)
    }
}

/** The picked words, one tap from their own pages — explained by the story when it gave a note, else by their gloss. */
@Composable
private fun Words(story: Story?, picked: List<Word>, onOpenWord: (String) -> Unit) {
    Column(Modifier.padding(top = TujiSpace.S3)) {
        Text(stringResource(R.string.story_words), style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.padding(bottom = TujiSpace.S1))
        picked.forEach { word ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .tujiClickable { onOpenWord(word.id) }
                    .padding(vertical = TujiSpace.S2),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(word.word, style = TujiType.bodyStrong, color = TujiColor.Ink)
                    Text(
                        story?.wordNotes?.firstOrNull { it.id == word.id }?.note ?: word.chinese.orEmpty(),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                    )
                }
                Text("→", style = TujiType.h3, color = TujiColor.Ink3)
            }
            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        }
    }
}
