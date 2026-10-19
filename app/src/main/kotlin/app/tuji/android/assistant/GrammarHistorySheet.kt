package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiPullToRefresh
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.GrammarMode
import app.tuji.android.core.model.GrammarRecord
import app.tuji.android.form.TujiSheet
import kotlinx.coroutines.launch

/** 語法紀錄 — iOS's `GrammarHistoryView`: every card, filterable by bookmark and template. */
@Composable
fun GrammarHistorySheet(
    model: GrammarModel,
    uiLanguage: String,
    onOpen: (GrammarRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(GrammarModel.HistoryFilter()) }
    var deleting by remember { mutableStateOf<GrammarRecord?>(null) }
    LaunchedEffect(filter) { model.loadHistory(filter) }

    TujiSheet(
        title = stringResource(R.string.grammar_history),
        onDismiss = onDismiss,
        maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f,
    ) {
        TujiPullToRefresh(onRefresh = { model.loadHistory(filter, force = true) }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).tujiClickable { filter = filter.copy(favorite = !filter.favorite) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.vocabulary_history_favorites), style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                    TujiCheckbox(checked = filter.favorite, onCheckedChange = { filter = filter.copy(favorite = it) })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    FilterChip(stringResource(R.string.grammar_history_all), filter.mode == null) { filter = filter.copy(mode = null) }
                    GrammarMode.entries.forEach { mode ->
                        FilterChip(stringResource(grammarModeTitle(mode)), filter.mode == mode) { filter = filter.copy(mode = mode) }
                    }
                }
                Text(stringResource(R.string.vocabulary_history_note), style = TujiType.label, color = TujiColor.Ink2)
                state.historyMessage?.let { note ->
                    Text(noteText(note), style = TujiType.bodySm, color = TujiColor.Ink)
                    TextAction(stringResource(R.string.credit_statement_retry)) {
                        scope.launch { model.loadHistory(filter, force = true) }
                    }
                }
                if (state.loadingHistory) TujiIndeterminateBar(Modifier.fillMaxWidth())
                if (state.history.isEmpty() && !state.loadingHistory && state.historyMessage == null) {
                    Text(
                        stringResource(R.string.grammar_history_empty),
                        style = TujiType.body,
                        color = TujiColor.Ink,
                        modifier = Modifier.padding(vertical = TujiSpace.S5),
                    )
                }
                state.history.forEach { record ->
                    AssistantHistoryRow(
                        title = record.result?.content?.title ?: record.input?.query.orEmpty(),
                        mode = record.input?.mode?.let { stringResource(grammarModeTitle(it)) },
                        explainedIn = record.input?.uiLanguage,
                        uiLanguage = uiLanguage,
                        gist = record.result?.content?.gist.orEmpty(),
                        createdAt = record.createdAt,
                        favorite = record.favorite,
                        onOpen = { onOpen(record) },
                        onFavorite = { scope.launch { model.toggleFavorite(record) } },
                        onDelete = { deleting = record },
                    )
                }
                if (state.nextCursor != null) {
                    TextAction(stringResource(R.string.vocabulary_history_more)) {
                        if (!state.loadingHistory) scope.launch { model.loadHistory(filter, more = true) }
                    }
                }
            }
        }
    }

    deleting?.let { record ->
        TujiPrompt(
            title = stringResource(R.string.grammar_delete_title),
            message = stringResource(R.string.grammar_delete_message),
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

@Composable
private fun FilterChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (selected) TujiColor.Current else TujiColor.Paper2)
            .tujiClickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TujiSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}
