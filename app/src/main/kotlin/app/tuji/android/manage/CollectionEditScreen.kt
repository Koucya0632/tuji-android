package app.tuji.android.manage

import androidx.compose.ui.semantics.stateDescription
import app.tuji.android.core.community.PickerBadge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.community.CollectionIdentityTile
import app.tuji.android.core.community.CollectionAuthoringRules
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.community.MemberBadge
import app.tuji.android.core.community.ReviewStatus
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSkeletonRows
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusBlocker
import app.tuji.android.core.design.TujiStatusEdgeLabel
import app.tuji.android.core.design.TujiStatusKind
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasCollectionMember
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.profile.AvatarIntake
import app.tuji.android.profile.CropMask
import app.tuji.android.profile.FormInput
import app.tuji.android.profile.PhotoCodec
import coil3.compose.AsyncImage

/**
 * 編輯合集 — iOS's `AtlasCollectionEditView`: the avatar, the words, what is in
 * it, and whether it is public.
 *
 * Two save models on one screen, as on iOS: the avatar and the cards are
 * written the moment they change, the 標題 and 簡介 wait for 儲存. So 儲存
 * sits in the bar, lit only when the words differ from the server, and
 * leaving with unsaved words asks first.
 */
@Composable
fun CollectionEditScreen(
    state: CollectionEditViewModel.State,
    deleteWarning: DeleteWarning,
    deleting: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onSaveMeta: () -> Unit,
    onSaveThenLeave: () -> Unit,
    onAvatar: (ByteArray) -> Unit,
    onOpenPicker: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSubmit: () -> Unit,
    onWithdraw: () -> Unit,
    onDelete: () -> Unit,
) {
    var pickingAvatar by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var askSubmit by remember { mutableStateOf(false) }
    var askWithdraw by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var showsAllMembers by rememberSaveable { mutableStateOf(false) }

    // Only the words are lost by leaving — the avatar and the cards are already
    // on the server — so only they are asked about.
    val leave = { if (state.isMetaDirty) askDiscard = true else onBack() }
    BackHandler(enabled = state.isMetaDirty && !deleting) { askDiscard = true }

    // Deleting a public 合集 takes it away from everyone who saved it, so the
    // page stops answering while it happens — including the back arrow, which
    // otherwise left the screen mid-delete and came back to a list that had not
    // caught up yet.
    TujiStatusBlocker(
        visible = deleting,
        title = stringResource(R.string.manage_deleting),
        detail = stringResource(R.string.manage_deleting_detail),
        kind = TujiStatusKind.Removing,
    )

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = leave,
            leadingLabel = stringResource(R.string.atlas_back),
            trailing = if (state.collection != null) {
                {
                    Text(
                        stringResource(if (state.savingMeta) R.string.profile_saving else R.string.profile_save),
                        style = TujiType.bodyStrong,
                        color = if (state.canSaveMeta) TujiColor.Ink else TujiColor.Ink3,
                        modifier = Modifier
                            .tujiClickable(enabled = state.canSaveMeta, onClick = onSaveMeta)
                            .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
                    )
                }
            } else {
                null
            },
        )
        TujiScreenTitle(stringResource(R.string.collections_edit_title))
        val collection = state.collection
        when {
            collection != null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                AvatarSection(state, onPick = { pickingAvatar = true })
                MetaSection(state, onTitle, onDescription)
                MemberList(
                    members = state.members,
                    failed = state.failure == CollectionEditViewModel.Failure.Member,
                    showsAll = showsAllMembers,
                    onToggleAll = { showsAllMembers = !showsAllMembers },
                    onAdd = { picking = true; onOpenPicker() },
                    onRemove = onRemove,
                )
                SubmitSection(
                    state = state,
                    deleting = deleting,
                    onSubmit = { askSubmit = true },
                    onWithdraw = { askWithdraw = true },
                    onDelete = { askDelete = true },
                )
            }
            state.loading -> TujiPageLoading(label = stringResource(R.string.atlas_loading))
            else -> Column(Modifier.padding(TujiSpace.S4), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(stringResource(R.string.atlas_failed), style = TujiType.bodySm, color = TujiColor.Ink3)
                SmallAction(text = stringResource(R.string.retry), enabled = true, onClick = onRetry)
            }
        }
    }

    if (pickingAvatar) {
        AvatarIntake(
            hasCustomAvatar = false,
            onImage = onAvatar,
            onUseDefault = {},
            onClose = { pickingAvatar = false },
            title = stringResource(R.string.collections_avatar_change_title),
            mask = CropMask.Square,
            encode = PhotoCodec::collectionJpeg,
        )
    }
    if (picking) {
        // A new card lands at the end of the roster, so a collapsed list would
        // answer an explicit 加入 with no visible change.
        ItemPicker(state = state, onAdd = { showsAllMembers = true; onAdd(it) }, onDismiss = { picking = false })
    }
    if (askSubmit) {
        TujiPrompt(
            title = stringResource(R.string.collections_publish_title),
            message = if (state.unpublishedCount > 0) {
                stringResource(R.string.collections_publish_message_items, state.unpublishedCount)
            } else {
                stringResource(R.string.collections_publish_message)
            },
            detail = stringResource(R.string.collections_publish_detail),
            confirm = stringResource(R.string.collections_publish_confirm),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askSubmit = false; onSubmit() },
            onCancel = { askSubmit = false },
        )
    }
    if (askWithdraw) {
        TujiPrompt(
            title = stringResource(R.string.collections_withdraw_title),
            message = stringResource(R.string.collections_withdraw_message),
            detail = stringResource(R.string.manage_withdraw_detail),
            confirm = stringResource(R.string.manage_withdraw),
            cancel = stringResource(R.string.manage_not_now),
            onConfirm = { askWithdraw = false; onWithdraw() },
            onCancel = { askWithdraw = false },
        )
    }
    if (askDelete) {
        TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.collections_delete_title),
            message = stringResource(
                when (deleteWarning) {
                    DeleteWarning.CancelsReview -> R.string.collections_delete_review
                    DeleteWarning.TakesDownFromPublic -> R.string.collections_delete_public
                    DeleteWarning.PrivateOnly -> R.string.collections_delete_private
                },
            ),
            confirm = stringResource(R.string.manage_delete),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askDelete = false; onDelete() },
            onCancel = { askDelete = false },
        )
    }
    if (askDiscard) {
        // A blank title cannot be saved at all, so on that path the offer to
        // save would be a button that does nothing.
        val canSave = state.canSaveMeta
        TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.collections_discard_title),
            message = stringResource(R.string.collections_discard_message),
            confirm = stringResource(if (canSave) R.string.collections_save_and_leave else R.string.collections_discard),
            alternative = if (canSave) stringResource(R.string.collections_discard) else null,
            onAlternative = { askDiscard = false; onBack() },
            cancel = stringResource(R.string.cancel),
            onConfirm = {
                askDiscard = false
                if (canSave) onSaveThenLeave() else onBack()
            },
            onCancel = { askDiscard = false },
        )
    }
}

/**
 * One centred photograph, the way 編輯個人資料 does it. It draws the same
 * [CollectionIdentityTile] 物見 shows, including the generated colour when
 * there is no photo, so what the author sees here is what everyone sees.
 */
@Composable
private fun AvatarSection(state: CollectionEditViewModel.State, onPick: () -> Unit) {
    val collection = state.collection ?: return
    val change = stringResource(R.string.collections_avatar_change_title)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Label(stringResource(R.string.collections_avatar))
        Column(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = change }
                .tujiClickable(enabled = !state.uploadingAvatar, onClick = onPick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Box(Modifier.size(AVATAR_SIZE + 8.dp)) {
                CollectionIdentityTile(
                    AtlasPublicCollection(
                        id = collection.id,
                        slug = collection.slug ?: collection.id,
                        title = collection.title,
                        avatarColor = collection.avatarColor,
                        avatarImageUrl = state.avatarPreviewUrl,
                    ),
                    Modifier.size(AVATAR_SIZE).align(Alignment.TopStart),
                )
                if (state.uploadingAvatar) {
                    Box(
                        Modifier.size(AVATAR_SIZE).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TujiIndeterminateBar(Modifier.width(56.dp), track = Color.White.copy(alpha = 0.3f), fill = Color.White)
                    }
                } else {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(34.dp)
                            .background(TujiColor.Paper, CircleShape)
                            .padding(3.dp)
                            .background(TujiColor.BrandPrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        TujiGlyph.Camera(size = 14.dp, tint = TujiColor.Ink)
                    }
                }
            }
            Text(stringResource(R.string.collections_avatar_tap), style = TujiType.label, color = TujiColor.Ink3)
        }
        if (state.failure == CollectionEditViewModel.Failure.Avatar) {
            Text(stringResource(R.string.collections_avatar_failed), style = TujiType.label, color = TujiColor.Alert)
        }
        Text(stringResource(R.string.collections_avatar_hint), style = TujiType.label, color = TujiColor.Ink3)
    }
}

@Composable
private fun MetaSection(state: CollectionEditViewModel.State, onTitle: (String) -> Unit, onDescription: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Label(stringResource(R.string.collections_field_title))
        FormInput(
            value = state.title,
            onValueChange = onTitle,
            placeholder = stringResource(R.string.collections_title_placeholder),
            valid = state.title.trim().length <= CollectionAuthoringRules.TITLE_MAX,
            enabled = true,
        )
        Spacer(Modifier.height(TujiSpace.S2))
        Label(stringResource(R.string.collections_field_about_optional))
        FormInput(
            value = state.description,
            onValueChange = onDescription,
            placeholder = stringResource(R.string.collections_about_placeholder),
            valid = true,
            enabled = true,
            singleLine = false,
        )
        // 已儲存 only after a save, and gone the moment the fields differ from
        // the server again — the same fact 儲存 lights up for.
        when {
            state.failure == CollectionEditViewModel.Failure.Save ->
                Text(stringResource(R.string.manage_action_failed), style = TujiType.label, color = TujiColor.Alert)
            state.metaSaved && !state.isMetaDirty ->
                TujiStatusEdgeLabel(text = stringResource(R.string.collections_saved), edge = TujiColor.Accumulation)
        }
    }
}

/**
 * The roster: one row per card, with room for the whole name, its own state and
 * a 44dp 移除 — iOS's `CollectionMemberList`. Five rows, then one tap for the
 * rest, so a long 合集 does not push 公開合集 thousands of dp down.
 */
@Composable
private fun MemberList(
    members: List<AtlasCollectionMember>,
    failed: Boolean,
    showsAll: Boolean,
    onToggleAll: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val collapsible = members.size > COLLAPSED_MEMBERS
    val visible = if (collapsible && !showsAll) members.take(COLLAPSED_MEMBERS) else members
    Column(Modifier.fillMaxWidth().padding(top = TujiSpace.S5)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.collections_items_count, members.size),
                style = TujiType.label,
                color = TujiColor.Ink3,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.height(44.dp).tujiClickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(32.dp).background(TujiColor.Paper2).padding(horizontal = TujiSpace.S3),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TujiGlyph.Plus(size = 12.dp, tint = TujiColor.Ink)
                    Text(stringResource(R.string.collections_add), style = TujiType.label, color = TujiColor.Ink)
                }
            }
        }
        // 加入/移除 refusals belong with these rows, not at the foot of the page.
        if (failed) {
            Text(
                stringResource(R.string.collections_add_failed),
                style = TujiType.label,
                color = TujiColor.Alert,
                modifier = Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S2),
            )
        }
        if (members.isEmpty()) {
            Text(
                stringResource(R.string.collections_items_empty),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            )
        } else {
            visible.forEachIndexed { index, member ->
                if (index > 0) TujiRowDivider()
                MemberRow(member, onRemove = { onRemove(member.id) })
            }
            if (collapsible) {
                TujiRowDivider()
                Row(
                    Modifier.fillMaxWidth().height(44.dp).tujiClickable(onClick = onToggleAll),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showsAll) stringResource(R.string.collections_show_fewer) else stringResource(R.string.collections_show_all, members.size),
                        style = TujiType.label,
                        color = TujiColor.Ink,
                    )
                    TujiGlyph.ChevronUp(
                        size = 11.dp,
                        tint = TujiColor.Ink,
                        modifier = if (showsAll) Modifier else Modifier.rotate(180f),
                    )
                }
            }
        }
    }
}

/** One card in the 合集: its photo, its whole name, its own state, and 移除. */
@Composable
private fun MemberRow(member: AtlasCollectionMember, onRemove: () -> Unit) {
    val remove = stringResource(R.string.collections_remove_item)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = TujiSpace.S4, end = TujiSpace.S1),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(TujiColor.Paper2)) {
            AsyncImage(model = member.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // The state sits under the name rather than at the trailing edge: the
        // name is what must not be cut.
        Column(
            Modifier.weight(1f).padding(vertical = TujiSpace.S2),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            Text(member.lemma, style = TujiType.bodySmStrong, color = TujiColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val badge = MemberBadge.of(member)
            when {
                member.publicationState == "public" ->
                    TujiStatusEdgeLabel(text = stringResource(R.string.manage_review_approved), edge = TujiColor.Accumulation)
                badge != null -> TujiStatusEdgeLabel(
                    text = stringResource(if (badge == MemberBadge.WithCollection) R.string.collections_member_with_collection else R.string.manage_review_pending),
                    edge = TujiColor.Current,
                )
            }
        }
        Box(
            Modifier.size(44.dp).semantics { contentDescription = remove }.tujiClickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Close(size = 15.dp, tint = TujiColor.Ink3)
        }
    }
}

@Composable
private fun SubmitSection(
    state: CollectionEditViewModel.State,
    deleting: Boolean,
    onSubmit: () -> Unit,
    onWithdraw: () -> Unit,
    onDelete: () -> Unit,
) {
    val review = state.review
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S5, bottom = TujiSpace.S6),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.manage_field_visibility), style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.weight(1f))
            TujiStatusEdgeLabel(text = review.label(), edge = review.edge())
        }
        when (state.failure) {
            CollectionEditViewModel.Failure.Submit, CollectionEditViewModel.Failure.Withdraw ->
                Text(stringResource(R.string.manage_action_failed), style = TujiType.bodySm, color = TujiColor.Alert)
            else -> Unit
        }
        // What the state means — most of all on the branches that offer no
        // button, where the one final state (下架) would otherwise look like the
        // one that is merely waiting.
        val note = when {
            state.published != null ->
                if (state.published) R.string.collections_published_now else R.string.collections_submitted
            review == ReviewStatus.Pending -> R.string.collections_submitted
            review == ReviewStatus.Rejected -> R.string.collections_note_rejected
            review == ReviewStatus.Takedown -> R.string.collections_note_takedown
            else -> null
        }
        note?.let { Text(stringResource(it), style = TujiType.bodySm, color = TujiColor.Ink3) }

        if (review.canSubmit) {
            WideAction(
                text = stringResource(if (state.submitting) R.string.collections_submitting else R.string.collections_publish),
                ground = TujiColor.BrandPrimary,
                ink = TujiColor.Ink,
                enabled = state.canSubmit,
                onClick = onSubmit,
                leading = { TujiGlyph.ArrowUp(size = 14.dp, tint = TujiColor.Ink) },
            )
            if (state.members.isEmpty()) {
                Text(stringResource(R.string.collections_publish_needs_item), style = TujiType.label, color = TujiColor.Ink3)
            }
        }
        // The quiet ground: 取消公開 is a utility, not this screen's call to action.
        if (review.canWithdraw) {
            WideAction(
                text = stringResource(if (state.withdrawing) R.string.manage_withdrawing else R.string.manage_withdraw),
                ground = TujiColor.Paper2,
                ink = TujiColor.Ink,
                enabled = state.canWithdraw,
                onClick = onWithdraw,
            )
        }
        // Last, and alone: the one thing on this screen that cannot be undone.
        WideAction(
            text = stringResource(R.string.collections_delete),
            ground = TujiColor.Alert,
            ink = Color.White,
            enabled = !deleting,
            onClick = onDelete,
            modifier = Modifier.padding(top = TujiSpace.S3),
            leading = { TujiGlyph.Trash(size = 14.dp, tint = Color.White) },
        )
    }
}

private val AVATAR_SIZE = 120.dp
private const val COLLAPSED_MEMBERS = 5

/** 加入項目 — iOS's `AtlasCollectionItemPicker`: this language's confirmed cards, three across, tap to add. */
@Composable
private fun ItemPicker(state: CollectionEditViewModel.State, onAdd: (String) -> Unit, onDismiss: () -> Unit) = TujiWindow(onDismiss = onDismiss) {
    // A window of its own draws under the status bar, so it pads for it.
    Column(Modifier.fillMaxSize().background(TujiColor.Paper).statusBarsPadding().navigationBarsPadding()) {
        TujiNavBar(onLeading = onDismiss, leading = app.tuji.android.core.design.TujiNavLeading.Close, leadingLabel = stringResource(R.string.nav_close), title = stringResource(R.string.collections_picker_title))
        val available = state.available
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TujiSpace.S3), verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            when {
                state.candidatesLoading ->
                    TujiSkeletonRows(count = 3, height = 72.dp, label = stringResource(R.string.atlas_loading))
                state.candidatesFailed -> Text(stringResource(R.string.atlas_failed), style = TujiType.bodySm, color = TujiColor.Ink3)
                available.isEmpty() -> Text(stringResource(R.string.collections_picker_empty), style = TujiType.bodySm, color = TujiColor.Ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = TujiSpace.S6))
                else -> {
                    if (state.failure == CollectionEditViewModel.Failure.Member) {
                        Text(
                            when (val refusal = state.addRefusal) {
                                CollectionEditViewModel.AddRefusal.AlreadyMember -> stringResource(R.string.collections_already_member)
                                is CollectionEditViewModel.AddRefusal.Said -> refusal.message
                                null -> stringResource(R.string.collections_add_failed)
                            },
                            style = TujiType.label,
                            color = TujiColor.Alert,
                        )
                    }
                    // A live or in-review 合集 takes a card that is not public
                    // yet, but cannot carry it through review: say what will happen.
                    if (CollectionAuthoringRules.submitsMembersOnTheirOwn(state.review)) {
                        Text(stringResource(R.string.collections_picker_note), style = TujiType.label, color = TujiColor.Ink3)
                    }
                    available.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                            row.forEach { item ->
                                val added = item.id in state.added
                                val badge = CollectionAuthoringRules.pickerBadge(state.review, item)
                                val hint = if (badge == PickerBadge.EntersReviewOnAdd) stringResource(R.string.collections_enters_review_hint) else null
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .alpha(if (added) 0.6f else 1f)
                                        .semantics { if (hint != null) stateDescription = hint }
                                        .tujiClickable(enabled = !added) { onAdd(item.id) },
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(Modifier.fillMaxWidth().height(84.dp).background(TujiColor.Paper2)) {
                                        AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        // ＋ until it is in, ✓ after — iOS's corner mark.
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(18.dp)
                                                .background(if (added) TujiColor.Accumulation else Color.Black.copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (added) TujiGlyph.Check(size = 10.dp, tint = Color.White) else TujiGlyph.Plus(size = 10.dp, tint = Color.White)
                                        }
                                        badge?.let {
                                            Text(
                                                stringResource(
                                                    when (it) {
                                                        PickerBadge.EntersReviewOnAdd -> R.string.collections_enters_review
                                                        PickerBadge.WithCollection -> R.string.collections_member_with_collection
                                                        PickerBadge.InReview -> R.string.manage_review_pending
                                                    },
                                                ),
                                                style = TujiType.label,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                                    .background(Color.Black.copy(alpha = 0.65f))
                                                    .padding(horizontal = 5.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                    Text(item.lemma, style = TujiType.label, color = TujiColor.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodyStrong,
        color = TujiColor.Ink,
        modifier = Modifier.alpha(if (enabled) 1f else 0.5f).background(TujiColor.Current).tujiClickable(enabled = enabled, onClick = onClick).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
    )
}

/** A full-width action on its own ground — the 合集 screens' version of iOS's `BBtn`. */
@Composable
internal fun WideAction(
    text: String,
    ground: Color,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .background(ground)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, style = TujiType.bodyStrong, color = ink, textAlign = TextAlign.Center)
    }
}
