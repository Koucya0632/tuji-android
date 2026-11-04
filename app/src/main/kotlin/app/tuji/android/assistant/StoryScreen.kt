package app.tuji.android.assistant

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.size
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.study.ThemeStatus
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Story
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordSpeaking
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
            StoryStage(
                story = state.story,
                sceneId = state.sceneId,
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

/** The mic under the speaker: 跟讀, or 停止跟讀 while it listens. */
@Composable
internal fun ShadowingButton(active: Boolean, onClick: () -> Unit) {
    val haptics = app.tuji.android.core.design.rememberTujiHaptics()
    val label = stringResource(if (active) R.string.shadowing_stop else R.string.shadowing_start)
    Box(
        Modifier
            .size(32.dp)
            .background(if (active) TujiColor.Current else TujiColor.Paper)
            .tujiClickable {
                haptics.soft()
                onClick()
            }
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        if (active) TujiGlyph.StopSquare(size = 12.dp) else TujiGlyph.Mic(size = 14.dp)
    }
}

/**
 * The try's result, inside the bubble: one number — how much of the line the
 * recogniser caught, not how good the accent was. The transcript is not shown.
 */
@Composable
internal fun ShadowingFeedback(phase: ShadowingRecorder.Phase) {
    val context = LocalContext.current
    val modifier = Modifier.padding(top = TujiSpace.S1)
    when (phase) {
        ShadowingRecorder.Phase.Idle -> Unit
        is ShadowingRecorder.Phase.Listening ->
            Text(stringResource(R.string.shadowing_listening), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = modifier)
        is ShadowingRecorder.Phase.Scored -> if (phase.heard.isEmpty()) {
            Text(stringResource(R.string.shadowing_nothing_heard), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = modifier)
        } else {
            Text(
                stringResource(R.string.shadowing_score, (phase.score.ratio * 100).roundToInt()),
                style = TujiType.bodyStrong.copy(fontFeatureSettings = "tnum"),
                color = TujiColor.Ink,
                modifier = modifier,
            )
        }
        ShadowingRecorder.Phase.Denied -> Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(stringResource(R.string.shadowing_denied), style = TujiType.bodySm, color = TujiColor.Ink2)
            Text(
                stringResource(R.string.speech_open_settings),
                style = TujiType.bodySm.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                color = TujiColor.Ink,
                modifier = Modifier.tujiClickable { openAppSettings(context) },
            )
        }
        ShadowingRecorder.Phase.Unavailable ->
            Text(stringResource(R.string.speech_unavailable), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = modifier)
        ShadowingRecorder.Phase.Offline ->
            Text(stringResource(R.string.speech_offline), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = modifier)
        ShadowingRecorder.Phase.Failed ->
            Text(stringResource(R.string.speech_failed), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = modifier)
    }
}

/** The picked words, one tap from their own pages — explained by the story when it gave a note, else by their gloss. */
@Composable
internal fun StoryWords(story: Story?, picked: List<Word>, onOpenWord: (String) -> Unit) {
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
