package app.tuji.android.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.core.community.BlockAction
import app.tuji.android.core.design.TujiPrompt

/**
 * The words for [BlockAction], once — iOS's `BlockFlow`.
 *
 * 物見單品 and 作者主頁 both offer 封鎖, and iOS had them drift: one said
 * 「已封鎖這位作者」, a string that existed nowhere else, and could not be
 * undone from there. Both screens print these.
 */
@Composable
internal fun blockControlLabel(blocked: Boolean): String = stringResource(
    when (BlockAction.of(blocked)) {
        BlockAction.Block -> R.string.block_control
        BlockAction.Unblock -> R.string.block_undo
    },
)

/**
 * 封鎖 is reversible and leaves what the reader already took in alone, and the
 * prompt says so — the difference between a decision and a dead end.
 */
@Composable
internal fun BlockPrompt(blocked: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val action = BlockAction.of(blocked)
    TujiPrompt(
        title = stringResource(if (action == BlockAction.Block) R.string.block_title else R.string.unblock_title),
        message = null,
        detail = stringResource(if (action == BlockAction.Block) R.string.block_detail else R.string.unblock_detail),
        confirm = stringResource(if (action == BlockAction.Block) R.string.block_confirm else R.string.block_undo),
        cancel = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}
