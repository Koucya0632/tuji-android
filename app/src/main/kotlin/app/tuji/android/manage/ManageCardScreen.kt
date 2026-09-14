package app.tuji.android.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.community.ShelfRow
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import coil3.compose.AsyncImage

/**
 * One card, read-only — iOS's `AtlasManageDetailView`. What it is, where it
 * stands in review, and the two ways out: 取消公開, which keeps everything, and
 * 刪除, which keeps nothing.
 */
@Composable
fun ManageCardScreen(
    row: ShelfRow?,
    withdrawing: Boolean,
    actionFailed: Boolean,
    onDelete: () -> Unit,
    onWithdraw: (String) -> Unit,
) {
    if (row == null) {
        // Deleted from under the page, or a sync that dropped it.
        Text(stringResource(R.string.manage_card_gone), style = TujiType.bodySm, color = TujiColor.Ink3, modifier = Modifier.padding(TujiSpace.S4))
        return
    }
    var askDelete by remember { mutableStateOf(false) }
    var askWithdraw by remember { mutableStateOf(false) }
    val item = row.item

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(TujiColor.Paper)) {
            AsyncImage(model = row.image.imageUrl ?: row.image.thumbUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (item != null) {
            Field(stringResource(R.string.manage_field_name), item.lemma)
            item.displayZhHant?.takeIf { it.isNotBlank() }?.let { Field(stringResource(R.string.manage_field_zh), it) }
            item.fineLabel?.takeIf { it.isNotBlank() }?.let { Field(stringResource(R.string.manage_field_fine), it) }
            item.partOfSpeech?.takeIf { it.isNotBlank() }?.let { Field(stringResource(R.string.manage_field_pos), it) }
            item.category?.takeIf { it.isNotBlank() }?.let { Field(stringResource(R.string.manage_field_category), it) }
        } else {
            Text(stringResource(R.string.manage_no_card), style = TujiType.bodySm, color = TujiColor.Ink3)
        }
        Field(stringResource(R.string.manage_field_status), row.imageStatus.label(row.image.status))

        val review = row.review
        if (item != null && review != null) {
            Column(Modifier.padding(top = TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Field(stringResource(R.string.manage_field_visibility), review.label())
                if (actionFailed) {
                    Text(stringResource(R.string.manage_action_failed), style = TujiType.label, color = TujiColor.Alert)
                }
                // Secondary, not red: withdrawing is reversible and carries no
                // penalty — it is the path that *avoids* deleting a card.
                if (review.canWithdraw) {
                    ActionBar(
                        text = stringResource(if (withdrawing) R.string.manage_withdrawing else R.string.manage_withdraw),
                        ground = TujiColor.Paper2,
                        ink = TujiColor.Ink,
                        enabled = !withdrawing,
                        onClick = { askWithdraw = true },
                    )
                }
            }
        }

        ActionBar(
            text = stringResource(R.string.manage_delete_card),
            ground = TujiColor.Alert,
            ink = TujiColor.Paper,
            enabled = true,
            onClick = { askDelete = true },
            modifier = Modifier.padding(top = TujiSpace.S2),
        )
        Spacer(Modifier.height(TujiSpace.S5))
    }

    if (askDelete) {
        val warning = row.warning
        TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.manage_delete_title),
            message = stringResource(R.string.manage_delete_message),
            detail = warning.detail(batch = false),
            confirm = stringResource(R.string.manage_delete),
            cancel = stringResource(R.string.cancel),
            // Only where deleting would reach other accounts: 取消公開 does
            // everything asked for and none of what was not.
            alternative = if (warning == DeleteWarning.TakesDownFromPublic) stringResource(R.string.manage_withdraw_instead) else null,
            onAlternative = { askDelete = false; item?.let { onWithdraw(it.id) } },
            onConfirm = { askDelete = false; onDelete() },
            onCancel = { askDelete = false },
        )
    }
    if (askWithdraw && item != null) {
        TujiPrompt(
            title = stringResource(R.string.manage_withdraw_title),
            message = stringResource(R.string.manage_withdraw_message),
            detail = stringResource(R.string.manage_withdraw_detail),
            confirm = stringResource(R.string.manage_withdraw),
            cancel = stringResource(R.string.manage_not_now),
            onConfirm = { askWithdraw = false; onWithdraw(item.id) },
            onCancel = { askWithdraw = false },
        )
    }
}

@Composable
private fun Field(title: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = TujiType.label, color = TujiColor.Ink3)
        Text(value, style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}

@Composable
private fun ActionBar(
    text: String,
    ground: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = TujiType.h3,
        color = ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(ground)
            .alpha(if (enabled) 1f else 0.6f)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = TujiSpace.S3),
    )
}
