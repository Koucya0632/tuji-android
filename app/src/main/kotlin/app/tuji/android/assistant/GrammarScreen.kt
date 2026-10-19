package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavIcon
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberReduceMotion
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.GrammarComparison
import app.tuji.android.core.model.GrammarContent
import app.tuji.android.core.model.GrammarDifficulty
import app.tuji.android.core.model.GrammarExplanation
import app.tuji.android.core.model.GrammarInput
import app.tuji.android.core.model.GrammarMode
import app.tuji.android.core.model.GrammarPractice
import app.tuji.android.core.model.GrammarPracticeType
import app.tuji.android.core.model.GrammarRecord
import app.tuji.android.core.model.GrammarReply
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.model.WordSpeaking
import kotlinx.coroutines.launch

/**
 * AI 語法 — a conversation with Moira about grammar. iOS's `GrammarView`: what
 * was asked is the block on the right; her reply is the whole card — an
 * explanation, a comparison, or a practice set worked through in place. Only
 * 送出 (or 換一組題目／重新生成 under the newest card) spends points.
 */
@Composable
fun GrammarScreen(
    model: GrammarModel,
    speech: WordSpeaking?,
    accent: String,
    offerRevision: Int,
    uiLanguage: String,
    onBack: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenMembership: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showingHistory by rememberSaveable { mutableStateOf(false) }
    var showingContext by rememberSaveable { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(model, offerRevision) { model.load() }
    LaunchedEffect(state.draftTick) {
        if (state.draftTick == 0) return@LaunchedEffect
        if (state.context.isNotEmpty()) showingContext = true
        runCatching { field.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(TujiColor.Paper).imePadding()) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            title = stringResource(R.string.assistant_feature_grammar),
            showsRule = true,
            trailing = {
                Row {
                    TujiNavIcon(stringResource(R.string.grammar_history), { showingHistory = true }, Modifier.testTag("grammar-history")) {
                        TujiGlyph.History()
                    }
                    TujiNavIcon(stringResource(R.string.credit_statement_title), onOpenStatement) { TujiGlyph.CreditCard() }
                }
            },
        )
        Conversation(state, model, speech, accent, Modifier.weight(1f), scope)
        Composer(
            state = state,
            model = model,
            field = field,
            showingContext = showingContext,
            onToggleContext = { showingContext = !showingContext },
            onSend = {
                focus.clearFocus()
                scope.launch { model.generate() }
            },
            onRefresh = { scope.launch { model.load() } },
            onOpenMembership = onOpenMembership,
        )
    }

    if (showingHistory) {
        GrammarHistorySheet(
            model = model,
            uiLanguage = uiLanguage,
            onOpen = { record ->
                model.open(record)
                showingHistory = false
            },
            onDismiss = { showingHistory = false },
        )
    }
}

@Composable
private fun Conversation(
    state: GrammarModel.State,
    model: GrammarModel,
    speech: WordSpeaking?,
    accent: String,
    modifier: Modifier,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val list = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(state.spotlight) {
        val spotlight = state.spotlight ?: return@LaunchedEffect
        val index = state.turns.indexOfFirst { it.id == spotlight.id }
        if (index < 0) return@LaunchedEffect
        if (reduceMotion) list.scrollToItem(index + 1) else list.animateScrollToItem(index + 1)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = list,
        contentPadding = PaddingValues(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S5),
    ) {
        item {
            MoiraReplyRow { Text(stringResource(R.string.grammar_welcome), style = TujiType.body, color = TujiColor.Ink) }
        }
        items(state.turns, key = { it.id }) { turn ->
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                GrammarAskRow(turn.input)
                MoiraReplyRow { Reply(turn, state, model, speech, accent, scope) }
            }
        }
    }
}

@Composable
private fun ColumnScope.Reply(
    turn: GrammarTurn,
    state: GrammarModel.State,
    model: GrammarModel,
    speech: WordSpeaking?,
    accent: String,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val newest = turn.id == state.turns.lastOrNull()?.id
    val note = turn.failureNote
    val record = turn.record
    when {
        note != null -> {
            Text(noteText(note), style = TujiType.body, color = TujiColor.Ink)
            TextAction(stringResource(R.string.vocabulary_edit)) { model.edit(turn) }
        }
        record != null -> when (record.status) {
            VocabularyRecord.Status.Completed -> record.result?.content?.let { content ->
                Card(record, content, turn, state, model, speech, accent, scope)
            }
            VocabularyRecord.Status.Clarification -> record.result?.let { Clarification(it, turn, newest, model) }
            else -> Unit
        }
        state.pending?.requestId == turn.id -> if (state.busy) {
            Row(
                Modifier.heightIn(min = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TujiIndeterminateBar(Modifier.width(40.dp))
                Text(
                    stringResource(
                        if (turn.input.mode == GrammarMode.Practice) R.string.grammar_generating_practice
                        else R.string.vocabulary_generating,
                    ),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                )
            }
        } else {
            Text(
                stringResource(R.string.vocabulary_reconcile),
                style = TujiType.bodyStrong,
                color = TujiColor.Ink,
                modifier = Modifier
                    .background(TujiColor.Current)
                    .tujiClickable { scope.launch { model.reconcile() } }
                    .padding(TujiSpace.S3),
            )
        }
    }
}

@Composable
private fun ColumnScope.Card(
    record: GrammarRecord,
    content: GrammarContent,
    turn: GrammarTurn,
    state: GrammarModel.State,
    model: GrammarModel,
    speech: WordSpeaking?,
    accent: String,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val target = if (turn.input.learningDirection == "zh-ja") TargetLanguage.JA else TargetLanguage.EN
    val lines = GrammarLines(target, speech, accent)
    when (content) {
        is GrammarExplanation -> {
            GrammarExplanationCard(content, lines)
            NextSteps {
                TextAction(stringResource(R.string.grammar_next_compare)) { model.next(GrammarMode.Compare, record) }
                TextAction(stringResource(R.string.grammar_next_practice)) { model.next(GrammarMode.Practice, record) }
            }
        }
        is GrammarComparison -> {
            GrammarComparisonCard(content, lines)
            NextSteps {
                TextAction(stringResource(R.string.grammar_explain_point, content.a.name)) {
                    model.follow(GrammarModel.ComparisonStep.ExplainA, record)
                }
                TextAction(stringResource(R.string.grammar_explain_point, content.b.name)) {
                    model.follow(GrammarModel.ComparisonStep.ExplainB, record)
                }
                TextAction(stringResource(R.string.grammar_practice_distinguish)) {
                    model.follow(GrammarModel.ComparisonStep.Practice, record)
                }
            }
        }
        is GrammarPractice -> model.owner?.let { owner ->
            val session = remember(record.id) {
                GrammarPracticeSession(content, record.id, owner, turn.input.learningDirection, model.progress)
            }
            GrammarPracticeCard(session)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1), verticalAlignment = Alignment.CenterVertically) {
            TujiGlyph.Check(size = 12.dp, tint = TujiColor.Ink2)
            Text(stringResource(R.string.grammar_saved), style = TujiType.label, color = TujiColor.Ink2)
        }
        val label = stringResource(if (record.favorite) R.string.vocabulary_unbookmark else R.string.vocabulary_bookmark)
        Box(
            Modifier
                .size(44.dp)
                .tujiClickable { scope.launch { model.toggleFavorite(record) } }
                .semantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { TujiGlyph.Bookmark(filled = record.favorite) }
    }
    when (val regeneration = model.regeneration(turn, state)) {
        is GrammarModel.Regeneration.Main -> Text(
            stringResource(R.string.grammar_regenerate_practice, regeneration.maxPoints),
            style = TujiType.bodyStrong,
            color = TujiColor.Ink,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .background(TujiColor.Current)
                .tujiClickable { scope.launch { model.regenerate(turn) } }
                .padding(TujiSpace.S3)
                .testTag("grammar-regenerate"),
        )
        is GrammarModel.Regeneration.Secondary -> Text(
            stringResource(R.string.vocabulary_regenerate, regeneration.maxPoints),
            style = TujiType.bodySm.copy(textDecoration = TextDecoration.Underline),
            color = TujiColor.Ink2,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .tujiClickable { scope.launch { model.regenerate(turn) } }
                .padding(vertical = TujiSpace.S3)
                .testTag("grammar-regenerate"),
        )
        null -> Unit
    }
}

/** A next step only fills the draft; the price is said beside 送出. */
@Composable
private fun NextSteps(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) { content() }
}

@Composable
private fun Clarification(reply: GrammarReply, turn: GrammarTurn, newest: Boolean, model: GrammarModel) {
    Text(reply.message, style = TujiType.body, color = TujiColor.Ink)
    reply.candidates.forEach { candidate ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper2)
                // An older question's choices stay readable, but answering it
                // now would put a stale sentence into the draft.
                .tujiClickable(enabled = newest) { model.choose(candidate, turn) }
                .padding(TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(candidate.focus, style = TujiType.bodyStrong, color = if (newest) TujiColor.Ink else TujiColor.Ink3)
            Text(candidate.description, style = TujiType.bodySm, color = if (newest) TujiColor.Ink else TujiColor.Ink3)
        }
    }
    Text(stringResource(R.string.grammar_choose_focus), style = TujiType.label, color = TujiColor.Ink2)
}

// MARK: - 輸入

@Composable
private fun Composer(
    state: GrammarModel.State,
    model: GrammarModel,
    field: FocusRequester,
    showingContext: Boolean,
    onToggleContext: () -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMembership: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        Column(
            Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            state.message?.let {
                Text(noteText(it), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = Modifier.testTag("grammar-message"))
            }
            Chips(
                GrammarMode.entries,
                selected = state.mode,
                title = { stringResource(grammarModeTitle(it)) },
                description = { stringResource(grammarModeSummary(it)) },
                tag = { "grammar-mode-${it.name.lowercase()}" },
                onSelect = model::setMode,
            )
            if (state.focus.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(stringResource(R.string.grammar_focus, state.focus), style = TujiType.bodySm, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.grammar_clear_focus),
                        style = TujiType.label,
                        color = TujiColor.Ink,
                        modifier = Modifier.heightIn(min = 44.dp).tujiClickable(onClick = model::clearFocus),
                    )
                }
            }
            if (state.mode == GrammarMode.Compare) {
                PaperField(
                    value = state.compareWith,
                    onValueChange = model::setCompareWith,
                    placeholder = stringResource(R.string.grammar_compare_with),
                    style = TujiType.bodySm,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().testTag("grammar-compare-with"),
                )
            }
            if (state.mode == GrammarMode.Practice) PracticeOptions(state, model)
            if (showingContext || state.context.isNotEmpty()) {
                PaperField(
                    value = state.context,
                    onValueChange = model::setContext,
                    placeholder = stringResource(R.string.grammar_context),
                    style = TujiType.bodySm,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SendArea(state, model, field, showingContext, onToggleContext, onSend, onRefresh, onOpenMembership)
            if (state.tooLong) Text(stringResource(R.string.grammar_too_long), style = TujiType.label, color = TujiColor.Ink)
        }
    }
}

@Composable
private fun <T> Chips(
    values: List<T>,
    selected: T,
    title: @Composable (T) -> String,
    description: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        values.forEach { value ->
            val summary = description(value)
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .background(if (value == selected) TujiColor.Current else TujiColor.Paper2)
                    .tujiClickable { onSelect(value) }
                    .semantics {
                        this.selected = value == selected
                        role = Role.Tab
                        contentDescription = summary
                    }
                    .padding(horizontal = TujiSpace.S3)
                    .testTag(tag(value)),
                contentAlignment = Alignment.Center,
            ) {
                Text(title(value), style = TujiType.bodySmStrong, color = TujiColor.Ink)
            }
        }
    }
}

@Composable
private fun PracticeOptions(state: GrammarModel.State, model: GrammarModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).testTag("grammar-practice-options"),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GrammarDifficulty.entries.forEach { level ->
            OutlineChip(stringResource(difficultyTitle(level)), state.difficulty == level) { model.setDifficulty(level) }
        }
        Box(Modifier.width(TujiBorder.Bw1).height(24.dp).background(TujiColor.Rule))
        GrammarPracticeType.entries.forEach { type ->
            OutlineChip(stringResource(practiceTypeTitle(type)), state.practiceType == type) { model.setPracticeType(type) }
        }
    }
}

@Composable
private fun OutlineChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) TujiColor.Ink else TujiColor.Ink3)
            .tujiClickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TujiSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = TujiType.label, color = TujiColor.Ink)
    }
}

@Composable
private fun SendArea(
    state: GrammarModel.State,
    model: GrammarModel,
    field: FocusRequester,
    showingContext: Boolean,
    onToggleContext: () -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMembership: () -> Unit,
) {
    when (state.blocker) {
        VocabularyModel.Blocker.Membership -> Column(
            Modifier.testTag("grammar-membership"),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(stringResource(R.string.grammar_membership_title), style = TujiType.bodyStrong, color = TujiColor.Ink)
            Text(stringResource(R.string.grammar_membership_detail), style = TujiType.bodySm, color = TujiColor.Ink2)
            TujiButton(
                text = stringResource(R.string.vocabulary_membership_action),
                onClick = onOpenMembership,
                modifier = Modifier.width(IntrinsicSize.Max),
            )
        }
        VocabularyModel.Blocker.Disabled ->
            Text(stringResource(R.string.vocabulary_disabled), style = TujiType.label, color = TujiColor.Ink2)
        VocabularyModel.Blocker.InsufficientPoints, null -> {
            QueryField(state, model, field, showingContext, onToggleContext, onSend)
            if (state.blocker == VocabularyModel.Blocker.InsufficientPoints) {
                Row(
                    Modifier.testTag("grammar-insufficient"),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.vocabulary_insufficient), style = TujiType.label, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                    TujiButton(
                        text = stringResource(R.string.vocabulary_top_up),
                        onClick = onOpenMembership,
                        modifier = Modifier.width(IntrinsicSize.Max),
                    )
                }
            } else {
                state.price?.let { price ->
                    Text(
                        stringResource(R.string.vocabulary_price, price.minPoints, price.maxPoints, price.maxPoints),
                        style = TujiType.label,
                        color = TujiColor.Ink3,
                    )
                }
            }
            // Not in the chain above: a kept offer still shows its prices offline.
            if (!state.offerCurrent && state.message != null) {
                Text(
                    stringResource(R.string.vocabulary_refresh_price),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink,
                    modifier = Modifier.tujiClickable(onClick = onRefresh).padding(vertical = TujiSpace.S1),
                )
            }
        }
    }
}

@Composable
private fun QueryField(
    state: GrammarModel.State,
    model: GrammarModel,
    field: FocusRequester,
    showingContext: Boolean,
    onToggleContext: () -> Unit,
    onSend: () -> Unit,
) {
    val canGenerate = model.canGenerate(state)
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Bottom) {
        PaperField(
            value = state.query,
            onValueChange = model::setQuery,
            placeholder = stringResource(grammarPlaceholder(state.mode)),
            style = TujiType.body,
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).focusRequester(field).testTag("grammar-query"),
        )
        val contextLabel = stringResource(R.string.grammar_context)
        Box(
            Modifier
                .size(width = 44.dp, height = 52.dp)
                .tujiClickable(onClick = onToggleContext)
                .semantics { contentDescription = contextLabel; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Quote(size = 17.dp, tint = if (showingContext || state.context.isNotEmpty()) TujiColor.Ink else TujiColor.Ink3)
        }
        val sendLabel = stringResource(R.string.vocabulary_send, state.price?.maxPoints ?: 0)
        Box(
            Modifier
                .size(52.dp)
                .background(if (canGenerate) TujiColor.Ink else TujiColor.Paper2)
                .tujiClickable(enabled = canGenerate, onClick = onSend)
                .semantics { contentDescription = sendLabel; role = Role.Button }
                .testTag("grammar-generate"),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.ArrowUp(size = 17.dp, tint = if (canGenerate) TujiColor.Paper else TujiColor.Ink3)
        }
    }
}

/** What was asked: the grammar or sentence, the template, and what narrowed it. */
@Composable
private fun GrammarAskRow(input: GrammarInput) {
    val details = buildList {
        add(stringResource(grammarModeTitle(input.mode)))
        if (input.compareWith.isNotEmpty()) add(input.compareWith)
        if (input.focus.isNotEmpty()) add(input.focus)
        if (input.mode == GrammarMode.Practice) {
            add(stringResource(difficultyTitle(input.difficulty)))
            add(stringResource(practiceTypeTitle(input.practiceType)))
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = TujiSpace.S5), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .background(TujiColor.Paper2)
                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            Text(input.query, style = TujiType.bodyStrong, color = TujiColor.Ink, textAlign = TextAlign.End, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(details.joinToString(" · "), style = TujiType.label, color = TujiColor.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (input.context.isNotEmpty()) {
                Text(input.context, style = TujiType.label, color = TujiColor.Ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun grammarModeTitle(mode: GrammarMode): Int = when (mode) {
    GrammarMode.Explain -> R.string.grammar_mode_explain
    GrammarMode.Compare -> R.string.grammar_mode_compare
    GrammarMode.Practice -> R.string.grammar_mode_practice
}

private fun grammarModeSummary(mode: GrammarMode): Int = when (mode) {
    GrammarMode.Explain -> R.string.grammar_mode_explain_summary
    GrammarMode.Compare -> R.string.grammar_mode_compare_summary
    GrammarMode.Practice -> R.string.grammar_mode_practice_summary
}

private fun grammarPlaceholder(mode: GrammarMode): Int = when (mode) {
    GrammarMode.Explain -> R.string.grammar_placeholder_explain
    GrammarMode.Compare -> R.string.grammar_placeholder_compare
    GrammarMode.Practice -> R.string.grammar_placeholder_practice
}

internal fun difficultyTitle(level: GrammarDifficulty): Int =
    if (level == GrammarDifficulty.Basic) R.string.grammar_basic else R.string.grammar_advanced

private fun practiceTypeTitle(type: GrammarPracticeType): Int = when (type) {
    GrammarPracticeType.Mixed -> R.string.grammar_type_mixed
    GrammarPracticeType.Choice -> R.string.grammar_type_choice
    GrammarPracticeType.Context -> R.string.grammar_type_context
}
