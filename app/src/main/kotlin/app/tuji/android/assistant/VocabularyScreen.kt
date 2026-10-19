package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
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
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.VocabularyCandidate
import app.tuji.android.core.model.VocabularyContent
import app.tuji.android.core.model.VocabularyInput
import app.tuji.android.core.model.VocabularyMode
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.model.VocabularyReply
import app.tuji.android.core.model.WordSpeaking
import kotlinx.coroutines.launch

/**
 * AI 查詞 — a conversation with Moira. iOS's `VocabularyView`: what was asked
 * is the block on the right; her reply is the whole card. The composer at the
 * bottom holds the draft and the three modes. Only 送出 (or 換個助記／重新生成
 * under the newest card) spends points; a candidate or a next step only fills
 * the draft.
 */
@Composable
fun VocabularyScreen(
    model: VocabularyModel,
    speech: WordSpeaking?,
    accent: String,
    /** Changes when the kept balance stops being trusted; the page loads again on it. */
    offerRevision: Int,
    uiLanguage: String,
    onBack: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenMembership: () -> Unit,
    onOpenWord: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showingHistory by rememberSaveable { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(model, offerRevision) { model.load() }

    fun focusField() = runCatching { field.requestFocus() }

    Column(Modifier.fillMaxSize().background(TujiColor.Paper).imePadding()) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            title = stringResource(R.string.assistant_feature_lookup),
            showsRule = true,
            trailing = {
                Row {
                    TujiNavIcon(stringResource(R.string.vocabulary_history), { showingHistory = true }) {
                        TujiGlyph.History()
                    }
                    TujiNavIcon(stringResource(R.string.credit_statement_title), onOpenStatement) {
                        TujiGlyph.CreditCard()
                    }
                }
            },
        )
        Conversation(
            state = state,
            model = model,
            speech = speech,
            accent = accent,
            modifier = Modifier.weight(1f),
            onEdit = { turn ->
                model.edit(turn)
                focusField()
            },
            onChoose = { candidate, mode ->
                model.choose(candidate, mode)
                focusField()
            },
            onFavorite = { scope.launch { model.toggleFavorite(it) } },
            onRegenerate = { scope.launch { model.regenerate(it) } },
            onReconcile = { scope.launch { model.reconcile() } },
        )
        Composer(
            state = state,
            model = model,
            field = field,
            onSend = {
                focus.clearFocus()
                scope.launch { model.generate() }
            },
            onRefresh = { scope.launch { model.load() } },
            onOpenMembership = onOpenMembership,
            onOpenWord = onOpenWord,
            onSearch = onSearch,
        )
    }

    if (showingHistory) {
        VocabularyHistorySheet(
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

// MARK: - 對話

@Composable
private fun Conversation(
    state: VocabularyModel.State,
    model: VocabularyModel,
    speech: WordSpeaking?,
    accent: String,
    modifier: Modifier,
    onEdit: (VocabularyTurn) -> Unit,
    onChoose: (VocabularyCandidate, VocabularyMode) -> Unit,
    onFavorite: (VocabularyRecord) -> Unit,
    onRegenerate: (VocabularyTurn) -> Unit,
    onReconcile: () -> Unit,
) {
    val list = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    // To the top of the turn, not the bottom of the list: a card is long, and
    // landing on its last section hides the word it is about.
    LaunchedEffect(state.spotlight) {
        val spotlight = state.spotlight ?: return@LaunchedEffect
        val index = state.turns.indexOfFirst { it.id == spotlight.id }
        if (index < 0) return@LaunchedEffect
        if (reduceMotion) list.scrollToItem(index + 1) else list.animateScrollToItem(index + 1)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S5),
    ) {
        item {
            MoiraReplyRow {
                Text(stringResource(R.string.vocabulary_welcome), style = TujiType.body, color = TujiColor.Ink)
            }
        }
        items(state.turns, key = { it.id }) { turn ->
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                VocabularyAskRow(turn.input)
                MoiraReplyRow {
                    Reply(turn, state, model, speech, accent, onEdit, onChoose, onFavorite, onRegenerate, onReconcile)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Reply(
    turn: VocabularyTurn,
    state: VocabularyModel.State,
    model: VocabularyModel,
    speech: WordSpeaking?,
    accent: String,
    onEdit: (VocabularyTurn) -> Unit,
    onChoose: (VocabularyCandidate, VocabularyMode) -> Unit,
    onFavorite: (VocabularyRecord) -> Unit,
    onRegenerate: (VocabularyTurn) -> Unit,
    onReconcile: () -> Unit,
) {
    val newest = turn.id == state.turns.lastOrNull()?.id
    val note = turn.failureNote
    val record = turn.record
    when {
        note != null -> {
            Text(noteText(note), style = TujiType.body, color = TujiColor.Ink)
            TextAction(stringResource(R.string.vocabulary_edit)) { onEdit(turn) }
        }
        record != null -> when (record.status) {
            VocabularyRecord.Status.Completed -> record.result?.content?.let { content ->
                Card(record, content, turn, state, model, speech, accent, onChoose, onFavorite, onRegenerate)
            }
            VocabularyRecord.Status.Clarification -> record.result?.let { reply ->
                Clarification(reply, turn.input.mode, newest, onChoose)
            }
            else -> Unit
        }
        state.pending?.requestId == turn.id -> if (state.busy) {
            Row(
                Modifier.heightIn(min = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TujiIndeterminateBar(Modifier.width(40.dp))
                Text(stringResource(R.string.vocabulary_generating), style = TujiType.bodySm, color = TujiColor.Ink2)
            }
        } else {
            Text(
                stringResource(R.string.vocabulary_reconcile),
                style = TujiType.bodyStrong,
                color = TujiColor.Ink,
                modifier = Modifier
                    .background(TujiColor.Current)
                    .tujiClickable(onClick = onReconcile)
                    .padding(TujiSpace.S3),
            )
        }
    }
}

@Composable
private fun ColumnScope.Card(
    record: VocabularyRecord,
    content: VocabularyContent,
    turn: VocabularyTurn,
    state: VocabularyModel.State,
    model: VocabularyModel,
    speech: WordSpeaking?,
    accent: String,
    onChoose: (VocabularyCandidate, VocabularyMode) -> Unit,
    onFavorite: (VocabularyRecord) -> Unit,
    onRegenerate: (VocabularyTurn) -> Unit,
) {
    val target = if (turn.input.learningDirection == "zh-ja") TargetLanguage.JA else TargetLanguage.EN
    VocabularyResultCard(content, target, speech, accent, onChoose)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TujiGlyph.Check(size = 12.dp, tint = TujiColor.Ink2)
            Text(stringResource(R.string.vocabulary_saved), style = TujiType.label, color = TujiColor.Ink2)
        }
        val label = stringResource(if (record.favorite) R.string.vocabulary_unbookmark else R.string.vocabulary_bookmark)
        Box(
            Modifier
                .size(44.dp)
                .tujiClickable { onFavorite(record) }
                .semantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Bookmark(filled = record.favorite)
        }
    }
    when (val regeneration = model.regeneration(turn, state)) {
        is VocabularyModel.Regeneration.Main -> Text(
            stringResource(R.string.vocabulary_regenerate_mnemonic, regeneration.maxPoints),
            style = TujiType.bodyStrong,
            color = TujiColor.Ink,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .background(TujiColor.Current)
                .tujiClickable { onRegenerate(turn) }
                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S3)
                .testTag("vocabulary-regenerate"),
        )
        is VocabularyModel.Regeneration.Secondary -> Text(
            stringResource(R.string.vocabulary_regenerate, regeneration.maxPoints),
            style = TujiType.bodySm.copy(textDecoration = TextDecoration.Underline),
            color = TujiColor.Ink2,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .tujiClickable { onRegenerate(turn) }
                .padding(vertical = TujiSpace.S3)
                .testTag("vocabulary-regenerate"),
        )
        null -> Unit
    }
}

@Composable
private fun Clarification(
    reply: VocabularyReply,
    mode: VocabularyMode,
    newest: Boolean,
    onChoose: (VocabularyCandidate, VocabularyMode) -> Unit,
) {
    Text(reply.message, style = TujiType.body, color = TujiColor.Ink)
    reply.candidates.forEach { candidate ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper2)
                // An older question's choices stay readable, but answering it
                // now would put a stale word into the draft.
                .tujiClickable(enabled = newest) { onChoose(candidate, mode) }
                .padding(TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(candidate.term, style = TujiType.bodyStrong, color = if (newest) TujiColor.Ink else TujiColor.Ink3)
            Text(candidate.meaning, style = TujiType.bodySm, color = if (newest) TujiColor.Ink else TujiColor.Ink3)
        }
    }
    Text(stringResource(R.string.vocabulary_choose_sense), style = TujiType.label, color = TujiColor.Ink2)
}

// MARK: - 輸入

@Composable
private fun Composer(
    state: VocabularyModel.State,
    model: VocabularyModel,
    field: FocusRequester,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMembership: () -> Unit,
    onOpenWord: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    var showingContext by rememberSaveable { mutableStateOf(false) }
    Column {
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        Column(
            Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            state.message?.let {
                Text(noteText(it), style = TujiType.bodySm, color = TujiColor.Ink2, modifier = Modifier.testTag("vocabulary-message"))
            }
            CatalogHint(model, state.query, onOpenWord, onSearch)
            Modes(state.mode, model::setMode)
            if (state.selectedSense.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(state.selectedSense, style = TujiType.bodySm, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.vocabulary_clear_sense),
                        style = TujiType.label,
                        color = TujiColor.Ink,
                        modifier = Modifier.tujiClickable(onClick = model::clearSense),
                    )
                }
            }
            if (showingContext || state.context.isNotEmpty()) {
                PaperField(
                    value = state.context,
                    onValueChange = model::setContext,
                    placeholder = stringResource(R.string.vocabulary_context_placeholder),
                    style = TujiType.bodySm,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SendArea(state, model, field, showingContext, { showingContext = !showingContext }, onSend, onRefresh, onOpenMembership)
            if (state.query.length > 120 || state.context.length > 800) {
                Text(stringResource(R.string.vocabulary_too_long), style = TujiType.label, color = TujiColor.Ink)
            }
        }
    }
}

@Composable
private fun SendArea(
    state: VocabularyModel.State,
    model: VocabularyModel,
    field: FocusRequester,
    showingContext: Boolean,
    onToggleContext: () -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMembership: () -> Unit,
) {
    when (state.blocker) {
        VocabularyModel.Blocker.Membership -> Column(
            Modifier.testTag("vocabulary-membership"),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(stringResource(R.string.vocabulary_membership_title), style = TujiType.bodyStrong, color = TujiColor.Ink)
            Text(stringResource(R.string.vocabulary_membership_detail), style = TujiType.bodySm, color = TujiColor.Ink2)
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
                    Modifier.testTag("vocabulary-insufficient"),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.vocabulary_insufficient),
                        style = TujiType.label,
                        color = TujiColor.Ink,
                        modifier = Modifier.weight(1f),
                    )
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
    state: VocabularyModel.State,
    model: VocabularyModel,
    field: FocusRequester,
    showingContext: Boolean,
    onToggleContext: () -> Unit,
    onSend: () -> Unit,
) {
    val canGenerate = model.canGenerate(state)
    val focus = LocalFocusManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
        PaperField(
            value = state.query,
            onValueChange = model::setQuery,
            placeholder = stringResource(R.string.vocabulary_query_placeholder),
            style = TujiType.body,
            singleLine = true,
            // Return only closes the keyboard: spending is the arrow, beside its price.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).height(52.dp).focusRequester(field).testTag("vocabulary-query"),
        )
        val contextLabel = stringResource(R.string.vocabulary_context_toggle)
        Box(
            Modifier
                .size(width = 44.dp, height = 52.dp)
                .tujiClickable(onClick = onToggleContext)
                .semantics { contentDescription = contextLabel; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Quote(
                size = 17.dp,
                tint = if (showingContext || state.context.isNotEmpty()) TujiColor.Ink else TujiColor.Ink3,
            )
        }
        val sendLabel = stringResource(R.string.vocabulary_send, state.price?.maxPoints ?: 0)
        Box(
            Modifier
                .size(52.dp)
                .background(if (canGenerate) TujiColor.Ink else TujiColor.Paper2)
                .tujiClickable(enabled = canGenerate, onClick = onSend)
                .semantics { contentDescription = sendLabel; role = Role.Button }
                .testTag("vocabulary-generate"),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.ArrowUp(size = 17.dp, tint = if (canGenerate) TujiColor.Paper else TujiColor.Ink3)
        }
    }
}

/** The free path, always in sight: 圖鑑 already has this word. */
@Composable
private fun CatalogHint(model: VocabularyModel, query: String, onOpenWord: (String) -> Unit, onSearch: (String) -> Unit) {
    val matches = remember(query) { model.catalogMatches(query) }
    val text = when {
        matches.size == 1 -> stringResource(R.string.vocabulary_catalog_one)
        matches.size > 1 -> stringResource(R.string.vocabulary_catalog_many, matches.size)
        else -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .border(1.dp, TujiColor.Ink3)
            .tujiClickable {
                if (matches.size == 1) onOpenWord(matches.first().id) else onSearch(query.trim())
            }
            .padding(horizontal = TujiSpace.S3)
            .testTag("vocabulary-catalog-hint"),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TujiGlyph.Books(size = 16.dp)
        Text(text, style = TujiType.bodySmStrong, color = TujiColor.Ink, modifier = Modifier.weight(1f))
        Text("→", style = TujiType.label, color = TujiColor.Ink)
    }
}

@Composable
private fun Modes(selected: VocabularyMode, onSelect: (VocabularyMode) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        VocabularyMode.entries.forEach { mode ->
            val summary = stringResource(modeSummary(mode))
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .background(if (mode == selected) TujiColor.Current else TujiColor.Paper2)
                    .tujiClickable { onSelect(mode) }
                    .semantics {
                        this.selected = mode == selected
                        role = Role.Tab
                        // TalkBack has no hint slot; the summary is read after the name.
                        contentDescription = summary
                    }
                    .padding(horizontal = TujiSpace.S3)
                    .testTag("vocabulary-mode-${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(modeTitle(mode)), style = TujiType.bodySmStrong, color = TujiColor.Ink)
            }
        }
    }
}

/** The composer's fields: 紙2 ground, and 瞳黃 around the one being typed in. */
@Composable
internal fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    singleLine: Boolean,
    modifier: Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 4,
        textStyle = style.copy(color = TujiColor.Ink),
        cursorBrush = SolidColor(TujiColor.Ink),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        modifier = modifier
            .background(TujiColor.Paper2)
            .then(if (focused) Modifier.border(TujiBorder.Bw2, TujiColor.Current) else Modifier),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S3), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style, color = TujiColor.Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}

// MARK: - 對話的兩邊

/** Moira's side: her face, and the words beside it rather than in a bubble — a card is far longer than a bubble. */
@Composable
internal fun MoiraReplyRow(content: @Composable ColumnScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
        MascotFigure(MascotPose.Face, 36.dp)
        Column(
            Modifier.weight(1f).heightIn(min = 36.dp),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            content = content,
        )
    }
}

/** What was asked: the word, the mode, and the sense or sentence that narrowed it. */
@Composable
private fun VocabularyAskRow(input: VocabularyInput) {
    Box(Modifier.fillMaxWidth().padding(start = TujiSpace.S5), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .background(TujiColor.Paper2)
                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            Text(input.query, style = TujiType.bodyStrong, color = TujiColor.Ink, textAlign = TextAlign.End)
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(stringResource(modeTitle(input.mode)), style = TujiType.label, color = TujiColor.Ink2)
                if (input.selectedSense.isNotEmpty()) {
                    Text("· ${input.selectedSense}", style = TujiType.label, color = TujiColor.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (input.context.isNotEmpty()) {
                Text(input.context, style = TujiType.label, color = TujiColor.Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodySmStrong,
        color = TujiColor.Ink,
        modifier = Modifier.heightIn(min = 44.dp).tujiClickable(onClick = onClick).padding(vertical = TujiSpace.S3),
    )
}

internal fun modeTitle(mode: VocabularyMode): Int = when (mode) {
    VocabularyMode.Lookup -> R.string.vocabulary_mode_lookup
    VocabularyMode.Analysis -> R.string.vocabulary_mode_analysis
    VocabularyMode.Mnemonic -> R.string.vocabulary_mode_mnemonic
}

private fun modeSummary(mode: VocabularyMode): Int = when (mode) {
    VocabularyMode.Lookup -> R.string.vocabulary_mode_lookup_summary
    VocabularyMode.Analysis -> R.string.vocabulary_mode_analysis_summary
    VocabularyMode.Mnemonic -> R.string.vocabulary_mode_mnemonic_summary
}

@Composable
internal fun noteText(note: AssistantNote): String = when (note) {
    is AssistantNote.Server -> note.text
    is AssistantNote.Known -> stringResource(
        when (note.kind) {
            AssistantNote.Kind.InsufficientCredits -> R.string.vocabulary_note_insufficient
            AssistantNote.Kind.NoContent -> R.string.vocabulary_note_no_content
            AssistantNote.Kind.SignIn -> R.string.vocabulary_note_sign_in
            AssistantNote.Kind.PriceUnavailable -> R.string.vocabulary_note_price_unavailable
            AssistantNote.Kind.Confirming -> R.string.vocabulary_note_confirming
            AssistantNote.Kind.HistoryFailed -> R.string.vocabulary_note_history_failed
            AssistantNote.Kind.BookmarkFailed -> R.string.vocabulary_note_bookmark_failed
            AssistantNote.Kind.DeleteFailed -> R.string.vocabulary_note_delete_failed
            AssistantNote.Kind.PriceChanged -> R.string.vocabulary_note_price_changed
            AssistantNote.Kind.RateLimited -> R.string.vocabulary_note_rate_limited
            AssistantNote.Kind.Unavailable -> R.string.vocabulary_note_unavailable
            AssistantNote.Kind.GrammarSignIn -> R.string.grammar_note_sign_in
            AssistantNote.Kind.GrammarHistoryFailed -> R.string.grammar_note_history_failed
        },
    )
}
