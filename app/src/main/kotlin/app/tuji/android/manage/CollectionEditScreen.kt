package app.tuji.android.manage

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.community.MemberBadge
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSkeletonRows
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasCollectionMember
import app.tuji.android.profile.AvatarIntake
import app.tuji.android.profile.CropMask
import app.tuji.android.profile.FormInput
import app.tuji.android.profile.PhotoCodec
import coil3.compose.AsyncImage

/**
 * 編輯合集 — iOS's `AtlasCollectionEditView`: the avatar, the words, what is in
 * it, and whether it is public. One thing iOS keeps in a swipe is here as a
 * line at the foot — 刪除 — because a row that hides its only way out behind a
 * gesture nobody on Android expects is a row that cannot be deleted.
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

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(onLeading = onBack, leadingLabel = stringResource(R.string.atlas_back))
        TujiScreenTitle(stringResource(R.string.collections_edit_title))
        val collection = state.collection
        when {
            collection != null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S5),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
            ) {
                // 合集頭像
                Column(
                    Modifier.fillMaxWidth().background(TujiColor.Paper).padding(vertical = TujiSpace.S3),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                ) {
                    Text(stringResource(R.string.collections_avatar), style = TujiType.bodySmStrong, color = TujiColor.Ink)
                    Column(
                        Modifier.tujiClickable(enabled = !state.uploadingAvatar) { pickingAvatar = true },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    ) {
                        Box(Modifier.size(92.dp).background(TujiColor.Paper2), contentAlignment = Alignment.Center) {
                            if (state.avatarPreviewUrl != null) {
                                AsyncImage(model = state.avatarPreviewUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                TujiGlyph.Camera(size = 20.dp, tint = TujiColor.Ink3)
                            }
                            if (state.uploadingAvatar) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                    Text("…", style = TujiType.h2, color = Color.White)
                                }
                            }
                        }
                        Text(
                            stringResource(if (state.avatarPreviewUrl == null) R.string.collections_avatar_choose else R.string.collections_avatar_change),
                            style = TujiType.label,
                            color = TujiColor.BrandSecondary,
                        )
                    }
                    Text(stringResource(R.string.collections_avatar_hint), style = TujiType.label, color = TujiColor.Ink3)
                    if (state.failure == CollectionEditViewModel.Failure.Avatar) {
                        Text(stringResource(R.string.collections_avatar_failed), style = TujiType.label, color = TujiColor.Alert)
                    }
                }

                // 標題、簡介
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Label(stringResource(R.string.collections_field_title))
                    FormInput(value = state.title, onValueChange = onTitle, placeholder = stringResource(R.string.collections_field_title), valid = state.title.trim().length <= app.tuji.android.core.community.CollectionAuthoringRules.TITLE_MAX, enabled = true)
                    Spacer(Modifier.height(TujiSpace.S1))
                    Label(stringResource(R.string.collections_field_about))
                    FormInput(value = state.description, onValueChange = onDescription, placeholder = stringResource(R.string.collections_field_about_optional), valid = true, enabled = true, singleLine = false)
                    Row(Modifier.fillMaxWidth().padding(top = TujiSpace.S1), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.metaSaved) stringResource(R.string.collections_saved) else if (state.failure == CollectionEditViewModel.Failure.Save) stringResource(R.string.manage_action_failed) else "",
                            style = TujiType.label,
                            color = if (state.failure == CollectionEditViewModel.Failure.Save) TujiColor.Alert else TujiColor.Ink3,
                            modifier = Modifier.weight(1f),
                        )
                        SmallAction(
                            text = stringResource(if (state.savingMeta) R.string.profile_saving else R.string.profile_save),
                            enabled = state.canSaveMeta,
                            onClick = onSaveMeta,
                        )
                    }
                }

                // 項目
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.collections_items_count, state.members.size), style = TujiType.bodySmStrong, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                        Row(
                            Modifier.tujiClickable { picking = true; onOpenPicker() }.padding(vertical = TujiSpace.S1),
                            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TujiGlyph.Plus(size = 12.dp, tint = TujiColor.BrandSecondary)
                            Text(stringResource(R.string.collections_add), style = TujiType.bodySmStrong, color = TujiColor.BrandSecondary)
                        }
                    }
                    if (state.failure == CollectionEditViewModel.Failure.Member) {
                        Text(stringResource(R.string.collections_add_failed), style = TujiType.label, color = TujiColor.Alert)
                    }
                    if (state.members.isEmpty()) {
                        Text(stringResource(R.string.collections_items_empty), style = TujiType.label, color = TujiColor.Ink3)
                    } else {
                        state.members.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                                row.forEach { member -> MemberCell(member, onRemove = { onRemove(member.id) }, modifier = Modifier.weight(1f)) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // 公開狀態
                Column(Modifier.padding(top = TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.manage_field_visibility), style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.weight(1f))
                        Text(state.review.label(), style = TujiType.label, color = TujiColor.Ink)
                    }
                    when (state.failure) {
                        CollectionEditViewModel.Failure.Submit, CollectionEditViewModel.Failure.Withdraw ->
                            Text(stringResource(R.string.manage_action_failed), style = TujiType.label, color = TujiColor.Alert)
                        else -> Unit
                    }
                    state.published?.let {
                        Text(
                            stringResource(if (it) R.string.collections_published_now else R.string.collections_submitted),
                            style = TujiType.label,
                            color = TujiColor.Ink3,
                        )
                    }
                    if (state.review.canSubmit) {
                        WideAction(
                            text = stringResource(if (state.submitting) R.string.collections_submitting else R.string.collections_publish),
                            ground = TujiColor.BrandPrimary,
                            ink = TujiColor.Ink,
                            enabled = state.canSubmit,
                            onClick = { askSubmit = true },
                        )
                        if (state.members.isEmpty()) {
                            Text(stringResource(R.string.collections_publish_needs_item), style = TujiType.label, color = TujiColor.Ink3)
                        }
                    }
                    if (state.review.canWithdraw) {
                        WideAction(
                            text = stringResource(if (state.withdrawing) R.string.manage_withdrawing else R.string.manage_withdraw),
                            ground = TujiColor.Paper2,
                            ink = TujiColor.Ink,
                            enabled = state.canWithdraw,
                            onClick = { askWithdraw = true },
                        )
                    }
                }

                Text(
                    stringResource(R.string.collections_delete),
                    style = TujiType.label,
                    color = TujiColor.Alert,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TujiSpace.S4)
                        .tujiClickable(enabled = !deleting) { askDelete = true }
                        .padding(vertical = TujiSpace.S3),
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
        ItemPicker(state = state, onAdd = onAdd, onDismiss = { picking = false })
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
}

@Composable
private fun MemberCell(member: AtlasCollectionMember, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(84.dp).background(TujiColor.Paper2)) {
            AsyncImage(model = member.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            MemberBadge.of(member)?.let { badge ->
                Text(
                    stringResource(if (badge == MemberBadge.WithCollection) R.string.collections_member_with_collection else R.string.manage_review_pending),
                    style = TujiType.label,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 5.dp, vertical = 3.dp),
                )
            }
            val remove = stringResource(R.string.collections_remove_item)
            Box(
                Modifier.align(Alignment.TopEnd).size(32.dp).semantics { contentDescription = remove }.tujiClickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(20.dp).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    TujiGlyph.Close(size = 12.dp, tint = Color.White)
                }
            }
        }
        Text(member.lemma, style = TujiType.label, color = TujiColor.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
                        Text(stringResource(R.string.collections_add_failed), style = TujiType.label, color = TujiColor.Alert)
                    }
                    available.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                            row.forEach { item ->
                                val added = item.id in state.added
                                Column(
                                    Modifier.weight(1f).tujiClickable(enabled = !added) { onAdd(item.id) },
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Box(Modifier.fillMaxWidth().height(84.dp).background(TujiColor.Paper2)) {
                                        AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(if (added) 0.5f else 1f))
                                        if (added) {
                                            Box(Modifier.align(Alignment.Center).size(28.dp).background(TujiColor.Accumulation), contentAlignment = Alignment.Center) {
                                                TujiGlyph.Check(size = 14.dp, tint = Color.White)
                                            }
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

@Composable
private fun WideAction(text: String, ground: Color, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.h3,
        color = ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f).background(ground).tujiClickable(enabled = enabled, onClick = onClick).padding(vertical = TujiSpace.S3),
    )
}
