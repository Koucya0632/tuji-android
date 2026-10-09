package app.tuji.android.wordlists

import app.tuji.android.form.TujiSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiRow
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.WordList
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.launch

/** What naming a list came to: close the sheet, say why not, or offer 會員方案. */
sealed interface WordListNameResult {
    data object Done : WordListNameResult
    data class Message(val text: Int) : WordListNameResult
    data object NeedsUpgrade : WordListNameResult
}

/** The name rule the server applies, so the button is not offered for a refusal. */
object WordListNameRules {
    const val MAX_LENGTH = 40

    fun valid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed.codePointCount(0, trimmed.length) <= MAX_LENGTH
    }
}

/**
 * 建立詞表 / 重新命名 — iOS's `WordListNameSheet`: one text field and one
 * button. What happens after is the presenting screen's business, so it hands
 * back the name and an outcome.
 */
@Composable
internal fun WordListNameSheet(
    title: String,
    actionTitle: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onNeedsUpgrade: () -> Unit,
    submit: suspend (String) -> WordListNameResult,
) = BottomSheet(title, onDismiss) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Text(stringResource(R.string.wordlist_name), style = TujiType.label, color = TujiColor.Ink3)
    BasicTextField(
        value = name,
        onValueChange = { name = it },
        enabled = !working,
        singleLine = true,
        textStyle = TujiType.body.copy(color = TujiColor.Ink),
        cursorBrush = SolidColor(TujiColor.Current),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(TujiColor.Paper2)
            .border(TujiBorder.Bw1, if (error != null) TujiColor.Alert else TujiColor.Rule)
            .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S3),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) {
                    Text(stringResource(R.string.wordlist_name_placeholder), style = TujiType.body, color = TujiColor.Ink3)
                }
                inner()
            }
        },
    )
    Text(
        error?.let { stringResource(it) } ?: stringResource(R.string.wordlist_name_footer),
        style = TujiType.label,
        color = if (error != null) TujiColor.Alert else TujiColor.Ink3,
    )
    TujiButton(
        text = actionTitle,
        enabled = !working && WordListNameRules.valid(name),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            working = true
            error = null
            scope.launch {
                val result = submit(name.trim())
                working = false
                when (result) {
                    WordListNameResult.Done -> onDismiss()
                    is WordListNameResult.Message -> error = result.text
                    WordListNameResult.NeedsUpgrade -> onNeedsUpgrade()
                }
            }
        },
    )
}

/**
 * 加入詞表 — iOS's `AddToWordListSheet`: one word, every list, a checkbox
 * each. Changes apply as they are ticked, like the rest of this app's switches.
 */
@Composable
internal fun AddToWordListSheet(
    wordId: String,
    store: WordListsStore,
    onDismiss: () -> Unit,
    onNeedsUpgrade: () -> Unit,
) {
    var lists by remember { mutableStateOf<List<WordList>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(emptySet<String>()) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val shared by store.state.collectAsStateWithLifecycle()
    val canCreate = shared.canCreate

    LaunchedEffect(attempt) {
        failed = false
        runCatching { store.listsContaining(wordId) }
            .onSuccess {
                lists = it
                // Nothing to tick yet: go straight to naming the first one.
                if (it.isEmpty() && store.state.value.canCreate) creating = true
            }
            .onFailure { if (lists == null) failed = true }
    }

    suspend fun set(list: WordList, present: Boolean) {
        busy = busy + list.id
        message = null
        when (store.setWord(wordId, list.id, present)) {
            MemberWriteOutcome.Done -> lists = lists?.map {
                if (it.id == list.id) store.state.value.lists.firstOrNull { l -> l.id == list.id } ?: it else it
            }
            MemberWriteOutcome.NeedsUpgrade -> onNeedsUpgrade()
            MemberWriteOutcome.AtLimit -> message = R.string.wordlist_words_at_limit
            MemberWriteOutcome.Missing -> attempt++
            MemberWriteOutcome.Failed -> message = R.string.manage_action_failed
        }
        busy = busy - list.id
    }

    if (creating) {
        WordListNameSheet(
            title = stringResource(R.string.wordlist_create),
            actionTitle = stringResource(R.string.wordlist_create_and_add),
            onDismiss = { creating = false },
            onNeedsUpgrade = {
                creating = false
                onNeedsUpgrade()
            },
        ) { name ->
            val (outcome, created) = store.create(name)
            when (outcome) {
                MemberWriteOutcome.Done -> {
                    created?.let { set(it, present = true) }
                    attempt++
                    WordListNameResult.Done
                }
                MemberWriteOutcome.NeedsUpgrade -> WordListNameResult.NeedsUpgrade
                MemberWriteOutcome.AtLimit -> WordListNameResult.Message(R.string.wordlist_lists_at_limit)
                MemberWriteOutcome.Missing -> WordListNameResult.Done
                MemberWriteOutcome.Failed -> WordListNameResult.Message(R.string.manage_action_failed)
            }
        }
        return
    }

    BottomSheet(stringResource(R.string.wordlist_add), onDismiss) {
        val shown = lists
        when {
            shown != null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                shown.forEachIndexed { index, list ->
                    if (index > 0) TujiRowDivider()
                    TujiRow(
                        trailing = {
                            if (!list.locked && list.id !in busy) {
                                TujiCheckbox(checked = list.containsWord == true) { on ->
                                    scope.launch { set(list, on) }
                                }
                            } else if (list.locked) {
                                TujiGlyph.Lock(tint = TujiColor.Ink3)
                            }
                        },
                    ) {
                        ListLabel(
                            name = list.name,
                            subtitle = if (list.locked) {
                                stringResource(R.string.manage_status_locked)
                            } else {
                                stringResource(R.string.wordlist_word_count, list.wordCount)
                            },
                        )
                    }
                }
                if (canCreate) {
                    if (shown.isNotEmpty()) TujiRowDivider()
                    TujiRow(onClick = { creating = true }) {
                        Text(stringResource(R.string.wordlist_new), style = TujiType.body, color = TujiColor.Ink)
                    }
                }
            }
            failed -> Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                Text(stringResource(R.string.manage_action_failed), style = TujiType.bodySm, color = TujiColor.Ink2)
                TujiButton(text = stringResource(R.string.retry), onClick = { attempt++ })
            }
            else -> TujiPageLoading(label = stringResource(R.string.atlas_loading))
        }
        message?.let { Text(stringResource(it), style = TujiType.bodySm, color = TujiColor.Alert) }
    }
}

/**
 * The word page's 加入詞表 control — iOS's `WordListButton`. Hidden under
 * membership v1; a lock for a non-member, which opens 會員方案.
 */
@Composable
internal fun WordListButton(level: MemberAccessLevel, onOpen: () -> Unit, onLocked: () -> Unit) {
    if (level == MemberAccessLevel.Hidden) return
    TujiIconButton(
        label = stringResource(R.string.wordlist_add),
        onClick = if (level == MemberAccessLevel.Locked) onLocked else onOpen,
        size = 48.dp,
        ground = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box {
            TujiGlyph.Books(tint = TujiColor.Ink)
            if (level == MemberAccessLevel.Locked) {
                TujiGlyph.Lock(
                    size = 9.dp,
                    tint = TujiColor.Ink3,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(start = 14.dp, top = 14.dp),
                )
            }
        }
    }
}

@Composable
internal fun ListLabel(name: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = TujiType.body, color = TujiColor.Ink)
        subtitle?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
    }
}

/** The sheet these share — [TujiSheet]'s head over a padded column. */
@Composable
internal fun BottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) =
    TujiSheet(title = title, onDismiss = onDismiss) {
        Column(
            Modifier.padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) { content() }
    }
