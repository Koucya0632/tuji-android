package app.tuji.android.scenes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.scenes.GlossToken
import app.tuji.android.core.scenes.LocalizedText
import app.tuji.android.core.scenes.PracticeMode
import app.tuji.android.core.scenes.PracticeScene
import app.tuji.android.core.scenes.PracticeStep
import app.tuji.android.core.scenes.SceneId
import app.tuji.android.core.scenes.SceneMachine
import app.tuji.android.core.scenes.ScenePracticeSession
import app.tuji.android.core.scenes.ScenePracticeSession.Phase
import app.tuji.android.core.scenes.ScenePracticeSession.QuestionPhase
import app.tuji.android.core.scenes.SceneQuestion
import app.tuji.android.core.scenes.SceneStatus
import app.tuji.android.core.scenes.SpokenLine
import app.tuji.android.core.scenes.StageReply
import app.tuji.android.core.scenes.StageRoute
import app.tuji.android.core.scenes.StageStep
import app.tuji.android.core.scenes.practised

/**
 * 場面練習 itself: one 問句 at a time on its stage, then the summary. The
 * screen renders `ScenePracticeSession.phase`; every decision about what comes
 * next is in the session.
 */
@Composable
fun ScenePracticeScreen(
    scenes: Scenes,
    sceneId: SceneId,
    routeId: String?,
    language: UiLanguage,
    isMember: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onUnlock: () -> Unit,
) {
    val scene = scenes.catalog.scene(sceneId) ?: return
    val session = remember(sceneId, routeId) {
        ScenePracticeSession(scene, scenes.progress, scene.stage?.route(routeId))
    }
    val phase by session.phase.collectAsState()
    var entered by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { scenes.stop() } }
    BackHandler(onBack = onClose)

    when (val now = phase) {
        is Phase.Finished -> SceneDoneScreen(scenes, scene, now.summary, language, isMember, onLook = onClose, onDone = onDone, onUnlock = onUnlock)
        is Phase.Practising -> {
            val step = session.steps[now.step]
            val question = scene.questions[step.questionIndex]
            Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
                TopBar(session, now, onClose)
                val stageStep = scene.stageStep(question.id)
                val route = session.route
                key(now.step) {
                    StagedQuestion(scenes, session, scene, step, question, stageStep, now.question, route, entered, language) { entered = true }
                }
            }
        }
    }
}

@Composable
private fun TopBar(session: ScenePracticeSession, phase: Phase.Practising, onClose: () -> Unit) {
    val revealed = phase.question is QuestionPhase.Revealed
    val review = session.steps[phase.step].mode == PracticeMode.Review
    Row(Modifier.fillMaxWidth().height(44.dp).padding(start = TujiSpace.S2, end = TujiSpace.S3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val closeLabel = stringResource(if (review) R.string.scene_end_review else R.string.scene_end_practice)
        Box(Modifier.size(44.dp).tujiClickable(onClick = onClose).semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
            Text("✕", style = TujiType.h3, color = TujiColor.Ink)
        }
        SceneSegments(
            session.steps.indices.map { i ->
                when {
                    i < phase.step || (i == phase.step && revealed) -> Segment.Done
                    i == phase.step -> Segment.Current
                    else -> Segment.Todo
                }
            },
            Modifier.weight(1f),
            spacing = 4.dp,
        )
        Text("${phase.step + 1} / ${session.steps.size}", style = TujiType.label, color = TujiColor.Ink2)
    }
}

@Composable
private fun StagedQuestion(
    scenes: Scenes,
    session: ScenePracticeSession,
    scene: PracticeScene,
    step: PracticeStep,
    question: SceneQuestion,
    stage: StageStep?,
    phase: QuestionPhase,
    route: StageRoute?,
    entered: Boolean,
    language: UiLanguage,
    onEnter: () -> Unit,
) {
    var line by remember { mutableStateOf<SpokenLine?>(null) }
    var subtitles by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<StageReply?>(null) }
    var glossed by remember { mutableStateOf<GlossToken?>(null) }
    var machine by remember { mutableStateOf<SceneMachine?>(stage?.machine?.makeMachine()) }
    val revealed = phase is QuestionPhase.Revealed
    val tried = (phase as? QuestionPhase.AnsweredWrong)?.tried.orEmpty()
    fun play(spoken: SpokenLine, rate: Float = 1f) {
        line = spoken
        scenes.play(spoken, rate)
    }
    LaunchedEffect(entered) {
        if ((entered || route == null) && line == null) step.phrasing.spoken?.let { play(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMax = maxOf(200.dp, maxHeight - 150.dp)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (stage != null) {
                    val props = stage.props.orEmpty().let { base -> base + chosen?.show.orEmpty().filter { it !in base } }
                    StageBackdropView(scenes, stage.background, props, stage.card, Modifier.fillMaxSize())
                    if (entered) stage.note?.let {
                        Text(
                            it.resolved(language),
                            style = TujiType.bodySmStrong,
                            color = TujiColor.Paper,
                            modifier = Modifier.padding(TujiSpace.S3).background(TujiColor.Ink.copy(alpha = 0.78f)).padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = panelMax).background(TujiColor.Paper).verticalScroll(rememberScrollState(), reverseScrolling = false).padding(TujiSpace.S3),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                if (!entered && route != null) {
                    Text(stringResource(R.string.scene_this_time), style = TujiType.label, color = TujiColor.Ink2)
                    Text(route.intent.resolved(language), style = TujiType.h3, color = TujiColor.Ink)
                    SceneCall(route.enter.resolved(language), onEnter)
                    return@Column
                }
                when (question.kind) {
                    SceneQuestion.Kind.Listening -> {
                        val shown = line ?: step.phrasing.spoken
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(speakerLabel(step.phrasing.speaker), style = TujiType.label, color = TujiColor.Ink2)
                            if (step.mode == PracticeMode.Review) Text("· " + stringResource(R.string.scene_review), style = TujiType.label, color = TujiColor.Ink2)
                        }
                        if (shown != null && (subtitles || revealed)) {
                            GlossedLine(shown.text, if (revealed) scene.gloss(shown.text) else null, TujiType.h3) { glossed = it }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                            step.phrasing.spoken?.let { original -> ScenePill(stringResource(R.string.scene_replay), { play(original) }) }
                            if (!revealed) {
                                ScenePill(stringResource(R.string.scene_say_again), { session.askAgain()?.let { play(it) } })
                                shown?.let { ScenePill(stringResource(R.string.scene_slow), { session.noteHint(); play(it, 0.8f) }) }
                                ScenePill(stringResource(R.string.scene_subtitles), {
                                    subtitles = !subtitles
                                    if (subtitles) session.noteHint()
                                }, pressed = subtitles)
                            }
                        }
                        if (!revealed && stage != null) {
                            chosen?.takeIf { "reply:" + it.id in tried }?.let { Result(it.result, fits = false, language) }
                            Text(
                                stringResource(if (stage.replies.orEmpty().any { !it.isSpoken }) R.string.scene_what_do else R.string.scene_what_say),
                                style = TujiType.h3,
                                color = TujiColor.Ink,
                            )
                            stage.replies(step.phrasing.id).forEach { reply ->
                                val missed = "reply:" + reply.id in tried
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .background(TujiColor.Paper2)
                                        .border(3.dp, if (missed) TujiColor.Alert else TujiColor.Paper2)
                                        .tujiClickable(enabled = !missed) {
                                            chosen = reply
                                            if (reply.isSpoken) scenes.say(reply)
                                            session.reply(reply)
                                        }
                                        .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    val ink = if (missed) TujiColor.Ink3 else TujiColor.Ink
                                    if (reply.isSpoken) {
                                        Text(reply.ja, style = TujiType.h3, color = ink)
                                        Text(reply.meaning.resolved(language), style = TujiType.bodySm, color = TujiColor.Ink2)
                                    } else {
                                        Text(reply.meaning.resolved(language), style = TujiType.h3, color = ink)
                                    }
                                }
                            }
                        } else if (!revealed) {
                            question.choices(step.phrasing.id).forEach { choice ->
                                val missed = choice.id in tried
                                Text(
                                    choice.text.resolved(language),
                                    style = TujiType.bodyStrong,
                                    color = if (missed) TujiColor.Ink3 else TujiColor.Ink,
                                    modifier = Modifier.fillMaxWidth().background(TujiColor.Paper2).border(3.dp, if (missed) TujiColor.Alert else TujiColor.Paper2)
                                        .tujiClickable(enabled = !missed) { session.answer(choice.id) }.padding(TujiSpace.S3),
                                )
                            }
                        }
                    }
                    SceneQuestion.Kind.Screen -> {
                        step.phrasing.spoken?.let { spoken ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                                Text(speakerLabel(step.phrasing.speaker), style = TujiType.label, color = TujiColor.Ink2, modifier = Modifier.weight(1f))
                                ScenePill(stringResource(R.string.scene_replay), { play(spoken) })
                                ScenePill(stringResource(R.string.scene_subtitles), {
                                    subtitles = !subtitles
                                    if (subtitles) session.noteHint()
                                }, pressed = subtitles || revealed)
                            }
                            if (subtitles || revealed) Text(spoken.text, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        }
                        val spec = stage?.machine
                        val running = machine
                        if (spec != null && running != null) {
                            MachineView(running, spec.brand) { action ->
                                val next = running.send(action)
                                machine = next
                                next.outcome?.let { session.finish(it, spec.fits(it)) }
                            }
                            if (tried.isNotEmpty() && !revealed) {
                                Result(question.goal, fits = false, language)
                                Box(
                                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).background(TujiColor.Paper2).tujiClickable { machine = spec.makeMachine() },
                                    contentAlignment = Alignment.Center,
                                ) { Text(stringResource(R.string.scene_restart), style = TujiType.bodyStrong, color = TujiColor.Ink) }
                            }
                        } else {
                            step.phrasing.screen?.let { screen ->
                                Text(question.goal.resolved(language), style = TujiType.bodySm, color = TujiColor.Ink2)
                                ScreenMockView(screen, tried, if (revealed) question.correctChoice?.id else null) { session.answer(it) }
                            }
                        }
                    }
                }
                if (revealed) {
                    chosen?.let { Result(it.result, fits = true, language) }
                    question.meaning?.let { Text(it.resolved(language), style = TujiType.bodySmStrong, color = TujiColor.Ink2) }
                    Text(question.explanation.resolved(language), style = TujiType.bodySm, color = TujiColor.Ink2)
                    Row(Modifier.defaultMinSize(minHeight = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                        Text(stringResource(R.string.scene_you_can_say), style = TujiType.label, color = TujiColor.Ink3)
                        Box(Modifier.weight(1f)) { GlossedLine(question.response.ja, scene.gloss(question.response.ja), TujiType.bodyStrong) { glossed = it } }
                        if (question.response.clip != null) ScenePlayButton(stringResource(R.string.scene_play_response), { scenes.play(question.response) }, filled = false)
                    }
                    val last = session.position?.let { it.first == it.second } ?: true
                    SceneCall(stringResource(if (last) R.string.scene_see_result else R.string.scene_next), { session.next() })
                }
            }
        }
    }
    GlossSheet(glossed, language) { glossed = null }
}

/** What happened after the traveller acted: plain when it went the way the trip wanted, marked when not. */
@Composable
private fun Result(text: LocalizedText, fits: Boolean, language: UiLanguage) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(if (fits) TujiColor.Accumulation else TujiColor.Alert))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text.resolved(language), style = TujiType.bodyStrong, color = TujiColor.Ink)
            if (!fits) Text(stringResource(R.string.scene_try_again), style = TujiType.bodySm, color = TujiColor.Ink2)
        }
    }
}

// MARK: - 練完

@Composable
private fun SceneDoneScreen(
    scenes: Scenes,
    scene: PracticeScene,
    summary: ScenePracticeSession.Summary,
    language: UiLanguage,
    isMember: Boolean,
    onLook: () -> Unit,
    onDone: () -> Unit,
    onUnlock: () -> Unit,
) {
    val book by scenes.progress.book.collectAsState()
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S4)) {
            Text(stringResource(R.string.scene_practice), style = TujiType.label, color = TujiColor.Ink2)
            Text(stringResource(R.string.scene_finished, scene.title.resolved(language)), style = TujiType.h2, color = TujiColor.Ink, modifier = Modifier.padding(top = 6.dp))
            val flags = scene.practised(book)
            Row(Modifier.padding(top = TujiSpace.S3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                SceneSegments(practisedSegments(flags), Modifier.weight(1f))
                Text("${flags.count { it }} / ${flags.size}", style = TujiType.bodySmStrong, color = TujiColor.Accumulation)
            }
            Column(Modifier.padding(top = TujiSpace.S3)) {
                SceneRule()
                summary.results.forEach { result ->
                    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        OneLine(result.question.meaning?.resolved(language).orEmpty(), TujiType.bodySmStrong, TujiColor.Ink2, Modifier.weight(1f))
                        if (result.usedHint && result.wrongTries == 0) {
                            Text(stringResource(R.string.scene_used_hint), style = TujiType.label, color = TujiColor.Ink2, modifier = Modifier.background(TujiColor.Paper2).padding(horizontal = TujiSpace.S2, vertical = 3.dp))
                        }
                        if (result.wrongTries > 0) {
                            Row(Modifier.background(TujiColor.Paper2)) {
                                Box(Modifier.width(3.dp).height(24.dp).background(TujiColor.Alert))
                                Text(stringResource(R.string.scene_wrong_times, result.wrongTries), style = TujiType.label, color = TujiColor.Ink2, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                    SceneRule()
                }
            }
            if (!isMember) {
                Column(Modifier.padding(top = 12.dp).border(1.dp, TujiColor.Rule).padding(TujiSpace.S3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.scene_lifetime), style = TujiType.label, color = TujiColor.Ink2)
                    Text(stringResource(R.string.scene_member_offer), style = TujiType.bodySm, color = TujiColor.Ink)
                    Box(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).background(TujiColor.Ink).tujiClickable(onClick = onUnlock),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.scene_see_lifetime), style = TujiType.bodyStrong, color = TujiColor.Paper) }
                }
            }
        }
        Column(Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S3)) {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).tujiClickable(onClick = onLook), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.scene_look), style = TujiType.label, color = TujiColor.Ink2)
            }
            SceneCall(stringResource(R.string.scene_done), onDone, trailing = null)
        }
    }
}
