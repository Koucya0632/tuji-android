package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiPullToRefresh
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.form.TujiSheet
import kotlinx.coroutines.launch

/**
 * 查詞紀錄 — iOS's `VocabularyHistoryView`: every card this account was given
 * in this learning direction, free to read again. Opening one puts it back in
 * the conversation.
 */
@Composable
fun VocabularyHistorySheet(
    model: VocabularyModel,
    uiLanguage: String,
    onOpen: (VocabularyRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var favorites by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VocabularyRecord?>(null) }
    LaunchedEffect(favorites) { model.loadHistory(favorite = favorites) }

    TujiSheet(
        title = stringResource(R.string.vocabulary_history),
        onDismiss = onDismiss,
        maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f,
    ) {
        TujiPullToRefresh(onRefresh = { model.loadHistory(favorite = favorites, force = true) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).tujiClickable { favorites = !favorites },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.vocabulary_history_favorites),
                        style = TujiType.body,
                        color = TujiColor.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    TujiCheckbox(checked = favorites, onCheckedChange = { favorites = it })
                }
                Text(stringResource(R.string.vocabulary_history_note), style = TujiType.label, color = TujiColor.Ink2)
                state.historyMessage?.let { note ->
                    Text(noteText(note), style = TujiType.bodySm, color = TujiColor.Ink)
                    Text(
                        stringResource(R.string.credit_statement_retry),
                        style = TujiType.bodyStrong,
                        color = TujiColor.Ink,
                        modifier = Modifier.tujiClickable {
                            scope.launch { model.loadHistory(favorite = favorites, force = true) }
                        },
                    )
                }
                if (state.loadingHistory) TujiIndeterminateBar(Modifier.fillMaxWidth())
                if (state.history.isEmpty() && !state.loadingHistory && state.historyMessage == null) {
                    Text(
                        stringResource(R.string.vocabulary_history_empty),
                        style = TujiType.body,
                        color = TujiColor.Ink,
                        modifier = Modifier.padding(vertical = TujiSpace.S5),
                    )
                }
                state.history.forEach { record ->
                    AssistantHistoryRow(
                        title = record.result?.content?.headword?.text ?: record.input?.query.orEmpty(),
                        mode = record.input?.mode?.let { stringResource(modeTitle(it)) },
                        explainedIn = record.input?.uiLanguage,
                        uiLanguage = uiLanguage,
                        gist = record.result?.content?.headword?.meaning.orEmpty(),
                        createdAt = record.createdAt,
                        favorite = record.favorite,
                        onOpen = { onOpen(record) },
                        onFavorite = { scope.launch { model.toggleFavorite(record) } },
                        onDelete = { deleting = record },
                    )
                }
                if (state.nextCursor != null) {
                    Text(
                        stringResource(R.string.vocabulary_history_more),
                        style = TujiType.bodyStrong,
                        color = if (state.loadingHistory) TujiColor.Ink3 else TujiColor.Ink,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .tujiClickable(enabled = !state.loadingHistory) {
                                scope.launch { model.loadHistory(favorite = favorites, more = true) }
                            }
                            .padding(vertical = TujiSpace.S3),
                    )
                }
            }
        }
    }

    deleting?.let { record ->
        TujiPrompt(
            title = stringResource(R.string.vocabulary_delete_title),
            message = stringResource(R.string.vocabulary_delete_message),
            confirm = stringResource(R.string.vocabulary_delete),
            cancel = stringResource(R.string.vocabulary_delete_cancel),
            onConfirm = {
                deleting = null
                scope.launch { model.delete(record) }
            },
            onCancel = { deleting = null },
            style = TujiPromptStyle.Destructive,
        )
    }
}

/** One saved card in 查詞紀錄 or 語法紀錄: what it is about, its template, and what it said. */
@Composable
internal fun AssistantHistoryRow(
    title: String,
    mode: String?,
    explainedIn: String?,
    uiLanguage: String,
    gist: String,
    createdAt: String,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.weight(1f).tujiClickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(title, style = TujiType.h3, color = TujiColor.Ink, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
                mode?.let { Text(it, style = TujiType.label, color = TujiColor.Ink) }
                // A card keeps the language it was explained in; say so when it is not this one.
                if (explainedIn != null && explainedIn != uiLanguage) {
                    Text(
                        nativeName(explainedIn),
                        style = TujiType.label,
                        color = TujiColor.Ink3,
                        modifier = Modifier.border(1.dp, TujiColor.Ink3).padding(horizontal = TujiSpace.S1),
                    )
                }
            }
            Text(gist, style = TujiType.bodySm, color = TujiColor.Ink, maxLines = 2)
            Text(createdAt.take(10), style = TujiType.label, color = TujiColor.Ink3)
        }
        Column {
            val bookmark = stringResource(if (favorite) R.string.vocabulary_unbookmark else R.string.vocabulary_bookmark)
            IconAction(bookmark, onFavorite) { TujiGlyph.Bookmark(filled = favorite) }
            IconAction(stringResource(R.string.vocabulary_delete), onDelete) { TujiGlyph.Trash() }
        }
    }
}

@Composable
private fun IconAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .tujiClickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** Each language in itself — never localized, so a speaker of it can always read it. */
private fun nativeName(code: String): String = when (UiLanguage.entries.firstOrNull { it.wire == code } ?: UiLanguage.ZhHant) {
    UiLanguage.ZhHant -> "繁體中文"
    UiLanguage.ZhHans -> "简体中文"
    UiLanguage.Ja -> "日本語"
    UiLanguage.En -> "English"
}
