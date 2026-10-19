package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaSentence
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiMenu
import app.tuji.android.core.design.TujiMenuItem
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberReduceMotion
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.ChatAITurn
import app.tuji.android.core.model.ChatMessage
import app.tuji.android.core.model.ChatOption
import app.tuji.android.core.model.ChatPattern
import app.tuji.android.core.model.ChatSupport
import app.tuji.android.core.model.ChatTargetText
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.TopicChatAssistKind
import app.tuji.android.core.model.TopicChatSummary
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.study.SentenceSpeakerButton
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** One 圖鑑 theme a conversation can be about. */
data class TopicChoice(val id: String, val name: String, val imageUrl: String?, val wordCount: Int) {
    /** A theme with enough words to talk about — the server's `MIN_TOPIC_WORDS` rule too. */
    val usable: Boolean get() = wordCount >= MINIMUM_WORDS

    companion object {
        const val MINIMUM_WORDS = 6
    }
}

/**
 * 圖鑑主題對話 — iOS's `TopicChatView`. Top: the topic (tap to change it) and a
 * menu for the reading supports and ending. Middle: the conversation, where
 * only the newest turn offers answer actions. Bottom: the draft, 送出／停止,
 * and 幫我接話 — one entry that opens the supports.
 */
@Composable
fun TopicChatScreen(
    model: TopicChatModel,
    topics: List<TopicChoice>,
    speech: WordSpeaking?,
    accent: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenMembership: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var supportsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model) { model.load() }
    DisposableEffect(model) { onDispose { model.leave() } }
    val japanese = (state.chat?.learningDirection?.takeIf { it.isNotEmpty() } ?: model.direction) == "zh-ja"
    val language = if (japanese) TargetLanguage.JA else TargetLanguage.EN

    Column(Modifier.fillMaxSize().background(TujiColor.Paper).imePadding()) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            showsRule = true,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
                    state.chat?.let { chat -> TopicButton(chat, topics, state.isActive) { picking = true } }
                    Box {
                        val menuLabel = stringResource(R.string.chat_menu)
                        Box(
                            Modifier
                                .size(44.dp)
                                .tujiClickable { menuOpen = true }
                                .semantics { contentDescription = menuLabel; role = Role.Button },
                            contentAlignment = Alignment.Center,
                        ) { TujiGlyph.More() }
                        if (menuOpen) {
                            TujiMenu(
                                items = menuItems(state, model, japanese, onOpenHistory, scope, onDelete = { confirmingDelete = true }),
                                onDismiss = { menuOpen = false },
                            )
                        }
                    }
                }
            },
        )
        when (val phase = state.phase) {
            TopicChatModel.Phase.Idle, TopicChatModel.Phase.Loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
            ) {
                TujiIndeterminateBar(Modifier.width(60.dp))
                Text(stringResource(R.string.chat_preparing), style = TujiType.bodySm, color = TujiColor.Ink3)
            }
            is TopicChatModel.Phase.Choosing -> ResumeChoice(phase.active, model, topics, scope)
            TopicChatModel.Phase.Picking -> Column {
                Column(Modifier.padding(TujiSpace.S4), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Text(stringResource(R.string.chat_pick_title), style = TujiType.h2, color = TujiColor.Ink)
                    Text(stringResource(R.string.chat_pick_detail), style = TujiType.bodySm, color = TujiColor.Ink2)
                }
                TopicChoiceList(topics, current = null) { scope.launch { model.startNew(it) } }
            }
            is TopicChatModel.Phase.Failed -> FailedState(phase.failure, onOpenMembership) { scope.launch { model.load() } }
            TopicChatModel.Phase.Ready -> {
                Conversation(state, model, topics, language, speech, accent, onOpenStatement, Modifier.weight(1f))
                if (state.isActive) {
                    Composer(state, model, supportsOpen, { supportsOpen = it })
                } else {
                    state.chat?.topicId?.let { topicId ->
                        Column {
                            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
                            TujiButton(
                                text = stringResource(R.string.chat_again),
                                onClick = { scope.launch { model.startNew(topicId) } },
                                modifier = Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                            )
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        TopicPickerSheet(topics, current = state.chat?.topicId, onDismiss = { picking = false }) { topicId ->
            picking = false
            scope.launch { model.switchTopic(topicId) }
        }
    }
    state.pendingFill?.let {
        // Three answers, not two: the replace/append choice is iOS's confirmation dialog.
        TujiPrompt(
            title = stringResource(R.string.chat_fill_title),
            message = null,
            confirm = stringResource(R.string.chat_fill_replace),
            cancel = stringResource(R.string.chat_cancel),
            onConfirm = { model.resolveFill(TopicChatModel.FillChoice.Replace) },
            onCancel = { model.resolveFill(TopicChatModel.FillChoice.Cancel) },
            alternative = stringResource(R.string.chat_fill_append),
            onAlternative = { model.resolveFill(TopicChatModel.FillChoice.Append) },
        )
    }
    if (confirmingDelete) {
        TujiPrompt(
            title = stringResource(R.string.chat_delete_title),
            message = stringResource(R.string.chat_delete_message),
            confirm = stringResource(R.string.chat_delete_confirm),
            cancel = stringResource(R.string.chat_cancel),
            onConfirm = {
                confirmingDelete = false
                val id = state.chat?.id ?: return@TujiPrompt
                scope.launch { if (model.delete(id)) onBack() }
            },
            onCancel = { confirmingDelete = false },
            style = TujiPromptStyle.Destructive,
        )
    }
}

@Composable
private fun menuItems(
    state: TopicChatModel.State,
    model: TopicChatModel,
    japanese: Boolean,
    onOpenHistory: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onDelete: () -> Unit,
): List<TujiMenuItem> {
    val translation = stringResource(R.string.chat_show_translation)
    val reading = stringResource(R.string.chat_show_reading)
    val history = stringResource(R.string.chat_history)
    val endReview = stringResource(R.string.chat_end_review)
    val end = stringResource(R.string.chat_end)
    val delete = stringResource(R.string.chat_delete)
    return buildList {
        add(TujiMenuItem((if (state.showTranslation) "✓ " else "") + translation) { model.setShowTranslation(!state.showTranslation) })
        if (japanese) add(TujiMenuItem((if (state.showReading) "✓ " else "") + reading) { model.setShowReading(!state.showReading) })
        add(TujiMenuItem(history, onClick = onOpenHistory))
        if (state.isActive) {
            add(TujiMenuItem(endReview, enabled = (state.chat?.answers ?: 0) > 0) { scope.launch { model.end(review = true) } })
            add(TujiMenuItem(end) { scope.launch { model.end(review = false) } })
        } else if (state.chat != null) {
            add(TujiMenuItem(delete, destructive = true, onClick = onDelete))
        }
    }
}

@Composable
private fun TopicButton(chat: TopicChatSummary, topics: List<TopicChoice>, active: Boolean, onClick: () -> Unit) {
    val change = stringResource(R.string.chat_change_topic)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .tujiClickable(enabled = active, onClick = onClick)
            .semantics(mergeDescendants = true) { if (active) contentDescription = "${chat.topicName}，$change" },
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        topics.firstOrNull { it.id == chat.topicId }?.imageUrl?.let {
            AsyncImage(it, null, Modifier.size(28.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        Column {
            Text(stringResource(R.string.chat_current_topic), style = TujiType.label, color = TujiColor.Ink3)
            Text(chat.topicName, style = TujiType.bodySmStrong, color = TujiColor.Ink, maxLines = 1)
        }
        if (active) {
            Text(
                change,
                style = TujiType.label,
                color = TujiColor.Ink2,
                modifier = Modifier.heightIn(min = 28.dp).background(TujiColor.Paper2).padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
            )
        }
    }
}

@Composable
private fun ResumeChoice(
    active: TopicChatSummary,
    model: TopicChatModel,
    topics: List<TopicChoice>,
    // The screen's scope, not this choice's: the choice leaves the moment loading begins, and its own scope would cancel the read.
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        MascotFigure(MascotPose.Wave, 72.dp)
        Text(stringResource(R.string.chat_resume_title), style = TujiType.h2, color = TujiColor.Ink)
        Text(stringResource(R.string.chat_resume_topic, active.topicName), style = TujiType.body, color = TujiColor.Ink2)
        TujiButton(text = stringResource(R.string.chat_resume_continue), onClick = { scope.launch { model.continueActive() } })
        when (val entry = model.entry) {
            is TopicChatEntry.Topic -> {
                val name = topics.firstOrNull { it.id == entry.topicId }?.name ?: entry.topicId
                TujiButton(
                    text = stringResource(R.string.chat_resume_new_topic, name),
                    onClick = { scope.launch { model.startNew(entry.topicId) } },
                    style = TujiButtonStyle.Secondary,
                )
                Text(stringResource(R.string.chat_resume_note), style = TujiType.bodySm, color = TujiColor.Ink3)
            }
            TopicChatEntry.Choose -> {
                TujiButton(text = stringResource(R.string.chat_resume_pick), onClick = model::pickNewTopic, style = TujiButtonStyle.Secondary)
                Text(stringResource(R.string.chat_resume_note), style = TujiType.bodySm, color = TujiColor.Ink3)
            }
            is TopicChatEntry.Saved -> Unit
        }
    }
}

@Composable
private fun FailedState(failure: TopicChatModel.Failure, onOpenMembership: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(TujiSpace.S5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
    ) {
        if (failure == TopicChatModel.Failure.Membership) {
            MascotEmptyState(
                title = stringResource(R.string.chat_membership_title),
                message = stringResource(R.string.chat_membership_detail),
                pose = MascotPose.Peek,
            )
            TujiButton(text = stringResource(R.string.vocabulary_membership_action), onClick = onOpenMembership)
        } else {
            MascotFigure(MascotPose.Sleep, 96.dp)
            Text(stringResource(R.string.chat_unavailable_title), style = TujiType.h3, color = TujiColor.Ink)
            Text(stringResource(chatFailureSentence(failure)), style = TujiType.body, color = TujiColor.Ink2, textAlign = TextAlign.Center)
            if (failure != TopicChatModel.Failure.SignedOut) {
                TujiButton(text = stringResource(R.string.chat_try_again), onClick = onRetry)
            }
        }
    }
}

// MARK: - 對話

@Composable
private fun Conversation(
    state: TopicChatModel.State,
    model: TopicChatModel,
    topics: List<TopicChoice>,
    language: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    onOpenStatement: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    val messages = state.chatState?.messages.orEmpty()
    val scrollKey = "${messages.lastOrNull()?.id}|${state.outgoing?.requestId}|${state.work}"
    LaunchedEffect(scrollKey) {
        // To the top of the newest turn: a turn with its options is taller than the screen.
        val index = state.latestTurn?.let { turn -> messages.indexOfFirst { it.id == turn.id } }?.takeIf { it >= 0 }
            ?: (messages.size)
        if (reduceMotion) list.scrollToItem(index) else list.animateScrollToItem(index)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = list,
        contentPadding = PaddingValues(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        items(messages, key = { it.id }) { message ->
            when (message.kind) {
                ChatMessage.Kind.Topic -> TopicDividerRow(message.topic?.name ?: message.topicId)
                ChatMessage.Kind.User -> UserTurnRow(message.user?.text.orEmpty())
                ChatMessage.Kind.Ai -> message.ai?.let { turn ->
                    val latest = state.isActive && message.id == state.latestTurn?.id && !state.isWriting
                    AITurnRow(
                        turn = turn,
                        language = language,
                        speech = speech,
                        accent = accent,
                        showTranslation = state.showTranslation,
                        showReading = state.showReading,
                        isLatest = latest,
                        exampleShown = latest && state.exampleShown,
                        fill = model::fill,
                    )
                }
            }
        }
        item(key = "tail") {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
                state.outgoing?.let { OutgoingRow(it, model::retryOutgoing, model::editOutgoing) }
                if (state.isWriting || state.work != null) {
                    WritingRow(state.work, topics)
                } else if (state.chatState?.needsOpening == true && state.isActive) {
                    Notice(stringResource(R.string.chat_opening_missing)) {
                        TextAction(stringResource(R.string.chat_retry_opening)) { scope.launch { model.retryOpening() } }
                    }
                }
                state.actionFailure?.let { failure ->
                    Notice(stringResource(chatFailureSentence(failure))) {
                        if (failure == TopicChatModel.Failure.InsufficientPoints) {
                            TextAction(stringResource(R.string.credit_statement_title), onOpenStatement)
                        }
                        TextAction(stringResource(R.string.chat_got_it), model::clearActionFailure)
                    }
                }
                state.chat?.takeIf { !it.isActive }?.let { chat ->
                    ChatReviewSection(
                        chat = chat,
                        failed = state.chatState?.reviewError != null,
                        showTranslation = state.showTranslation,
                        showReading = state.showReading,
                        retry = { scope.launch { model.end(review = true) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun WritingRow(work: TopicChatModel.Work?, topics: List<TopicChoice>) {
    val label = when (work) {
        TopicChatModel.Work.Starting -> stringResource(R.string.chat_preparing)
        is TopicChatModel.Work.Switching ->
            stringResource(R.string.chat_switching, topics.firstOrNull { it.id == work.topicId }?.name ?: work.topicId)
        TopicChatModel.Work.Opening -> stringResource(R.string.chat_opening)
        TopicChatModel.Work.Simplify -> stringResource(R.string.chat_simplifying)
        TopicChatModel.Work.Another -> stringResource(R.string.chat_another_question)
        TopicChatModel.Work.Ending -> stringResource(R.string.chat_ending)
        null -> stringResource(R.string.chat_replying)
    }
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MascotFigure(MascotPose.Think, 28.dp)
        TujiIndeterminateBar(Modifier.width(40.dp))
        Text(label, style = TujiType.bodySm, color = TujiColor.Ink3)
    }
}

@Composable
private fun Notice(text: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
    ) {
        Text(text, style = TujiType.bodySm, color = TujiColor.Ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S4)) { actions() }
    }
}

// MARK: - 輸入

@Composable
private fun Composer(
    state: TopicChatModel.State,
    model: TopicChatModel,
    supportsOpen: Boolean,
    onSupportsOpen: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column {
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        Column(
            Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            state.keptDraft?.let { kept ->
                Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chat_kept_draft, kept.topicName), style = TujiType.bodySm, color = TujiColor.Ink2, maxLines = 2, modifier = Modifier.weight(1f))
                    TextAction(stringResource(R.string.chat_kept_restore), model::restoreKeptDraft)
                    Text(
                        stringResource(R.string.chat_kept_discard),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                        modifier = Modifier.tujiClickable(onClick = model::discardKeptDraft).padding(vertical = TujiSpace.S3),
                    )
                }
            }
            if (supportsOpen) {
                SupportTray(state, onExample = model::toggleExample) { kind ->
                    onSupportsOpen(false)
                    scope.launch { model.assist(kind) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Bottom) {
                val supportsLabel = stringResource(R.string.chat_supports)
                Box(
                    Modifier
                        .size(52.dp)
                        .background(if (supportsOpen) TujiColor.BrandPrimary else TujiColor.Paper2)
                        .tujiClickable { onSupportsOpen(!supportsOpen) }
                        .semantics { contentDescription = supportsLabel; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    if (supportsOpen) TujiGlyph.Close(size = 17.dp) else TujiGlyph.Sparkles(size = 20.dp)
                }
                PaperField(
                    value = state.draft,
                    onValueChange = model::setDraft,
                    placeholder = stringResource(R.string.chat_placeholder),
                    style = TujiType.body,
                    singleLine = false,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                )
                SendButton(state, model) { onSupportsOpen(false) }
            }
            state.chatState?.offer?.let { offer ->
                Text(
                    if (offer.available < offer.maxPoints) {
                        stringResource(R.string.chat_price_short, offer.available, offer.maxPoints)
                    } else {
                        stringResource(R.string.chat_price_spent, state.chat?.spent ?: 0, offer.available)
                    },
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                )
            }
        }
    }
}

@Composable
private fun SendButton(state: TopicChatModel.State, model: TopicChatModel, onSend: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (state.outgoing?.status == TopicChatModel.Outgoing.Status.Sending) {
        val label = stringResource(R.string.chat_stop)
        Box(
            Modifier
                .size(52.dp)
                .background(TujiColor.Ink)
                .tujiClickable { scope.launch { model.stop() } }
                .semantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(14.dp).background(TujiColor.Paper)) }
    } else {
        val enabled = state.canSend && state.canAfford
        val label = stringResource(R.string.chat_send)
        Box(
            Modifier
                .size(52.dp)
                .background(if (enabled) TujiColor.Ink else TujiColor.Paper2)
                .tujiClickable(enabled = enabled) {
                    onSend()
                    model.send()
                }
                .semantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { TujiGlyph.ArrowUp(size = 17.dp, tint = if (enabled) TujiColor.Paper else TujiColor.Ink3) }
    }
}

/** 幫我接話 — 示範回答 opens what came with the turn; 簡單一點 and 換個問題 generate, free, a couple of times. */
@Composable
private fun SupportTray(state: TopicChatModel.State, onExample: () -> Unit, generate: (TopicChatAssistKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        SupportChip(stringResource(R.string.chat_example), selected = state.exampleShown, enabled = state.latestTurn?.ai?.example != null, onClick = onExample)
        SupportChip(
            stringResource(R.string.chat_simpler),
            selected = false,
            enabled = state.canAssist && state.latestTurn?.ai?.simpler == null,
        ) { generate(TopicChatAssistKind.Simplify) }
        SupportChip(stringResource(R.string.chat_another), selected = false, enabled = state.canAssist) { generate(TopicChatAssistKind.Another) }
    }
}

@Composable
private fun SupportChip(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (selected) TujiColor.Ink else TujiColor.Paper2)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TujiSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = TujiType.bodySmStrong,
            color = when {
                selected -> TujiColor.Paper
                enabled -> TujiColor.Ink
                else -> TujiColor.Ink3
            },
        )
    }
}

internal fun chatFailureSentence(failure: TopicChatModel.Failure): Int = when (failure) {
    TopicChatModel.Failure.InsufficientPoints -> R.string.chat_failure_points
    TopicChatModel.Failure.RateLimited -> R.string.chat_failure_rate
    TopicChatModel.Failure.Busy -> R.string.chat_failure_busy
    TopicChatModel.Failure.Offline -> R.string.chat_failure_offline
    TopicChatModel.Failure.Membership -> R.string.chat_failure_membership
    TopicChatModel.Failure.SignedOut -> R.string.chat_failure_signed_out
    TopicChatModel.Failure.Unavailable -> R.string.chat_failure_unavailable
    TopicChatModel.Failure.TopicUnavailable -> R.string.chat_failure_topic
    TopicChatModel.Failure.Outdated -> R.string.chat_failure_outdated
    TopicChatModel.Failure.AssistLimit -> R.string.chat_failure_assist_limit
    TopicChatModel.Failure.Ended -> R.string.chat_failure_ended
    TopicChatModel.Failure.Failed -> R.string.chat_failure_failed
}

// MARK: - 對話的各列

/** A sentence in the language being learned, with its reading and translation as the person chose. */
@Composable
internal fun ChatSentence(text: ChatTargetText, showTranslation: Boolean, showReading: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val furigana = text.furigana
        if (showReading && !furigana.isNullOrEmpty()) {
            FuriganaSentence(furigana)
        } else {
            Text(text.text, style = TujiType.bodyStrong, color = TujiColor.Ink)
            if (showReading) text.reading?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink3) }
        }
        if (showTranslation) text.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
    }
}

@Composable
private fun TopicDividerRow(name: String) {
    Row(
        Modifier.padding(vertical = TujiSpace.S1).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(TujiBorder.Bw1).background(TujiColor.Rule))
        Text(stringResource(R.string.chat_topic_divider, name), style = TujiType.label, color = TujiColor.Ink3)
        Box(Modifier.weight(1f).height(TujiBorder.Bw1).background(TujiColor.Rule))
    }
}

@Composable
private fun UserTurnRow(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = TujiSpace.S5), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = TujiType.body,
            color = TujiColor.Ink,
            modifier = Modifier.background(TujiColor.BrandPrimary.copy(alpha = 0.35f)).padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
        )
    }
}

/** The answer on its way, or the one that did not get there. */
@Composable
private fun OutgoingRow(outgoing: TopicChatModel.Outgoing, retry: () -> Unit, edit: () -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        UserTurnRow(outgoing.text, Modifier.alpha(if (outgoing.status == TopicChatModel.Outgoing.Status.Sending) 0.6f else 1f))
        val note = when (val status = outgoing.status) {
            TopicChatModel.Outgoing.Status.Sending -> null
            TopicChatModel.Outgoing.Status.Stopped -> stringResource(R.string.chat_stopped)
            is TopicChatModel.Outgoing.Status.Failed -> stringResource(chatFailureSentence(status.failure))
        }
        note?.let {
            Text(it, style = TujiType.label, color = TujiColor.Ink3, textAlign = TextAlign.End)
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
                TextAction(stringResource(R.string.vocabulary_edit), edit)
                TextAction(stringResource(R.string.chat_resend), retry)
            }
        }
    }
}

@Composable
private fun AITurnRow(
    turn: ChatAITurn,
    language: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    showTranslation: Boolean,
    showReading: Boolean,
    isLatest: Boolean,
    exampleShown: Boolean,
    fill: (String, ChatSupport) -> Unit,
) {
    val indent = Modifier.padding(start = 32.dp + TujiSpace.S2)
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        turn.scene?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
        Row(Modifier.padding(end = TujiSpace.S5), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
            MascotFigure(MascotPose.Face, 32.dp, Modifier.semantics { contentDescription = "Moira" })
            Row(
                Modifier.background(TujiColor.Paper2).padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.Top,
            ) {
                ChatSentence(turn.line, showTranslation, showReading, Modifier.weight(1f, fill = false))
                SentenceSpeakerButton(turn.line.text, language, speech, accent)
            }
        }
        turn.simpler?.let { simpler ->
            Row(indent.fillMaxWidth()) {
                Box(Modifier.width(TujiBorder.Bw2).heightIn(min = 40.dp).background(TujiColor.BrandSecondary))
                Column(Modifier.padding(TujiSpace.S3), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Text(stringResource(R.string.chat_simpler), style = TujiType.label, color = TujiColor.BrandSecondary)
                    Text(simpler.note, style = TujiType.bodySm, color = TujiColor.Ink2)
                    simpler.line?.let { ChatSentence(it, showTranslation, showReading) }
                    if (isLatest && simpler.options.isNotEmpty()) {
                        OptionList(simpler.options, showTranslation) { fill(it, ChatSupport.Simplify) }
                    }
                }
            }
        }
        if (isLatest) {
            val pattern = turn.pattern
            if (pattern != null) {
                FollowAlongCard(pattern, turn.options, showTranslation, indent) { fill(it, ChatSupport.Option) }
            } else if (turn.options.isNotEmpty()) {
                Box(indent) { OptionList(turn.options, showTranslation) { fill(it, ChatSupport.Option) } }
            }
            val example = turn.example
            if (exampleShown && example != null) {
                Column(
                    indent.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                ) {
                    Text(stringResource(R.string.chat_example), style = TujiType.label, color = TujiColor.Ink3)
                    ChatSentence(example, showTranslation, showReading)
                    TextAction(stringResource(R.string.chat_example_fill)) { fill(example.text, ChatSupport.Example) }
                }
            }
        }
    }
}

/** 「跟著說」: the structure with its blank marked by more than colour — an underlined, bracketed gap read as 「空格」. */
@Composable
private fun FollowAlongCard(
    pattern: ChatPattern,
    options: List<ChatOption>,
    showTranslation: Boolean,
    modifier: Modifier,
    fill: (String) -> Unit,
) {
    val blank = Regex("＿＿|___")
    val spokenBlank = stringResource(R.string.chat_blank)
    val template = buildAnnotatedString {
        val match = blank.find(pattern.template)
        if (match == null) {
            append(pattern.template)
        } else {
            append(pattern.template.substring(0, match.range.first))
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, background = TujiColor.BrandPrimary.copy(alpha = 0.5f))) {
                append(" ［  ］ ")
            }
            append(pattern.template.substring(match.range.last + 1))
        }
    }
    Column(
        modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Text(stringResource(R.string.chat_follow_along), style = TujiType.label, color = TujiColor.BrandSecondary)
        Text(
            template,
            style = TujiType.bodyStrong,
            color = TujiColor.Ink,
            modifier = Modifier.semantics { contentDescription = pattern.template.replace(blank, spokenBlank) },
        )
        if (showTranslation) pattern.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
        if (options.isNotEmpty()) OptionList(options, showTranslation, fill)
    }
}

/** Up to three answers. A tap fills the draft with the whole sentence; nothing is sent. */
@Composable
private fun OptionList(options: List<ChatOption>, showTranslation: Boolean, fill: (String) -> Unit) {
    val hint = stringResource(R.string.chat_option_hint)
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        options.forEach { option ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(TujiColor.Paper)
                    .semantics { onClick(label = hint) { fill(option.answer.text); true } }
                    .tujiClickable { fill(option.answer.text) }
                    .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(option.answer.text, style = TujiType.bodySmStrong, color = TujiColor.Ink)
                if (showTranslation) option.answer.translation?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
            }
        }
    }
}
