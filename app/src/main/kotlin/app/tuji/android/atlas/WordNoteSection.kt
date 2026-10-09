package app.tuji.android.atlas

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import app.tuji.android.form.TujiSheet
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.MemberAccess
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.launch

/**
 * 我的筆記 on the word page — iOS's `WordNoteSection`.
 *
 * Hidden under membership v1 and before the entitlement loads (a null
 * [entitlement]); a lock for a non-member; read-only for a non-member who
 * already wrote one.
 */
@Composable
internal fun WordNoteSection(
    wordId: String,
    store: WordNotesStore,
    entitlement: Entitlement?,
    /** Where a lock, or a refused save, leads. */
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes by store.notes.collectAsStateWithLifecycle()
    val note = notes.byWordId[wordId]
    val level = MemberAccess.level(MemberFeature.WordNote, entitlement, hasOwnData = note != null)
    var editing by rememberSaveable(wordId) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(wordId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Asked whenever the feature exists at all, so a refunded non-member still
    // finds their own notes.
    val visible = MemberAccess.level(MemberFeature.WordNote, entitlement, hasOwnData = true) != MemberAccessLevel.Hidden
    LaunchedEffect(visible) { if (visible) store.loadIfNeeded() }

    when (level) {
        MemberAccessLevel.Hidden -> Unit

        MemberAccessLevel.Locked -> NoteCard(modifier.tujiClickable(onClick = onLocked)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TujiGlyph.Lock(tint = TujiColor.Ink3)
                Text(stringResource(R.string.word_note_locked), style = TujiType.bodySm, color = TujiColor.Ink3)
            }
        }

        MemberAccessLevel.ReadOnly -> NoteCard(modifier) {
            NoteText(note?.body.orEmpty())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.word_note_member_to_edit),
                    style = TujiType.label.copy(textDecoration = TextDecoration.Underline),
                    color = TujiColor.Ink2,
                    modifier = Modifier.tujiClickable(onClick = onLocked),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.word_note_delete),
                    style = TujiType.label,
                    color = TujiColor.Alert,
                    modifier = Modifier.tujiClickable { confirmDelete = true },
                )
            }
        }

        MemberAccessLevel.Open -> NoteCard(modifier.tujiClickable { editing = true }) {
            if (note != null) {
                NoteText(note.body)
            } else {
                Text(stringResource(R.string.word_note_placeholder), style = TujiType.bodySm, color = TujiColor.Ink3)
            }
        }
    }

    if (editing) {
        WordNoteEditorSheet(
            wordId = wordId,
            store = store,
            onDismiss = { editing = false },
            onNeedsUpgrade = {
                // Closed first: 會員方案 is a page, and a window left open
                // would sit over it.
                editing = false
                onLocked()
            },
        )
    }
    if (confirmDelete) {
        TujiPrompt(
            title = stringResource(R.string.word_note_delete_confirm),
            message = null,
            confirm = stringResource(R.string.manage_delete),
            cancel = stringResource(R.string.cancel),
            style = TujiPromptStyle.Destructive,
            onConfirm = {
                confirmDelete = false
                scope.launch { store.delete(wordId) }
            },
            onCancel = { confirmDelete = false },
        )
    }
}

/**
 * The note, read-only, where review reveals the answer — iOS's `WordNoteLine`.
 * Nothing at all when there is none: the reveal is not the place to invite
 * writing one.
 */
@Composable
internal fun WordNoteLine(body: String?, modifier: Modifier = Modifier) {
    if (body.isNullOrBlank()) return
    // iOS marks it with `note.text` rather than a rule: it is the reader's own
    // writing, and the mark says whose.
    val label = stringResource(R.string.word_note_title)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        TujiGlyph.Note(
            size = 13.dp,
            tint = TujiColor.Ink3,
            modifier = Modifier.padding(top = 3.dp).semantics { contentDescription = label },
        )
        Text(
            body,
            style = TujiType.bodySm,
            color = TujiColor.Ink2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Writing or rewriting one note. Saving an empty note is not offered — deleting is. */
@Composable
private fun WordNoteEditorSheet(
    wordId: String,
    store: WordNotesStore,
    onDismiss: () -> Unit,
    onNeedsUpgrade: () -> Unit,
) = TujiSheet(title = stringResource(R.string.word_note_title), onDismiss = onDismiss) {
    val notes by store.notes.collectAsStateWithLifecycle()
    val existing = notes.byWordId[wordId]
    var text by rememberSaveable(wordId) { mutableStateOf(existing?.body.orEmpty()) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val trimmed = text.trim()
    val count = trimmed.codePointCount(0, trimmed.length)

    fun run(write: suspend () -> MemberWriteOutcome) {
        working = true
        error = null
        scope.launch {
            val outcome = write()
            working = false
            when (outcome) {
                MemberWriteOutcome.Done, MemberWriteOutcome.Missing -> onDismiss()
                MemberWriteOutcome.NeedsUpgrade -> onNeedsUpgrade()
                MemberWriteOutcome.AtLimit -> error = R.string.word_note_at_limit
                MemberWriteOutcome.Failed -> error = R.string.manage_action_failed
            }
        }
    }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Text(stringResource(R.string.word_note_field), style = TujiType.label, color = TujiColor.Ink3)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !working,
                minLines = 4,
                maxLines = 10,
                textStyle = TujiType.body.copy(color = TujiColor.Ink),
                cursorBrush = SolidColor(TujiColor.Current),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TujiColor.Paper2)
                    .border(TujiBorder.Bw1, if (error != null) TujiColor.Alert else TujiColor.Rule)
                    .padding(TujiSpace.S3),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.word_note_placeholder), style = TujiType.body, color = TujiColor.Ink3)
                        }
                        inner()
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                error?.let { Text(stringResource(it), style = TujiType.label, color = TujiColor.Alert) }
                Spacer(Modifier.weight(1f))
                Text(
                    "$count / ${notes.maxLength}",
                    style = TujiType.label,
                    color = if (count > notes.maxLength) TujiColor.Alert else TujiColor.Ink3,
                )
            }
            TujiButton(
                text = stringResource(if (working) R.string.profile_saving else R.string.profile_save),
                onClick = { run { store.save(wordId, text) } },
                enabled = !working && store.isValid(text),
                modifier = Modifier.fillMaxWidth(),
            )
            if (existing != null) {
                Text(
                    stringResource(R.string.word_note_delete),
                    style = TujiType.bodySm,
                    color = TujiColor.Alert,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .tujiClickable(enabled = !working) { run { store.delete(wordId) } }
                        .padding(TujiSpace.S2),
                )
            }
        }
}

@Composable
private fun NoteText(body: String) {
    Text(body, style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NoteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(TujiColor.Paper2)
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Text(
            stringResource(R.string.word_note_title),
            style = TujiType.label.copy(letterSpacing = 2.sp),
            color = TujiColor.Ink3,
        )
        content()
    }
}
