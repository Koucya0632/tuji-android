package app.tuji.android.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.community.AuthorSegment
import app.tuji.android.core.community.AuthorShelf
import app.tuji.android.core.community.LanguageGroup
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ViewerRelationship
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.ProfileAvatar
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiInkStat
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavIcon
import app.tuji.android.core.design.TujiSegmented
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.TargetLanguage

/**
 * One author's public work — iOS's `AtlasAuthorProfileView`.
 *
 * The same page for everyone, because its value is that it *is* the page other
 * people see. What changes with the reader is only the bar: someone else's
 * page has 更多 (檢舉這位作者, 封鎖); your own has nothing to protect you from.
 *
 * A 檢舉 here targets the **identity**, not one word — which is why it carries
 * the TJ UID rather than a slug: a slug moves with a rename and a UID does not.
 */
@Composable
fun AuthorScreen(
    state: AuthorViewModel.State,
    relationship: ViewerRelationship,
    blocked: Boolean,
    reported: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onReport: (ReportReason) -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var askBlock by remember { mutableStateOf(false) }
    // The reader's pick survives a trip into one of the items and back, which
    // rebuilds the page underneath. Null until they pick: then the collections
    // lead when there are any.
    var chosen by rememberSaveable { mutableStateOf<AuthorSegment?>(null) }
    val segment = chosen ?: AuthorShelf.defaultSegment(state.collections)
    val isMine = relationship == ViewerRelationship.Mine

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            trailing = if (relationship == ViewerRelationship.Theirs) {
                {
                    TujiNavIcon(label = stringResource(R.string.author_more), onClick = { showMore = true }) {
                        TujiGlyph.More(size = 20.dp, tint = TujiColor.Ink2)
                    }
                }
            } else {
                null
            },
        )

        val author = state.author
        when {
            author != null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
            ) {
                Header(author)
                if (state.showsSegments) {
                    TujiSegmented(
                        options = listOf(
                            AuthorSegment.Collections to stringResource(R.string.author_segment_collections),
                            AuthorSegment.Items to stringResource(R.string.author_segment_items),
                        ),
                        selected = segment,
                        onSelect = { chosen = it },
                    )
                }
                when (AuthorShelf.visible(segment, state.collections)) {
                    AuthorSegment.Collections -> Column {
                        state.collections.forEachIndexed { index, collection ->
                            if (index > 0) RowRule()
                            CollectionRow(collection, onOpen = { onOpenCollection(collection.slug) }, showsAuthor = false)
                        }
                    }
                    AuthorSegment.Items -> Items(state, isMine, onOpenItem)
                }
                Spacer(Modifier.height(TujiSpace.S6))
            }
            state.phase == AuthorViewModel.Phase.Loading -> Centered(stringResource(R.string.community_loading))
            // Your own page with no public identity behind it yet: the way
            // forward, not "not found".
            state.phase == AuthorViewModel.Phase.NotFound && isMine -> Box(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                contentAlignment = Alignment.Center,
            ) {
                MascotEmptyState(
                    title = stringResource(R.string.author_empty_self),
                    message = stringResource(R.string.author_empty_self_message),
                    pose = MascotPose.Think,
                )
            }
            state.phase == AuthorViewModel.Phase.NotFound -> Centered(stringResource(R.string.author_not_found))
            else -> Column(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.community_failed), style = TujiType.bodySm, color = TujiColor.Ink3, textAlign = TextAlign.Center)
                TujiButton(text = stringResource(R.string.retry), onClick = onRetry)
            }
        }
    }

    if (showMore) {
        MoreSheet(
            reported = reported,
            blocked = blocked,
            onReport = { showMore = false; reporting = true },
            onBlock = { showMore = false; askBlock = true },
            onDismiss = { showMore = false },
        )
    }
    if (reporting) {
        ReportSheet(
            onPick = { reason -> reporting = false; onReport(reason) },
            onDismiss = { reporting = false },
        )
    }
    if (askBlock) {
        BlockPrompt(
            blocked = blocked,
            onConfirm = { askBlock = false; if (blocked) onUnblock() else onBlock() },
            onCancel = { askBlock = false },
        )
    }
}

/**
 * A full-bleed ink block rather than a white card. This page is about a
 * person, and the block gives it that weight — the same block 今天 uses for
 * "you", here for "them".
 */
@Composable
private fun Header(author: AtlasAuthor) {
    Column(
        Modifier.fillMaxWidth().background(TujiColor.Ink).padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            ProfileAvatar(avatar = author.avatar, size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(author.name, style = TujiType.h2, color = TujiColor.Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // "UID: " rather than "@": the @ form promises a handle you
                // could type or mention, and this is a code nobody types.
                Text(
                    stringResource(R.string.community_author_uid, author.handle),
                    style = TujiType.monoLabel,
                    color = TujiColor.Paper.copy(alpha = 0.6f),
                )
            }
        }
        author.bio?.trim()?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = TujiType.body, color = TujiColor.Paper.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = TujiSpace.S1), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S5)) {
            TujiInkStat(label = stringResource(R.string.community_stat_published), value = author.publishedCount ?: 0)
            // How much this author's work has helped other people.
            TujiInkStat(label = stringResource(R.string.community_stat_saves), value = author.saveCount ?: 0)
        }
    }
}

@Composable
private fun Items(state: AuthorViewModel.State, isMine: Boolean, onOpenItem: (String) -> Unit) {
    Column(Modifier.padding(horizontal = TujiSpace.S4), verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        // Redundant once the switch above names the section.
        if (!state.showsSegments) {
            Text(stringResource(R.string.author_items_title), style = TujiType.bodySmStrong, color = TujiColor.Ink2)
        }
        if (state.groups.isEmpty()) {
            Text(
                stringResource(if (isMine) R.string.author_empty_self else R.string.author_empty),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
        } else {
            state.groups.forEach { group -> Group(group, labelled = state.groups.size > 1, onOpenItem = onOpenItem) }
        }
    }
}

/** One language's items. Unlabelled when the author only ever published in one — a single heading explains nothing. */
@Composable
private fun Group(group: LanguageGroup, labelled: Boolean, onOpenItem: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        if (labelled) {
            Text(
                "${stringResource(if (group.language == TargetLanguage.JA) R.string.direction_zh_ja else R.string.direction_zh_en)} (${group.items.size})",
                style = TujiType.label.copy(letterSpacing = 2.sp),
                color = TujiColor.Ink3,
            )
        }
        group.items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                row.forEach { item ->
                    PublicItemTile(
                        item = item,
                        enabled = true,
                        onOpen = { onOpenItem(item.slug) },
                        // Already on the author's page.
                        onOpenAuthor = null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 更多 — iOS's menu. One control rather than two icons: 檢舉 asks someone else
 * to judge this person's name, bio and avatar; 封鎖 is the reader's own
 * decision to stop seeing them.
 */
@Composable
private fun MoreSheet(
    reported: Boolean,
    blocked: Boolean,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) = TujiWindow(onDismiss = onDismiss) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Scrim)
            .tujiClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                // Swallows taps, so touching the sheet does not dismiss it
                // through the scrim underneath.
                .tujiClickable {}
                .navigationBarsPadding()
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(
                stringResource(if (reported) R.string.collection_report_received else R.string.author_report),
                style = TujiType.body,
                color = if (reported) TujiColor.Ink3 else TujiColor.Alert,
                modifier = Modifier
                    .fillMaxWidth()
                    .tujiClickable(enabled = !reported, onClick = onReport)
                    .padding(vertical = TujiSpace.S2),
            )
            // Blocking is destructive-flavoured; undoing it is not.
            Text(
                blockControlLabel(blocked),
                style = TujiType.body,
                color = if (blocked) TujiColor.Ink else TujiColor.Alert,
                modifier = Modifier
                    .fillMaxWidth()
                    .tujiClickable(onClick = onBlock)
                    .padding(vertical = TujiSpace.S2),
            )
            TujiButton(
                text = stringResource(R.string.cancel),
                style = TujiButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
