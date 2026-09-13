package app.tuji.android.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRow
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSection
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * 已封鎖的人 — iOS's `BlockedAuthorsView`, and the one place a block can be
 * undone without finding the person again, which is exactly what blocking them
 * made hard.
 *
 * Deliberately only UIDs. Their name and avatar would put the face someone
 * chose to stop seeing back on screen, in the list opened to manage that.
 */
@Composable
fun BlockedAuthorsScreen(
    handles: List<String>,
    /** The one being unblocked right now; the others wait. */
    working: String?,
    onUnblock: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TujiScreenTitle(stringResource(R.string.blocked_title))
        if (handles.isEmpty()) {
            Text(
                stringResource(R.string.blocked_empty),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            )
        } else {
            TujiSection(footer = stringResource(R.string.blocked_footer)) {
                handles.forEachIndexed { index, handle ->
                    if (index > 0) TujiRowDivider()
                    TujiRow(
                        trailing = {
                            Text(
                                stringResource(if (working == handle) R.string.blocked_unblocking else R.string.block_undo),
                                style = TujiType.label,
                                color = TujiColor.BrandSecondary,
                                modifier = Modifier
                                    .tujiClickable(enabled = working == null) { onUnblock(handle) }
                                    .padding(vertical = TujiSpace.S2),
                            )
                        },
                    ) {
                        Text(handle.uppercase(), style = TujiType.monoLabel, color = TujiColor.Ink)
                    }
                }
            }
        }
    }
}
