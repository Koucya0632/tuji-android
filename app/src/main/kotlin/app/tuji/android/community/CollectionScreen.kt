package app.tuji.android.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.community.CollectionLearnAction
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.design.ProfileAvatar
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiInkStat
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiSegmented
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasPublicCollection

/** 目錄 or 簡介. */
private enum class CollectionTab { Contents, About }

/**
 * One 合集 — iOS's `AtlasCollectionDetailView`.
 *
 * A collection is made of photographs somebody took, which is the most
 * persuasive thing about it, so the cover is the first event at full width,
 * and the numbers and the one action collect into an ink bar under it — the
 * screen's centre of gravity. The old page was a title and a plain list.
 */
@Composable
fun CollectionScreen(
    state: CollectionDetailViewModel.State,
    isGuest: Boolean,
    reported: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onSave: () -> Unit,
    onUnsave: () -> Unit,
    onLearn: () -> Unit,
    onDismissError: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onReport: (ReportReason) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(CollectionTab.Contents) }
    var askSignIn by remember { mutableStateOf(false) }
    var askUnsave by remember { mutableStateOf(false) }
    var askLearn by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val collection = state.collection
        when {
            collection != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Cover(collection, onOpenAuthor)
                ActionBar(
                    collection = collection,
                    state = state,
                    onBookmark = {
                        when {
                            isGuest -> askSignIn = true
                            state.saved -> askUnsave = true
                            else -> onSave()
                        }
                    },
                )
                Spacer(Modifier.height(TujiSpace.S4))
                TujiSegmented(
                    options = listOf(
                        CollectionTab.Contents to stringResource(R.string.collection_tab_contents),
                        CollectionTab.About to stringResource(R.string.collection_tab_about),
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                )
                if (tab == CollectionTab.Contents && state.unlocked && state.totalCount > 0) {
                    LearnPill(state, onClick = { askLearn = true }, modifier = Modifier.padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S2))
                }
                Spacer(Modifier.height(TujiSpace.S2))
                when (tab) {
                    CollectionTab.Contents -> Contents(state, onOpenItem, onOpenAuthor)
                    CollectionTab.About -> {
                        val about = collection.description?.trim()?.takeIf { it.isNotEmpty() }
                        Text(
                            about ?: stringResource(R.string.collection_about_empty),
                            style = TujiType.bodySm,
                            color = if (about == null) TujiColor.Ink3 else TujiColor.Ink2,
                            modifier = Modifier.fillMaxWidth().padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S2, bottom = TujiSpace.S5),
                        )
                    }
                }
                // The collection's own title, 簡介 and avatar are public words
                // that a per-item 檢舉 inside it cannot reach.
                if (!state.isOwner) {
                    Text(
                        stringResource(if (reported) R.string.collection_report_received else R.string.collection_report),
                        style = TujiType.label,
                        color = if (reported) TujiColor.Ink3 else TujiColor.Alert,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TujiSpace.S4, bottom = TujiSpace.S6)
                            .tujiClickable(enabled = !reported) { reporting = true }
                            .padding(vertical = TujiSpace.S3),
                    )
                }
            }
            state.loading -> TujiPageLoading(label = stringResource(R.string.community_loading))
            else -> Column(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (state.notFound) R.string.collection_not_found else R.string.community_failed),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    textAlign = TextAlign.Center,
                )
                if (!state.notFound) TujiButton(text = stringResource(R.string.retry), onClick = onRetry)
            }
        }

        // Floats over the cover, so the page opens with the photograph.
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            modifier = Modifier.align(Alignment.TopStart),
        )
    }

    if (askSignIn) {
        TujiPrompt(
            title = stringResource(R.string.collection_sign_in_title),
            message = null,
            confirm = stringResource(R.string.auth_sign_in),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askSignIn = false; onSignIn() },
            onCancel = { askSignIn = false },
        )
    }
    if (askUnsave) {
        TujiPrompt(
            title = stringResource(R.string.collection_unsave_title),
            message = null,
            confirm = stringResource(R.string.collection_confirm),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askUnsave = false; onUnsave() },
            onCancel = { askUnsave = false },
        )
    }
    if (askLearn) {
        TujiPrompt(
            title = stringResource(R.string.collection_learn_title, state.remaining),
            message = null,
            confirm = stringResource(R.string.collection_learn_confirm),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askLearn = false; onLearn() },
            onCancel = { askLearn = false },
        )
    }
    state.error?.let { error ->
        TujiPrompt(
            style = TujiPromptStyle.Error,
            title = stringResource(
                if (error == CollectionDetailViewModel.Error.Bookmark) R.string.collection_action_failed else R.string.collection_learn_failed,
            ),
            message = stringResource(
                if (error == CollectionDetailViewModel.Error.LearnLimit) R.string.collection_learn_limit else R.string.collection_try_later,
            ),
            confirm = stringResource(R.string.collection_confirm),
            cancel = null,
            onConfirm = onDismissError,
            onCancel = onDismissError,
        )
    }
    if (reporting) {
        ReportSheet(
            onPick = { reason ->
                reporting = false
                onReport(reason)
            },
            onDismiss = { reporting = false },
        )
    }
}

@Composable
private fun Cover(collection: AtlasPublicCollection, onOpenAuthor: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        CollectionIdentityTile(collection, Modifier.fillMaxSize())
        // One-way scrim for legibility, not decoration.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TujiColor.Ink.copy(alpha = 0f), TujiColor.Ink.copy(alpha = 0.7f)))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(collection.title, style = TujiType.h1, color = TujiColor.Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
                collection.author?.let { author ->
                    Row(
                        Modifier.tujiClickable { onOpenAuthor(author.handle) },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(avatar = author.avatar, size = 24.dp)
                        Text(
                            author.name,
                            style = TujiType.bodySm,
                            color = TujiColor.Paper.copy(alpha = 0.8f),
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                    }
                }
                LangBadge(collection.targetLanguage.badge(), ground = TujiColor.Paper.copy(alpha = 0.2f), ink = TujiColor.Paper)
            }
        }
    }
}

@Composable
private fun ActionBar(collection: AtlasPublicCollection, state: CollectionDetailViewModel.State, onBookmark: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(TujiColor.Ink).padding(horizontal = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TujiInkStat(stringResource(R.string.community_stat_items), collection.itemCount)
        TujiInkStat(stringResource(R.string.community_stat_saves), collection.saveCount)
        Spacer(Modifier.weight(1f))
        if (state.isOwner) {
            Text(stringResource(R.string.collection_yours), style = TujiType.label, color = TujiColor.Paper.copy(alpha = 0.6f))
        } else {
            // 「收藏」, not 「收進圖鑑」: saving a collection unlocks it and counts
            // toward its author, and puts nothing in your 圖鑑.
            val waiting = state.bookmarkBusy || !state.bookmarkKnown
            Box(
                Modifier
                    .widthIn(min = 96.dp)
                    .heightIn(min = 44.dp)
                    .background(if (state.saved) TujiColor.Paper.copy(alpha = 0.2f) else TujiColor.Current)
                    .tujiClickable(enabled = !waiting, onClick = onBookmark)
                    .semantics { selected = state.saved }
                    .padding(horizontal = TujiSpace.S3),
                contentAlignment = Alignment.Center,
            ) {
                if (waiting) {
                    Box(Modifier.widthIn(min = 40.dp).height(3.dp).background(if (state.saved) TujiColor.Paper else TujiColor.Ink))
                } else {
                    Text(
                        stringResource(if (state.saved) R.string.collection_saved else R.string.collection_save),
                        style = TujiType.h3,
                        color = if (state.saved) TujiColor.Paper else TujiColor.Ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun LearnPill(state: CollectionDetailViewModel.State, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action = state.learnAction
    val done = action == CollectionLearnAction.AllLearning
    Row(
        modifier
            .height(30.dp)
            .background(if (done) TujiColor.Paper3 else TujiColor.AccumulationSoft)
            .tujiClickable(enabled = !done && !state.learningBusy, onClick = onClick)
            .padding(horizontal = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (done) TujiColor.Ink3 else TujiColor.Accumulation
        if (done) TujiGlyph.Check(size = 11.dp, tint = ink) else Text("＋", style = TujiType.label, color = ink)
        Text(
            when (action) {
                CollectionLearnAction.AllLearning -> stringResource(R.string.collection_learn_all_learning)
                is CollectionLearnAction.AddRemaining -> stringResource(R.string.collection_learn_remaining, action.count)
                CollectionLearnAction.AddAll -> stringResource(R.string.collection_learn_all)
            },
            style = TujiType.label,
            color = ink,
        )
    }
}

@Composable
private fun Contents(
    state: CollectionDetailViewModel.State,
    onOpenItem: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    if (state.items.isEmpty()) {
        Text(
            stringResource(R.string.collection_empty),
            style = TujiType.bodySm,
            color = TujiColor.Ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = TujiSpace.S5),
        )
        return
    }
    Column(Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S5), verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        state.items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                row.forEach { item ->
                    // Locked, the members are a preview to look at, not doors.
                    PublicItemTile(
                        item = item,
                        enabled = state.unlocked,
                        onOpen = { onOpenItem(item.slug) },
                        onOpenAuthor = item.author?.let { author -> { onOpenAuthor(author.handle) } }.takeIf { state.unlocked },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (!state.unlocked) LockedNote(state.totalCount)
    }
}
