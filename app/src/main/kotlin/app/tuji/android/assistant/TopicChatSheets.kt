package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiPullToRefresh
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.ChatReview
import app.tuji.android.core.model.TopicChatSummary
import app.tuji.android.form.TujiSheet
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** 更換主題 — iOS's `TopicPickerSheet`. */
@Composable
internal fun TopicPickerSheet(topics: List<TopicChoice>, current: String?, onDismiss: () -> Unit, pick: (String) -> Unit) {
    TujiSheet(
        title = stringResource(R.string.chat_change_topic),
        onDismiss = onDismiss,
        maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f,
    ) {
        TopicChoiceList(topics, current, footer = stringResource(R.string.chat_switch_note), onCurrent = onDismiss, pick = pick)
    }
}

/**
 * The topics, as a list: in 更換主題's sheet, and on the conversation screen
 * itself when it was opened without one. A theme with too few words is shown
 * but cannot be picked — the server makes the same check.
 */
@Composable
internal fun TopicChoiceList(
    topics: List<TopicChoice>,
    current: String?,
    footer: String? = null,
    onCurrent: () -> Unit = {},
    pick: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(topics, key = { it.id }) { topic ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .tujiClickable(enabled = topic.usable) { if (topic.id == current) onCurrent() else pick(topic.id) }
                    .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).background(TujiColor.Paper2)) {
                    if (!topic.imageUrl.isNullOrBlank()) {
                        AsyncImage(topic.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(topic.name, style = TujiType.bodyStrong, color = if (topic.usable) TujiColor.Ink else TujiColor.Ink3)
                    Text(
                        if (topic.usable) stringResource(R.string.chat_topic_words, topic.wordCount) else stringResource(R.string.chat_topic_unusable),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                    )
                }
                if (topic.id == current) Text(stringResource(R.string.chat_current_topic), style = TujiType.label, color = TujiColor.Ink2)
            }
            Box(Modifier.fillMaxWidth().padding(start = TujiSpace.S4).height(TujiBorder.Bw1).background(TujiColor.Rule))
        }
        footer?.let {
            item { Text(it, style = TujiType.bodySm, color = TujiColor.Ink3, modifier = Modifier.padding(TujiSpace.S4)) }
        }
    }
}

/**
 * 回顧 — at the end of an ended conversation: what the person takes away. Their
 * sentences (a corrected one with what they wrote a tap away), the 答法 they
 * answered, and one suggestion marked as one.
 */
@Composable
internal fun ChatReviewSection(
    chat: TopicChatSummary,
    failed: Boolean,
    showTranslation: Boolean,
    showReading: Boolean,
    retry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Text(stringResource(R.string.chat_review_ended), style = TujiType.label, color = TujiColor.Ink3)
        Text(stringResource(R.string.chat_review_spent, chat.spent ?: 0), style = TujiType.bodySm, color = TujiColor.Ink2)
        val review = chat.review
        when {
            review != null -> {
                Text(stringResource(R.string.chat_review), style = TujiType.h3, color = TujiColor.Ink)
                Text(
                    stringResource(R.string.chat_review_topics, review.topics.joinToString("、") { it.name }),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                )
                if (review.sentences.isNotEmpty()) {
                    ReviewHeading(stringResource(R.string.chat_review_sentences))
                    review.sentences.forEach { ReviewSentenceRow(it, showTranslation, showReading) }
                }
                if (review.answerTypes.isNotEmpty()) {
                    ReviewHeading(stringResource(R.string.chat_review_answer_types))
                    review.answerTypes.forEach { answered ->
                        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                            Text(answered.pattern, style = TujiType.bodyStrong, color = TujiColor.Ink)
                            Text(stringResource(answerTypeName(answered.type)), style = TujiType.label, color = TujiColor.Ink3)
                            Text("→ ${answered.answer}", style = TujiType.bodySm, color = TujiColor.Ink2)
                        }
                    }
                }
                review.tryNext?.let { tryNext ->
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(stringResource(R.string.chat_review_try_next), style = TujiType.label, color = TujiColor.BrandSecondary)
                        Text(stringResource(answerTypeName(tryNext.answerType)), style = TujiType.label, color = TujiColor.Ink3)
                        ChatSentence(tryNext.text, showTranslation, showReading)
                    }
                }
            }
            failed -> {
                Text(stringResource(R.string.chat_review_failed), style = TujiType.bodySm, color = TujiColor.Ink2)
                TextAction(stringResource(R.string.chat_review_retry), retry)
            }
            chat.answers == 0 -> Text(stringResource(R.string.chat_review_none), style = TujiType.bodySm, color = TujiColor.Ink2)
            else -> TextAction(stringResource(R.string.chat_review_make), retry)
        }
    }
}

@Composable
private fun ReviewHeading(text: String) = Text(text, style = TujiType.label, color = TujiColor.Ink3)

/** One of 「你說過的」: the sentence to take away, and — when it was corrected or said for them — what they wrote, behind a tap. */
@Composable
private fun ReviewSentenceRow(sentence: ChatReview.Sentence, showTranslation: Boolean, showReading: Boolean) {
    var showsOriginal by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        when (sentence.kind) {
            "expressed" -> R.string.chat_review_expressed
            "corrected" -> R.string.chat_review_corrected
            else -> null
        }?.let { Text(stringResource(it), style = TujiType.label, color = TujiColor.BrandSecondary) }
        ChatSentence(sentence.text, showTranslation, showReading)
        sentence.original?.let { original ->
            if (showsOriginal) {
                Text(stringResource(R.string.chat_review_original, original), style = TujiType.bodySm, color = TujiColor.Ink3)
            } else {
                Text(
                    stringResource(R.string.chat_review_show_original),
                    style = TujiType.label,
                    color = TujiColor.Ink2,
                    modifier = Modifier.heightIn(min = 32.dp).tujiClickable { showsOriginal = true }.padding(vertical = TujiSpace.S2),
                )
            }
        }
    }
}

/** The name of a 答法, as the interface shows it. */
internal fun answerTypeName(type: String): Int = when (type) {
    "what" -> R.string.chat_answer_what
    "describe" -> R.string.chat_answer_describe
    "choose_one" -> R.string.chat_answer_choose_one
    "where" -> R.string.chat_answer_where
    "when" -> R.string.chat_answer_when
    "who" -> R.string.chat_answer_who
    "how_often" -> R.string.chat_answer_how_often
    "how_many" -> R.string.chat_answer_how_many
    "want_to" -> R.string.chat_answer_want_to
    "like" -> R.string.chat_answer_like
    "reason" -> R.string.chat_answer_reason
    "experience" -> R.string.chat_answer_experience
    "plan" -> R.string.chat_answer_plan
    "agree" -> R.string.chat_answer_agree
    "ask_back" -> R.string.chat_answer_ask_back
    else -> R.string.chat_answer_other
}

/**
 * 對話紀錄 — iOS's `TopicChatHistoryView`: saved conversations in the language
 * being learned. Ended ones are for reading; deleting one deletes its review and
 * anything still being written. iOS deletes with a swipe; here a long press asks.
 */
@Composable
fun TopicChatHistoryScreen(model: TopicChatModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<TopicChatSummary?>(null) }
    LaunchedEffect(model) { model.loadHistory() }
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            title = stringResource(R.string.chat_history),
            showsRule = true,
        )
        TujiPullToRefresh(onRefresh = { model.loadHistory() }) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.history.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.chat_history_empty),
                            style = TujiType.bodySm,
                            color = TujiColor.Ink3,
                            modifier = Modifier.padding(TujiSpace.S4),
                        )
                    }
                }
                items(state.history, key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .tujiClickable(onLongClick = { deleting = chat }) { onOpen(chat.id) }
                            .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.topicName, style = TujiType.bodyStrong, color = TujiColor.Ink)
                                if (chat.isActive) {
                                    Text(stringResource(R.string.chat_history_active), style = TujiType.label, color = TujiColor.BrandSecondary)
                                }
                            }
                            Text(stringResource(R.string.chat_history_answers, chat.answers), style = TujiType.bodySm, color = TujiColor.Ink3)
                        }
                        Text("→", style = TujiType.h3, color = TujiColor.Ink3)
                    }
                    Box(Modifier.fillMaxWidth().padding(start = TujiSpace.S4).height(TujiBorder.Bw1).background(TujiColor.Rule))
                }
            }
        }
    }
    deleting?.let { chat ->
        TujiPrompt(
            title = stringResource(R.string.chat_delete_title),
            message = stringResource(R.string.chat_delete_message),
            confirm = stringResource(R.string.chat_delete_confirm),
            cancel = stringResource(R.string.chat_cancel),
            onConfirm = {
                deleting = null
                scope.launch { model.delete(chat.id) }
            },
            onCancel = { deleting = null },
            style = TujiPromptStyle.Destructive,
        )
    }
}
