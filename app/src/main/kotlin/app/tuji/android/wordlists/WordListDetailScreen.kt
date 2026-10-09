package app.tuji.android.wordlists

import app.tuji.android.core.design.TujiMenuItem
import app.tuji.android.core.design.TujiMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavIcon
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiPullToRefresh
import app.tuji.android.core.design.TujiRow
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.model.WordListDetailResponse
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.launch

/** One 個人詞表 — iOS's `WordListDetailView`: study it, look through it, trim it. */
@Composable
fun WordListDetailScreen(
    vm: WordListDetailViewModel,
    store: WordListsStore,
    /** A catalogue id → the row, from the copy already in memory. */
    resolve: (String) -> Word?,
    showChinese: Boolean,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onStudy: (StudyMode) -> Unit,
    onNeedsMembership: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val detail = (state as? WordListDetailViewModel.State.Loaded)?.detail

    LaunchedEffect(state) { if (state == WordListDetailViewModel.State.Missing) onBack() }

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (detail?.wordIds?.isNotEmpty() == true) {
                        TextAction(stringResource(if (editing) R.string.manage_done else R.string.wordlist_edit)) {
                            editing = !editing
                        }
                    }
                    if (detail != null) {
                        // iOS's `Menu`: dropped from ⋯, not a sheet from the bottom.
                        Box {
                            TujiNavIcon(label = stringResource(R.string.author_more), onClick = { menu = true }) {
                                TujiGlyph.More(tint = TujiColor.Ink)
                            }
                            if (menu) {
                                TujiMenu(
                                    items = listOfNotNull(
                                        TujiMenuItem(stringResource(R.string.wordlist_rename)) {
                                            menu = false
                                            renaming = true
                                        }.takeIf { detail.canEdit },
                                        TujiMenuItem(stringResource(R.string.wordlist_delete), destructive = true) {
                                            menu = false
                                            confirmDelete = true
                                        },
                                    ),
                                    onDismiss = { menu = false },
                                )
                            }
                        }
                    }
                }
            },
        )
        TujiPullToRefresh(onRefresh = { vm.refresh() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (val s = state) {
                    WordListDetailViewModel.State.Loading, WordListDetailViewModel.State.Missing ->
                        TujiPageLoading(label = stringResource(R.string.atlas_loading))

                    WordListDetailViewModel.State.Failed -> BlankState(
                        text = stringResource(R.string.load_failed),
                        onRetry = { vm.load() },
                    )

                    is WordListDetailViewModel.State.Loaded -> {
                        TujiScreenTitle(s.detail.list.name)
                        Header(s.detail, onStudy, onNeedsMembership)
                        WordRows(
                            detail = s.detail,
                            resolve = resolve,
                            showChinese = showChinese,
                            editing = editing,
                            onOpenWord = onOpenWord,
                            onRemove = { id ->
                                scope.launch {
                                    if (vm.remove(id) == MemberWriteOutcome.Failed) failure = R.string.manage_action_failed
                                    store.reload()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
            }
        }
    }

    if (renaming && detail != null) {
        WordListNameSheet(
            title = stringResource(R.string.wordlist_rename),
            actionTitle = stringResource(R.string.profile_save),
            initialName = detail.list.name,
            onDismiss = { renaming = false },
            onNeedsUpgrade = {
                renaming = false
                onNeedsMembership()
            },
        ) { name ->
            when (store.rename(detail.list.id, name)) {
                MemberWriteOutcome.Done, MemberWriteOutcome.Missing -> {
                    vm.refresh()
                    WordListNameResult.Done
                }
                MemberWriteOutcome.NeedsUpgrade -> WordListNameResult.NeedsUpgrade
                MemberWriteOutcome.AtLimit -> WordListNameResult.Message(R.string.word_note_at_limit)
                MemberWriteOutcome.Failed -> WordListNameResult.Message(R.string.manage_action_failed)
            }
        }
    }
    if (confirmDelete && detail != null) {
        TujiPrompt(
            title = stringResource(R.string.wordlist_delete_confirm),
            message = stringResource(R.string.wordlist_delete_message),
            confirm = stringResource(R.string.manage_delete),
            cancel = stringResource(R.string.cancel),
            style = TujiPromptStyle.Destructive,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    when (store.delete(detail.list.id)) {
                        MemberWriteOutcome.Done, MemberWriteOutcome.Missing -> onBack()
                        MemberWriteOutcome.NeedsUpgrade, MemberWriteOutcome.AtLimit -> Unit
                        MemberWriteOutcome.Failed -> failure = R.string.manage_action_failed
                    }
                }
            },
            onCancel = { confirmDelete = false },
        )
    }
    failure?.let {
        TujiPrompt(
            title = stringResource(R.string.wordlist_failed_title),
            message = stringResource(it),
            confirm = stringResource(R.string.wordlist_got_it),
            cancel = null,
            style = TujiPromptStyle.Error,
            onConfirm = { failure = null },
            onCancel = { failure = null },
        )
    }
}

@Composable
private fun Header(detail: WordListDetailResponse, onStudy: (StudyMode) -> Unit, onNeedsMembership: () -> Unit) {
    Column(
        Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Text(
            stringResource(R.string.wordlist_stats, detail.wordIds.size, detail.stats.seen, detail.stats.due),
            style = TujiType.bodySm,
            color = TujiColor.Ink3,
        )
        if (!detail.canStudy) {
            Row(
                Modifier.fillMaxWidth().tujiClickable(onClick = onNeedsMembership),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TujiGlyph.Lock(tint = TujiColor.Ink2)
                Text(
                    stringResource(if (detail.list.locked) R.string.wordlist_locked_notice else R.string.wordlist_member_notice),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                )
            }
        }
        val modes = WordListDetailViewModel.studyModes(detail)
        if (modes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                modes.forEach { mode ->
                    Text(
                        stringResource(if (mode == StudyMode.New) R.string.study_new_label else R.string.study_review_label),
                        style = TujiType.bodySmStrong,
                        color = TujiColor.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(if (mode == StudyMode.New) TujiColor.Current else TujiColor.Paper2)
                            .tujiClickable { onStudy(mode) }
                            .padding(vertical = TujiSpace.S3),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordRows(
    detail: WordListDetailResponse,
    resolve: (String) -> Word?,
    showChinese: Boolean,
    editing: Boolean,
    onOpenWord: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (detail.wordIds.isEmpty()) {
        BlankState(
            text = stringResource(R.string.wordlist_no_words),
            icon = { TujiGlyph.Plus(size = 40.dp, tint = TujiColor.Ink3) },
        )
        return
    }
    detail.wordIds.forEachIndexed { index, id ->
        if (index > 0) TujiRowDivider()
        val word = resolve(id)
        TujiRow(
            onClick = if (editing) null else ({ onOpenWord(id) }),
            trailing = {
                if (editing) {
                    TujiNavIcon(label = stringResource(R.string.wordlist_remove_word), onClick = { onRemove(id) }) {
                        TujiGlyph.Close(size = 16.dp, tint = TujiColor.Alert)
                    }
                } else {
                    TujiGlyph.ArrowRight(size = 14.dp, tint = TujiColor.Ink3)
                }
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp)) {
                    WordPicture(
                        url = word?.imageUrl,
                        kind = WordImageKind.of(word?.category),
                        inset = TujiSpace.S1,
                        glyphSize = 16.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ListLabel(word?.word ?: id, if (showChinese) word?.chinese else null)
            }
        }
    }
}
