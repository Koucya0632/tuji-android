package app.tuji.android.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.scenes.LocalDay
import app.tuji.android.core.scenes.SceneCardPick
import app.tuji.android.core.scenes.SceneCards
import app.tuji.android.core.scenes.SceneId
import app.tuji.android.core.scenes.SceneLinks
import app.tuji.android.core.scenes.SceneStatus
import app.tuji.android.core.scenes.canPractise
import app.tuji.android.core.scenes.minutes
import app.tuji.android.core.scenes.practiceCount

/** 場面 are written for people learning Japanese; other directions see none of these entries. */
val LearningDirection.hasScenes: Boolean get() = this == LearningDirection.ZH_JA

/**
 * 今天's scene card, beside the cat: one scene due for review, else one half
 * done, else the next one never opened, else the last one practised. A scene
 * this account cannot practise is never offered.
 */
@Composable
fun SceneTodayCard(
    scenes: Scenes,
    language: UiLanguage,
    isMember: Boolean,
    onOpenList: () -> Unit,
    onOpen: (SceneId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book by scenes.progress.book.collectAsState()
    val pick = SceneCards.pick(scenes.catalog.scenes, book, LocalDay.today()) { it.canPractise(isMember) } ?: return
    Column(modifier.height(198.dp).background(TujiColor.Paper).border(1.dp, TujiColor.Rule)) {
        Row(Modifier.fillMaxWidth().padding(start = TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scene_practice), style = TujiType.label, color = TujiColor.Ink2, modifier = Modifier.weight(1f))
            Box(Modifier.height(40.dp).tujiClickable(onClick = onOpenList).padding(start = TujiSpace.S3, end = 10.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.scene_all) + " ›", style = TujiType.label, color = TujiColor.Ink2)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().tujiClickable { onOpen(pick.scene.id) }.padding(start = TujiSpace.S3, end = TujiSpace.S3, top = 2.dp, bottom = 14.dp),
        ) {
            pick.scene.questions.firstOrNull()?.lookLine?.let {
                OneLine("「${it.text}」", TujiType.h3.copy(fontSize = 20.sp), TujiColor.Ink)
            }
            OneLine(pick.scene.title.resolved(language), TujiType.bodySmStrong, TujiColor.Ink2, Modifier.padding(top = 2.dp))
            Spacer(Modifier.weight(1f))
            val practised = if (pick.state == SceneCardPick.State.NotStarted) pick.practised.map { false } else pick.practised
            SceneSegments(practisedSegments(practised), Modifier.fillMaxWidth())
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when (val state = pick.state) {
                        SceneCardPick.State.NotStarted ->
                            OneLine(stringResource(R.string.scene_count_minutes, pick.scene.practiceCount, pick.scene.minutes), TujiType.bodySm, TujiColor.Ink2)
                        is SceneCardPick.State.InProgress -> OneLine(stringResource(R.string.scene_at_question, state.at), TujiType.bodySm, TujiColor.Ink2)
                        SceneCardPick.State.ReviewDue -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(TujiColor.Current))
                            Text(stringResource(R.string.scene_review_today), style = TujiType.label, color = TujiColor.Ink)
                        }
                        is SceneCardPick.State.AllDone -> OneLine(
                            stringResource(if (state.status == SceneStatus.Complete) R.string.scene_reviewed_all else R.string.scene_review_tomorrow),
                            TujiType.bodySm,
                            TujiColor.Ink2,
                        )
                    }
                }
                val verb = when (pick.state) {
                    SceneCardPick.State.NotStarted -> R.string.scene_verb_start
                    is SceneCardPick.State.InProgress -> R.string.scene_verb_continue
                    SceneCardPick.State.ReviewDue -> R.string.scene_verb_review
                    is SceneCardPick.State.AllDone -> R.string.scene_verb_look
                }
                Text(stringResource(verb) + " ›", style = TujiType.label, color = TujiColor.Ink)
            }
        }
    }
}

/**
 * A word page's 場面 section: the 問句 that ask about this word, each playable,
 * and a way into its scene. A page opened from one scene leaves that scene out.
 */
@Composable
fun WordSceneSection(scenes: Scenes, wordId: String, excluding: SceneId?, language: UiLanguage, onOpen: (SceneId) -> Unit) {
    val mentions = SceneLinks.mentions(wordId, scenes.catalog, excluding)
    if (mentions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Text(stringResource(R.string.scene_word_section), style = TujiType.label.copy(letterSpacing = 2.sp), color = TujiColor.Ink3, modifier = Modifier.padding(top = TujiSpace.S4))
        mentions.forEach { mention ->
            Column(Modifier.fillMaxWidth().border(1.dp, TujiColor.Rule)) {
                mention.questions.forEach { question ->
                    Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = TujiSpace.S3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SceneSpeakerTag(question.lookSpeaker)
                            question.lookLine?.let { Text(it.text, style = TujiType.bodyStrong.copy(fontSize = 17.sp), color = TujiColor.Ink) }
                            question.meaning?.let { Text(it.resolved(language), style = TujiType.bodySmStrong, color = TujiColor.Ink3) }
                        }
                        question.lookLine?.let { line -> ScenePlayButton(stringResource(R.string.scene_play), { scenes.play(line) }) }
                    }
                    SceneRule()
                }
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).tujiClickable { onOpen(mention.scene.id) }.padding(start = 18.dp, end = TujiSpace.S3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mention.scene.title.resolved(language), style = TujiType.bodyStrong, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.scene_count, mention.scene.practiceCount) + "  ›", style = TujiType.bodySm, color = TujiColor.Ink2)
                }
            }
        }
    }
}

/** A theme page's way into the 場面 that use its words, at most two. */
@Composable
fun TopicSceneEntries(scenes: Scenes, topic: String, topicOf: Map<String, String>, language: UiLanguage, onOpen: (SceneId) -> Unit) {
    SceneLinks.scenesForTopic(topic, topicOf, scenes.catalog).forEach { scene ->
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).background(TujiColor.Paper2).tujiClickable { onOpen(scene.id) }
                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Box(Modifier.size(36.dp).background(TujiColor.Ink), contentAlignment = Alignment.Center) { Text("💬", style = TujiType.body) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.scene_topic_entry, scene.title.resolved(language)), style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(scene.summary.resolved(language), style = TujiType.bodySm, color = TujiColor.Ink3, maxLines = 2)
            }
            Text("›", style = TujiType.h3, color = TujiColor.Ink3)
        }
    }
}
