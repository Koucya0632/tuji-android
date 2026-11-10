package app.tuji.android.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.scenes.GlossToken
import app.tuji.android.core.scenes.LocalDay
import app.tuji.android.core.scenes.PracticeScene
import app.tuji.android.core.scenes.SceneId
import app.tuji.android.core.scenes.SceneProgressBook
import app.tuji.android.core.scenes.SceneQuestion
import app.tuji.android.core.scenes.ScenePractice
import app.tuji.android.core.scenes.SceneStatus
import app.tuji.android.core.scenes.canPractise
import app.tuji.android.core.scenes.minutes
import app.tuji.android.core.scenes.practiceCount
import app.tuji.android.core.scenes.practised
import app.tuji.android.core.scenes.resumeNumber

/** What the list and the scene page say about where a scene stands. */
@Composable
internal fun sceneStatusText(scene: PracticeScene, status: SceneStatus, book: SceneProgressBook): String = when (status) {
    SceneStatus.NotStarted -> stringResource(R.string.scene_count_minutes, scene.practiceCount, scene.minutes)
    is SceneStatus.InProgress -> stringResource(R.string.scene_at_question, scene.resumeNumber(book))
    is SceneStatus.ReviewDue -> stringResource(R.string.scene_review_today)
    is SceneStatus.ReviewLater -> stringResource(R.string.scene_review_tomorrow)
    SceneStatus.Complete -> stringResource(R.string.scene_reviewed_all)
}

// MARK: - 場面練習 list

@Composable
fun SceneListScreen(scenes: Scenes, language: UiLanguage, isMember: Boolean, onBack: () -> Unit, onOpen: (SceneId) -> Unit) {
    val book by scenes.progress.book.collectAsState()
    val catalog = scenes.catalog
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDay.today()
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(onLeading = onBack, leadingLabel = stringResource(R.string.scene_back))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = TujiSpace.S4).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(stringResource(R.string.scene_practice), style = TujiType.h2, color = TujiColor.Ink)
                Text(stringResource(R.string.scene_list_lede), style = TujiType.bodySm, color = TujiColor.Ink2)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = TujiSpace.S4).padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                Chip(stringResource(R.string.scene_all), filter == null) { filter = null }
                catalog.groups.forEach { group -> Chip(group.title.resolved(language), filter == group.id) { filter = group.id } }
            }
            catalog.groups.filter { filter == null || it.id == filter }.forEach { group ->
                Text(
                    group.title.resolved(language),
                    style = TujiType.label,
                    color = TujiColor.Ink2,
                    modifier = Modifier.padding(horizontal = TujiSpace.S4).padding(top = 28.dp, bottom = TujiSpace.S2),
                )
                SceneRule()
                group.scenes.forEach { scene ->
                    val status = ScenePractice.status(scene, book, today)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .tujiClickable { onOpen(scene.id) }
                            .padding(start = TujiSpace.S4, end = TujiSpace.S3, top = TujiSpace.S2, bottom = TujiSpace.S2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(scene.title.resolved(language), style = TujiType.bodyStrong, color = TujiColor.Ink)
                            if (status == SceneStatus.NotStarted) {
                                Text(stringResource(R.string.scene_count, scene.practiceCount), style = TujiType.bodySm, color = TujiColor.Ink3)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SceneSegments(practisedSegments(scene.practised(book)), Modifier.width(64.dp), height = 3.dp, spacing = 2.dp)
                                    Text(sceneStatusText(scene, status, book), style = TujiType.bodySm, color = TujiColor.Ink2)
                                }
                            }
                        }
                        if (!scene.canPractise(isMember)) {
                            Text(stringResource(R.string.scene_lifetime), style = TujiType.label, color = TujiColor.Ink3)
                        } else {
                            Text("›", style = TujiType.h3, color = TujiColor.Ink3)
                        }
                    }
                    SceneRule()
                }
            }
            Text(
                stringResource(R.string.scene_offline),
                style = TujiType.label,
                color = TujiColor.Ink2,
                modifier = Modifier.padding(TujiSpace.S4),
            )
        }
    }
}

@Composable
private fun Chip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .background(if (selected) TujiColor.Ink else Color.Transparent, CircleShape)
            .border(1.dp, if (selected) Color.Transparent else TujiColor.Rule, CircleShape)
            .tujiClickable(onClick = onClick)
            .padding(horizontal = TujiSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = TujiType.label, color = if (selected) TujiColor.Paper else TujiColor.Ink2)
    }
}

// MARK: - One 場面's page, which is also 進店前看一眼

@Composable
fun SceneDetailScreen(
    scenes: Scenes,
    sceneId: SceneId,
    language: UiLanguage,
    isMember: Boolean,
    onBack: () -> Unit,
    onPractise: (routeId: String?) -> Unit,
    onUnlock: () -> Unit,
) {
    val scene = scenes.catalog.scene(sceneId) ?: return
    val book by scenes.progress.book.collectAsState()
    var glossed by remember { mutableStateOf<GlossToken?>(null) }
    DisposableEffect(Unit) { onDispose { scenes.stop() } }
    val status = ScenePractice.status(scene, book, LocalDay.today())
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(onLeading = onBack, leadingLabel = stringResource(R.string.scene_back))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = TujiSpace.S5)) {
            Column(
                Modifier.fillMaxWidth().padding(top = TujiSpace.S2).background(TujiColor.Ink).padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.scene_practice), style = TujiType.label, color = TujiColor.Paper.copy(alpha = 0.7f))
                    Text(scene.title.resolved(language), style = TujiType.h2, color = TujiColor.Paper)
                    Text(scene.summary.resolved(language), style = TujiType.bodySm, color = TujiColor.Paper.copy(alpha = 0.85f))
                }
                if (status != SceneStatus.NotStarted) {
                    val practised = scene.practised(book)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                        SceneSegments(practisedSegments(practised), Modifier.weight(1f), onInk = true)
                        Text("${practised.count { it }} / ${practised.size}", style = TujiType.bodySmStrong, color = TujiColor.AccumulationSoft)
                    }
                }
                if (scene.canPractise(isMember)) {
                    val trailing = when (status) {
                        is SceneStatus.InProgress -> stringResource(R.string.scene_question_n, scene.resumeNumber(book))
                        is SceneStatus.ReviewDue -> stringResource(R.string.scene_count, status.count)
                        SceneStatus.NotStarted -> stringResource(R.string.scene_about_minutes, scene.minutes)
                        else -> null
                    }
                    StartCall(stringResource(startTitle(status)), trailing) { onPractise(null) }
                    scene.stage?.routes?.filter { it.isOptional }?.forEach { route ->
                        Row(
                            Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).tujiClickable { onPractise(route.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.scene_other_trip, route.title.resolved(language)),
                                style = TujiType.bodySmStrong,
                                color = TujiColor.Paper,
                                modifier = Modifier.weight(1f),
                            )
                            Text("›", style = TujiType.h3, color = TujiColor.Paper)
                        }
                    }
                } else {
                    StartCall(stringResource(R.string.scene_see_lifetime), null, onUnlock)
                    Text(stringResource(R.string.scene_needs_lifetime), style = TujiType.label, color = TujiColor.Paper.copy(alpha = 0.7f))
                }
            }
            Column(Modifier.padding(horizontal = TujiSpace.S4).padding(top = 32.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(stringResource(R.string.scene_look), style = TujiType.h3, color = TujiColor.Ink)
                Text(stringResource(R.string.scene_offline), style = TujiType.label, color = TujiColor.Ink2)
            }
            SceneRule()
            scene.questions.forEachIndexed { index, question ->
                LookRow(scenes, scene, question, index + 1, language) { glossed = it }
                SceneRule()
            }
        }
    }
    GlossSheet(glossed, language) { glossed = null }
}

private fun startTitle(status: SceneStatus) = when (status) {
    SceneStatus.NotStarted -> R.string.scene_start
    is SceneStatus.InProgress -> R.string.scene_continue
    is SceneStatus.ReviewDue -> R.string.scene_start_review
    else -> R.string.scene_again
}

@Composable
private fun StartCall(title: String, trailing: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(TujiColor.Current).tujiClickable(onClick = onClick).padding(horizontal = 20.dp, vertical = TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TujiType.h3, color = TujiColor.Ink, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = TujiType.bodySmStrong, color = TujiColor.Ink.copy(alpha = 0.75f)) }
    }
}

@Composable
private fun LookRow(scenes: Scenes, scene: PracticeScene, question: SceneQuestion, number: Int, language: UiLanguage, onPick: (GlossToken) -> Unit) {
    Row(Modifier.padding(start = TujiSpace.S4, end = 12.dp, top = 18.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number", style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.width(16.dp).padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                SceneSpeakerTag(question.lookSpeaker)
                question.meaning?.let { Text(it.resolved(language), style = TujiType.bodySmStrong, color = TujiColor.Ink2) }
            }
            val line = question.lookLine
            if (line != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Box(Modifier.weight(1f)) { GlossedLine(line.text, scene.gloss(line.text), TujiType.h3, onPick) }
                    ScenePlayButton(stringResource(R.string.scene_play), { scenes.play(line) })
                }
            } else {
                question.firstPhrasing?.screen?.let { ScreenMockView(it, emptySet(), question.correctChoice?.id, null) }
            }
            Row(Modifier.defaultMinSize(minHeight = 44.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(stringResource(R.string.scene_response), style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.width(28.dp))
                Box(Modifier.weight(1f)) { GlossedLine(question.response.ja, scene.gloss(question.response.ja), TujiType.bodyStrong, onPick) }
                if (question.response.clip != null) {
                    ScenePlayButton(stringResource(R.string.scene_play_response), { scenes.play(question.response) }, filled = false)
                }
            }
            if (question.kind == SceneQuestion.Kind.Screen) {
                Text(stringResource(R.string.scene_screens_vary), style = TujiType.label, color = TujiColor.Ink3)
            }
        }
    }
}

/** Truncating single-line text, for the cards. */
@Composable
internal fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) =
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

@Composable
internal fun Gap(height: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(height))
