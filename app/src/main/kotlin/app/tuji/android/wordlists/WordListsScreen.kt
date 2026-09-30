package app.tuji.android.wordlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiErrorState
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
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.MembershipTier
import app.tuji.android.core.model.WordList
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.launch

/**
 * 詞表 — iOS's `WordListsView`: the account's 個人詞表 in the current
 * learning language.
 *
 * Order matters beyond taste: after a downgrade the first lists in this order
 * stay usable, so 排序 is how a person chooses which ones. It is never gated.
 */
@Composable
fun WordListsScreen(
    store: WordListsStore,
    tier: MembershipTier,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    onNeedsMembership: () -> Unit,
) {
    val state by store.state.collectAsStateWithLifecycle()
    var reordering by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { store.loadIfNeeded() }

    fun handle(outcome: MemberWriteOutcome) {
        when (outcome) {
            MemberWriteOutcome.Done, MemberWriteOutcome.Missing -> Unit
            MemberWriteOutcome.NeedsUpgrade -> onNeedsMembership()
            MemberWriteOutcome.AtLimit -> failure = R.string.word_note_at_limit
            MemberWriteOutcome.Failed -> failure = R.string.manage_action_failed
        }
    }

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.lists.size > 1) {
                        TextAction(stringResource(if (reordering) R.string.manage_done else R.string.wordlist_reorder)) {
                            reordering = !reordering
                        }
                    }
                    if (state.canCreate) {
                        TujiNavIcon(label = stringResource(R.string.wordlist_create), onClick = { creating = true }) {
                            TujiGlyph.Plus(size = 18.dp, tint = TujiColor.Ink)
                        }
                    }
                }
            },
        )
        TujiPullToRefresh(onRefresh = { store.reload() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TujiScreenTitle(stringResource(R.string.wordlist_title))
                Summary(state, tier, onNeedsMembership)
                when (state.phase) {
                    WordListsStore.Phase.Idle, WordListsStore.Phase.Loading ->
                        TujiPageLoading(label = stringResource(R.string.atlas_loading))

                    WordListsStore.Phase.Failed -> TujiErrorState(
                        title = stringResource(R.string.manage_action_failed),
                        modifier = Modifier.padding(horizontal = TujiSpace.S4),
                    ) {
                        TujiButton(text = stringResource(R.string.retry), onClick = { scope.launch { store.reload() } })
                    }

                    WordListsStore.Phase.Loaded -> if (state.lists.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(TujiSpace.S4),
                            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.wordlist_empty), style = TujiType.bodySm, color = TujiColor.Ink2)
                            if (state.canCreate) {
                                TujiButton(text = stringResource(R.string.wordlist_create), onClick = { creating = true })
                            }
                        }
                    } else {
                        state.lists.forEachIndexed { index, list ->
                            if (index > 0) TujiRowDivider()
                            ListRow(
                                list = list,
                                index = index,
                                count = state.lists.size,
                                reordering = reordering,
                                onOpen = { onOpenList(list.id) },
                                onMove = { by -> scope.launch { handle(store.move(list.id, by)) } },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
            }
        }
    }

    if (creating) {
        WordListNameSheet(
            title = stringResource(R.string.wordlist_create),
            actionTitle = stringResource(R.string.wordlist_create_confirm),
            onDismiss = { creating = false },
            onNeedsUpgrade = {
                creating = false
                onNeedsMembership()
            },
        ) { name ->
            when (store.create(name).first) {
                MemberWriteOutcome.Done, MemberWriteOutcome.Missing -> WordListNameResult.Done
                MemberWriteOutcome.NeedsUpgrade -> WordListNameResult.NeedsUpgrade
                MemberWriteOutcome.AtLimit -> WordListNameResult.Message(R.string.wordlist_lists_at_limit)
                MemberWriteOutcome.Failed -> WordListNameResult.Message(R.string.manage_action_failed)
            }
        }
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
private fun Summary(state: WordListsStore.Snapshot, tier: MembershipTier, onNeedsMembership: () -> Unit) {
    val limits = state.limits
    val modifier = Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S3)
    if (tier.isMember && limits != null) {
        Text(
            stringResource(R.string.wordlist_summary, state.lists.size, limits.lists, limits.words),
            style = TujiType.bodySm,
            color = TujiColor.Ink3,
            modifier = modifier,
        )
    } else if (!tier.isMember) {
        // A refund leaves the lists in place, read-only.
        Text(
            stringResource(R.string.wordlist_readonly_notice),
            style = TujiType.bodySm.copy(textDecoration = TextDecoration.Underline),
            color = TujiColor.Ink2,
            modifier = modifier.fillMaxWidth().tujiClickable(onClick = onNeedsMembership),
        )
    }
}

@Composable
private fun ListRow(
    list: WordList,
    index: Int,
    count: Int,
    reordering: Boolean,
    onOpen: () -> Unit,
    onMove: (Int) -> Unit,
) {
    TujiRow(
        onClick = if (reordering) null else onOpen,
        trailing = {
            if (reordering) {
                MoveButton(R.string.wordlist_move_up, enabled = index > 0, flipped = false) { onMove(-1) }
                MoveButton(R.string.wordlist_move_down, enabled = index < count - 1, flipped = true) { onMove(1) }
            } else {
                if (list.locked) TujiGlyph.Lock(size = 14.dp, tint = TujiColor.Ink3)
                TujiGlyph.ArrowRight(size = 14.dp, tint = TujiColor.Ink3)
            }
        },
    ) {
        ListLabel(list.name, stringResource(R.string.wordlist_word_count, list.wordCount))
    }
}

@Composable
private fun MoveButton(label: Int, enabled: Boolean, flipped: Boolean, onClick: () -> Unit) {
    TujiNavIcon(
        label = stringResource(label),
        onClick = { if (enabled) onClick() },
        modifier = Modifier.alpha(if (enabled) 1f else 0.3f),
    ) {
        TujiGlyph.ArrowUp(
            size = 16.dp,
            tint = TujiColor.Ink,
            modifier = if (flipped) Modifier.rotate(180f) else Modifier,
        )
    }
}

@Composable
internal fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodySmStrong,
        color = TujiColor.Ink,
        modifier = Modifier.tujiClickable(onClick = onClick).padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
    )
}

/**
 * 我 → 詞表 — iOS's `MeWordListsRow`. Absent under membership v1 and for
 * guests; a lock for a non-member with nothing yet; otherwise the way in.
 */
@Composable
fun MeWordListsRow(locked: Boolean, listCount: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(1.dp, TujiColor.Rule.copy(alpha = 0.2f))
            .tujiClickable(onClick = onClick)
            .padding(TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TujiGlyph.Books(tint = TujiColor.Ink2)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.wordlist_title), style = TujiType.bodySmStrong, color = TujiColor.Ink)
            Text(
                if (locked) stringResource(R.string.wordlist_me_locked) else stringResource(R.string.wordlist_me_count, listCount),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
        }
        if (locked) TujiGlyph.Lock(tint = TujiColor.Ink3) else TujiGlyph.ArrowRight(size = 12.dp, tint = TujiColor.Ink3)
    }
}
