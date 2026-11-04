package app.tuji.android.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaSentence
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberReduceMotion
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Story
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.form.TujiSheet
import app.tuji.android.study.SentenceSpeakerButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nothing left to type: the whole text is in the box. */
private const val TYPED_ALL = Int.MAX_VALUE

/**
 * 情境故事 read as a stage — iOS's `StoryStageView`. The place fills the screen,
 * Moira stands in it, and one segment at a time sits in a box along the
 * bottom: a name plate for whoever speaks, nothing for narration. Tapping
 * anywhere moves on; tapping while a line is still typing finishes it first.
 *
 * The opening — title and intro — is the first thing in the box. A spoken line
 * is read aloud as it comes in, keeps its speaker and 跟讀 buttons in the box,
 * and shows its translation only while 譯 is on, so the box stays small enough
 * to leave Moira in view. Japanese with kana over it appears whole (a furigana
 * sentence cannot be typed out unit by unit without the ruby jumping);
 * everything else types out unless motion is reduced.
 *
 * 履歷 lists every segment already shown. When the reader catches up with a
 * story still being written the box says so; after the last one, the picked
 * words and the way out take the box's place.
 *
 * The person is never drawn: 你 is the camera. Moira's pose follows who is
 * talking: waving on her lines, thinking on yours, cheering at the end.
 */
@Composable
internal fun StoryStage(
    story: Story?,
    sceneId: String?,
    language: TargetLanguage,
    picked: List<Word>,
    writing: StoryBuilderModel.Writing,
    speech: WordSpeaking?,
    accent: String,
    onOpenWord: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = story?.segments.orEmpty()
    /** Segments shown so far; 0 is the opening. */
    var shown by rememberSaveable { mutableIntStateOf(0) }
    /** Past the last segment: the ending is up. */
    var ended by rememberSaveable { mutableStateOf(false) }
    var translating by rememberSaveable { mutableStateOf(false) }
    var backlog by remember { mutableStateOf(false) }
    /** Characters of the current box text typed so far. */
    var typed by remember { mutableIntStateOf(TYPED_ALL) }
    /** Bumped by 再看一次, so the opening types out again even if it was already showing. */
    var replay by remember { mutableIntStateOf(0) }

    val current = segments.getOrNull(shown - 1)
    val caughtUp = shown >= segments.size
    val finished = caughtUp && writing != StoryBuilderModel.Writing.InProgress
    val reduceMotion = rememberReduceMotion()

    val scope = rememberCoroutineScope()
    var speaking by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val recorder = remember { ShadowingRecorder(context, scope) }
    val shadowing by recorder.state.collectAsStateWithLifecycle()
    val microphone = rememberMicrophone(refused = { recorder.denied(shown - 1) })
    DisposableEffect(Unit) {
        onDispose {
            speaking?.cancel()
            recorder.cancel()
        }
    }

    fun quiet() {
        speaking?.cancel()
        speech?.stop()
        recorder.cancel()
    }

    // The text now in the box, typed out unless it has furigana or motion is reduced.
    val typeable: String? = when {
        current != null -> current.text.takeIf { current.furigana.isNullOrEmpty() }
        else -> story?.intro
    }
    LaunchedEffect(shown, story?.intro, replay) {
        val text = typeable
        if (reduceMotion || text.isNullOrEmpty()) {
            typed = TYPED_ALL
            return@LaunchedEffect
        }
        val count = text.codePointCount(0, text.length)
        typed = 0
        while (typed < count) {
            delay(32)
            if (typed == TYPED_ALL) return@LaunchedEffect
            typed += 1
        }
        typed = TYPED_ALL
    }

    fun typedPart(text: String): String =
        if (typed == TYPED_ALL) text else text.substring(0, text.offsetByCodePoints(0, minOf(typed, text.codePointCount(0, text.length))))

    fun tap() {
        if (ended || story == null) return
        if (typed != TYPED_ALL) {
            typed = TYPED_ALL
            return
        }
        if (!caughtUp) {
            val next = segments[shown]
            quiet()
            shown += 1
            // Narration is in the interface language, so it is not read aloud.
            if (next.speaker != Story.Speaker.Narrator && speech?.canSpeak(language) == true) {
                speaking = scope.launch { speech.speak(next.text, language, accent) }
            }
        } else if (finished) {
            quiet()
            ended = true
        }
    }

    val pose = when {
        finished && shown == segments.size && segments.isNotEmpty() -> MascotPose.Cheer
        segments.take(shown).lastOrNull { it.speaker != Story.Speaker.Narrator }?.speaker == Story.Speaker.You -> MascotPose.Think
        else -> MascotPose.Wave
    }
    val continueLabel = stringResource(R.string.story_continue)
    // The stage's pointer handler outlives this composition; it calls the latest tap.
    val onStageTap by rememberUpdatedState<() -> Unit> { tap() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        StoryBackdrop(sceneId, Modifier.fillMaxSize())
        // Moira stands on the floor line, centred, her pose's ground line on it.
        val figure = minOf(maxHeight * 0.46f, maxWidth * 0.80f)
        val groundY = maxHeight * FLOOR + figure * 0.10f
        Crossfade(
            targetState = pose,
            animationSpec = if (reduceMotion) snap() else tween(200),
            label = "moira",
            modifier = Modifier
                .offset(x = (maxWidth - figure) / 2, y = groundY - figure * pose.groundLine)
                .size(figure)
                .semantics { contentDescription = "Moira" },
        ) { shownPose ->
            Image(painterResource(shownPose.res), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        // The tap that moves on lives on its own layer and on the box, never on
        // the whole stack, so the chips and the box's own buttons do not also
        // advance the story.
        // A bare pointer handler, not `clickable`: a screen-sized button with no
        // name would be the first thing TalkBack lands on, and would hide
        // Moira from it. TalkBack moves on through the box's own action.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onStageTap() } })
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S3)
                .padding(bottom = TujiSpace.S3),
        ) {
            if (ended) {
                AnimatedVisibility(
                    visible = true,
                    enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { it / 3 },
                    label = "ending",
                ) {
                    Ending(
                        story = story,
                        picked = picked,
                        writing = writing,
                        onOpenWord = onOpenWord,
                        onReadAgain = {
                            ended = false
                            shown = 0
                            replay += 1
                        },
                        onClose = {
                            quiet()
                            onClose()
                        },
                    )
                }
            } else {
                Column(
                    Modifier
                        .clickableWithoutRipple(::tap)
                        .semantics { onClick(label = continueLabel) { tap(); true } },
                ) {
                    val name = when (current?.speaker) {
                        null -> story?.title
                        Story.Speaker.Cat -> "Moira"
                        Story.Speaker.You -> stringResource(R.string.story_you)
                        Story.Speaker.Narrator -> null
                    }
                    name?.let {
                        Text(
                            it,
                            style = TujiType.bodySmStrong,
                            color = TujiColor.Ink,
                            modifier = Modifier
                                .zIndex(1f)
                                .offset(x = TujiSpace.S3, y = 1.dp)
                                .background(if (current?.speaker == Story.Speaker.You) TujiColor.AccumulationSoft else TujiColor.BrandPrimary)
                                .border(TujiBorder.Bw1, TujiColor.Ink)
                                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S1),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .background(TujiColor.Paper.copy(alpha = 0.94f))
                            .border(TujiBorder.Bw1, TujiColor.Ink)
                            .padding(TujiSpace.S3),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    ) {
                        when {
                            current?.speaker == Story.Speaker.Narrator -> Text(
                                typedPart(current.text),
                                style = TujiType.body.copy(fontStyle = FontStyle.Italic),
                                color = TujiColor.Ink2,
                            )
                            current != null -> SpokenLine(
                                segment = current,
                                index = shown - 1,
                                text = typedPart(current.text),
                                typedOut = typed == TYPED_ALL,
                                translating = translating,
                                language = language,
                                speech = speech,
                                accent = accent,
                                shadowing = shadowing,
                                onSpeak = { recorder.cancel() },
                                onShadow = {
                                    val index = shown - 1
                                    if (shadowing.isActive(index)) {
                                        recorder.stop()
                                    } else {
                                        speaking?.cancel()
                                        microphone { recorder.start(index, current.text, language, current.furigana) }
                                    }
                                },
                            )
                            story != null -> Text(typedPart(story.intro), style = TujiType.body, color = TujiColor.Ink)
                            else -> Waiting()
                        }
                        // What tells the reader a tap does something: ▼ when there
                        // is more, the writing indicator when caught up with it.
                        if (typed == TYPED_ALL) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (caughtUp && writing == StoryBuilderModel.Writing.InProgress && story != null) {
                                    Waiting()
                                } else {
                                    Text("▼", style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.clearAndSetSemantics {})
                                }
                            }
                        }
                    }
                }
            }
        }
        TopBar(
            translating = translating,
            onTranslate = { translating = !translating },
            onBacklog = {
                quiet()
                backlog = true
            },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }

    if (backlog) Backlog(story, segments.take(shown)) { backlog = false }
}

@Composable
private fun SpokenLine(
    segment: Story.Segment,
    index: Int,
    text: String,
    typedOut: Boolean,
    translating: Boolean,
    language: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    shadowing: ShadowingRecorder.State,
    onSpeak: () -> Unit,
    onShadow: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            val furigana = segment.furigana
            if (!furigana.isNullOrEmpty()) {
                FuriganaSentence(furigana, style = TujiType.h3)
            } else {
                Text(text, style = TujiType.h3, color = TujiColor.Ink)
                if (typedOut) segment.reading?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink3) }
            }
            if (translating && typedOut) segment.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
            if (shadowing.line == index) ShadowingFeedback(shadowing.phase)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Box(Modifier.clickableWithoutRipple(onSpeak, consume = false)) {
                SentenceSpeakerButton(segment.text, language, speech, accent)
            }
            ShadowingButton(active = shadowing.isActive(index), onClick = onShadow)
        }
    }
}

@Composable
private fun Waiting() {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
        TujiIndeterminateBar(Modifier.width(32.dp))
        Text(stringResource(R.string.story_writing), style = TujiType.bodySm, color = TujiColor.Ink3)
    }
}

@Composable
private fun TopBar(translating: Boolean, onTranslate: () -> Unit, onBacklog: () -> Unit, modifier: Modifier) {
    val translateLabel = stringResource(if (translating) R.string.story_hide_translation else R.string.story_show_translation)
    Row(modifier.padding(TujiSpace.S3), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Chip(
            stringResource(R.string.story_translate),
            on = translating,
            modifier = Modifier
                .tujiClickable(onClick = onTranslate)
                .clearAndSetSemantics {
                    contentDescription = translateLabel
                    role = Role.Button
                    onClick { onTranslate(); true }
                },
        )
        Chip(
            stringResource(R.string.story_backlog),
            on = false,
            modifier = Modifier.tujiClickable(onClick = onBacklog).semantics { role = Role.Button },
        )
    }
}

/** A chip on the stage: 36 tall to look at, 44 to hit. */
@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier) {
    Box(modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = TujiType.bodySmStrong,
            color = if (on) TujiColor.Paper else TujiColor.Ink,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .background(if (on) TujiColor.Ink else TujiColor.Paper.copy(alpha = 0.92f))
                .border(TujiBorder.Bw1, TujiColor.Ink)
                .padding(horizontal = TujiSpace.S3, vertical = 8.dp),
        )
    }
}

@Composable
private fun Ending(
    story: Story?,
    picked: List<Word>,
    writing: StoryBuilderModel.Writing,
    onOpenWord: (String) -> Unit,
    onReadAgain: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper.copy(alpha = 0.96f))
            .border(TujiBorder.Bw1, TujiColor.Ink)
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        if (writing is StoryBuilderModel.Writing.Failed) {
            Text(stringResource(failureSentence(writing.failure)), style = TujiType.bodySm, color = TujiColor.Ink3)
        }
        if (picked.isNotEmpty()) StoryWords(story, picked, onOpenWord)
        Row(Modifier.padding(top = TujiSpace.S1), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            TujiButton(
                text = stringResource(R.string.story_read_again),
                onClick = onReadAgain,
                style = TujiButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            TujiButton(text = stringResource(R.string.story_another), onClick = onClose, modifier = Modifier.weight(1f))
        }
    }
}

/** 履歷: the opening and every segment already shown, nothing ahead of the reader. */
@Composable
private fun Backlog(story: Story?, shown: List<Story.Segment>, onDismiss: () -> Unit) {
    TujiSheet(title = stringResource(R.string.story_backlog), onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            story?.let {
                Text(it.title, style = TujiType.h3, color = TujiColor.Ink)
                Text(it.intro, style = TujiType.bodySm, color = TujiColor.Ink2)
            }
            shown.forEach { segment ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (segment.speaker == Story.Speaker.Narrator) {
                        Text(segment.text, style = TujiType.bodySm.copy(fontStyle = FontStyle.Italic), color = TujiColor.Ink3)
                    } else {
                        Text(
                            if (segment.speaker == Story.Speaker.Cat) "Moira" else stringResource(R.string.story_you),
                            style = TujiType.label,
                            color = TujiColor.Ink3,
                        )
                        Text(segment.text, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        segment.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
                    }
                }
            }
            Spacer(Modifier.heightIn(min = TujiSpace.S2))
        }
    }
}

/**
 * A tap without a ripple: the stage and the box are not buttons to look at.
 * With [consume] false the touch is only watched (on the way down, before the
 * child sees it), so the child still gets its own tap.
 */
@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit, consume: Boolean = true): Modifier =
    if (consume) {
        this.then(
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        )
    } else {
        this.then(
            Modifier.pointerInput(onClick) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onClick()
                }
            },
        )
    }
