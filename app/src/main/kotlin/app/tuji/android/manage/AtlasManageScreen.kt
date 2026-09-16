package app.tuji.android.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.community.ImageStatus
import app.tuji.android.core.community.ReviewStatus
import app.tuji.android.core.community.ShelfRow
import app.tuji.android.core.community.ShelfState
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSkeletonRows
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavIcon
import app.tuji.android.core.design.TujiSegmented
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSection
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusBlocker
import app.tuji.android.core.design.TujiStatusKind
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.TargetLanguage
import coil3.compose.AsyncImage

/**
 * 圖鑑管理 — iOS's `AtlasManageView`: 圖鑑卡片, every photo this account made a
 * card from, in the language being learned, to open, select and delete; and
 * 合集, the collections made from them. Making cards is the camera's job.
 */
@Composable
fun AtlasManageScreen(
    state: AtlasManageViewModel.State,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelecting: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (Set<String>) -> Unit,
    collections: MyCollectionsViewModel.State,
    onLoadCollections: () -> Unit,
    onRefreshCollections: suspend () -> Unit = {},
    onCreateCollection: (title: String, description: String, onCreated: () -> Unit) -> Unit,
    onDismissCreateError: () -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    var askDelete by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(ManageSection.Cards) }
    var creating by remember { mutableStateOf(false) }
    val rows = state.rows
    // Asked on the first visit to 合集, not before: most people open this
    // page for a card, and the collections are one more request.
    LaunchedEffect(section) {
        if (section == ManageSection.Collections) onLoadCollections()
        if (section != ManageSection.Cards) onSelecting(false)
    }

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            trailing = if (section == ManageSection.Collections) {
                {
                    TujiNavIcon(label = stringResource(R.string.collections_create), onClick = { creating = true }) {
                        TujiGlyph.Plus(size = 16.dp, tint = TujiColor.Ink)
                    }
                }
            } else if (rows.isNotEmpty()) {
                {
                    Text(
                        stringResource(if (state.selecting) R.string.manage_done else R.string.manage_select),
                        style = TujiType.bodyStrong,
                        color = TujiColor.Ink,
                        modifier = Modifier.tujiClickable { onSelecting(!state.selecting) }.padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
                    )
                }
            } else {
                null
            },
        )
        TujiScreenTitle(stringResource(R.string.manage_title))
        TujiSegmented(
            options = listOf(
                ManageSection.Cards to stringResource(R.string.manage_section_cards),
                ManageSection.Collections to stringResource(R.string.manage_section_collections),
            ),
            selected = section,
            onSelect = { section = it },
            modifier = Modifier.padding(vertical = TujiSpace.S3),
        )
        if (section == ManageSection.Collections) {
            MyCollectionsPane(
                collections,
                onRetry = onLoadCollections,
                onOpen = onOpenCollection,
                modifier = Modifier.weight(1f),
                onRefresh = onRefreshCollections,
            )
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TujiSection(title = stringResource(R.string.manage_cards_section)) {
                if (state.actionFailed) {
                    Text(
                        stringResource(R.string.manage_action_failed),
                        style = TujiType.bodySm,
                        color = TujiColor.Alert,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                    )
                }
                when (val shelf = state.shelf) {
                    ShelfState.Loading ->
                        TujiSkeletonRows(count = 4, height = 88.dp, label = stringResource(R.string.atlas_loading))
                    ShelfState.Failed -> Column(
                        Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    ) {
                        Text(stringResource(R.string.atlas_failed), style = TujiType.label, color = TujiColor.Ink3)
                        Text(
                            stringResource(R.string.retry),
                            style = TujiType.bodyStrong,
                            color = TujiColor.Ink,
                            modifier = Modifier.background(TujiColor.Current).tujiClickable(onClick = onRetry).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
                        )
                    }
                    is ShelfState.HiddenElsewhere -> HiddenRow(shelf.count, state.language)
                    ShelfState.Empty -> Column(
                        Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.manage_empty), style = TujiType.bodySmStrong, color = TujiColor.Ink)
                        Text(stringResource(R.string.manage_empty_hint), style = TujiType.label, color = TujiColor.Ink3)
                    }
                    ShelfState.Loaded -> {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) TujiRowDivider()
                            CardRow(
                                row = row,
                                selecting = state.selecting,
                                selected = row.id in state.selected,
                                onClick = { if (state.selecting) onToggle(row.id) else onOpen(row.id) },
                            )
                        }
                        if (state.hidden > 0) HiddenRow(state.hidden, state.language)
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(TujiSpace.S6))
        }
        if (state.selecting && state.selected.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().background(TujiColor.Paper).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3)) {
                Text(
                    // Both this and the card over it say 刪除中…, which is what
                    // iOS does: the card is a window of its own and the bar is
                    // what the page still reads as behind it.
                    if (state.deleting) {
                        stringResource(R.string.manage_deleting)
                    } else {
                        stringResource(R.string.manage_delete_count, state.selected.size)
                    },
                    style = TujiType.h3,
                    color = TujiColor.Paper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(TujiColor.Alert)
                        .tujiClickable(enabled = !state.deleting) { askDelete = true }
                        .padding(vertical = TujiSpace.S3),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    // Deleting reaches other accounts when any of the chosen cards is public,
    // so the page stops answering until it is done: the selection this is
    // deleting cannot be edited underneath it, and 刪除中… no longer has to be
    // said by the button that is already gone grey.
    TujiStatusBlocker(
        visible = state.deleting,
        title = stringResource(R.string.manage_deleting),
        detail = stringResource(R.string.manage_deleting_detail),
        kind = TujiStatusKind.Removing,
    )

    if (creating) {
        CreateCollectionSheet(
            state = collections,
            onCreate = { title, description -> onCreateCollection(title, description) { creating = false } },
            onDismiss = { creating = false; onDismissCreateError() },
        )
    }
    if (askDelete) {
        val batch = state.selected
        TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.manage_delete_selected_title),
            message = stringResource(R.string.manage_delete_selected_message),
            detail = state.selectionWarning.detail(batch = true),
            confirm = stringResource(R.string.manage_delete),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askDelete = false; onDelete(batch) },
            onCancel = { askDelete = false },
        )
    }
}

@Composable
private fun CardRow(row: ShelfRow, selecting: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (selecting && selected) TujiColor.Paper2 else TujiColor.Paper)
            .tujiClickable(onClick = onClick),
    ) {
        // Selection is a 3dp leading edge, not a circle: circles are kept for
        // the things that speak — avatars, dots, the cat's bubble.
        Box(Modifier.width(TujiBorder.Bw3).fillMaxHeight().background(if (selecting && selected) TujiColor.Current else TujiColor.Paper.copy(alpha = 0f)))
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).background(TujiColor.Paper2)) {
                AsyncImage(model = row.image.thumbUrl ?: row.image.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title(), style = TujiType.bodySmStrong, color = TujiColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.item?.displayZhHant?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = TujiType.label, color = TujiColor.Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(row.imageStatus.label(row.image.status), style = TujiType.label, color = TujiColor.Ink3)
        }
    }
}

/** 圖鑑卡片 or 合集. */
enum class ManageSection { Cards, Collections }

/** Not an empty state: "nothing here" and "your cards are on the other side" are different sentences. */
@Composable
private fun HiddenRow(count: Int, language: TargetLanguage) {
    val other = stringResource(if (language == TargetLanguage.JA) R.string.manage_atlas_en else R.string.manage_atlas_ja)
    Text(
        stringResource(R.string.manage_hidden, count, other),
        style = TujiType.bodySm,
        color = TujiColor.Ink2,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).background(TujiColor.Paper2).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
    )
}

@Composable
internal fun ShelfRow.title(): String = item?.lemma?.takeIf { it.isNotBlank() } ?: stringResource(
    when {
        imageStatus == null -> R.string.manage_placeholder_unfinished
        imageStatus!!.impliesItem -> R.string.manage_placeholder_unsynced
        else -> imageStatus!!.labelRes()
    },
)

@Composable
internal fun ImageStatus?.label(raw: String?): String = this?.let { stringResource(it.labelRes()) } ?: raw.orEmpty()

private fun ImageStatus.labelRes(): Int = when (this) {
    ImageStatus.Uploaded -> R.string.manage_status_uploaded
    ImageStatus.Processing -> R.string.manage_status_processing
    ImageStatus.NeedsReview -> R.string.manage_status_needs_review
    ImageStatus.Confirmed -> R.string.manage_status_confirmed
    ImageStatus.CardsReady -> R.string.manage_status_ready
    ImageStatus.Failed -> R.string.manage_status_failed
    ImageStatus.Deleted -> R.string.manage_status_deleted
}

@Composable
internal fun ReviewStatus.label(): String = stringResource(
    when (this) {
        ReviewStatus.Draft -> R.string.manage_review_draft
        ReviewStatus.Pending -> R.string.manage_review_pending
        ReviewStatus.Approved -> R.string.manage_review_approved
        ReviewStatus.Rejected -> R.string.manage_review_rejected
        ReviewStatus.Takedown -> R.string.manage_review_takedown
        ReviewStatus.Withdrawn -> R.string.manage_review_withdrawn
    },
)

/** The sentence a warning adds under the prompt — the last thing read before an irreversible button. */
@Composable
internal fun DeleteWarning.detail(batch: Boolean): String? = when (this) {
    DeleteWarning.PrivateOnly -> null
    DeleteWarning.CancelsReview -> stringResource(if (batch) R.string.manage_warn_review_batch else R.string.manage_warn_review)
    DeleteWarning.TakesDownFromPublic -> stringResource(if (batch) R.string.manage_warn_public_batch else R.string.manage_warn_public)
}
