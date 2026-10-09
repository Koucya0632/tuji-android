package app.tuji.android.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable

/**
 * iOS's `TujiSheetShell`: a sheet of paper pushed in from below. Square
 * corners, no grabber, a 3dp ink edge along the top, the title on the left and
 * ✕ on the right — the same head [TujiFormSheet] wears, so a choice and a form
 * read as the same family.
 *
 * Hand-rolled rather than Material's `ModalBottomSheet`, whose rounded top and
 * drag handle are exactly the system fingerprint iOS's shell removes.
 *
 * [maxHeight] stands in for iOS's detent height: the sheet wraps its content
 * up to there. The content lays out its own padding and scrolling — a list of
 * rows runs edge to edge, a form does not.
 *
 * **A window of its own** — see `TujiWindow`.
 */
@Composable
internal fun TujiSheet(
    title: String,
    onDismiss: () -> Unit,
    closeEnabled: Boolean = true,
    maxHeight: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val close = { if (closeEnabled) onDismiss() }
    TujiWindow(onDismiss = close) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TujiColor.Scrim)
                .tujiClickable(onClick = close),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(TujiColor.Paper)
                    // Swallows taps so a tap on the sheet does not dismiss it
                    // through the scrim underneath.
                    .tujiClickable {}
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                TujiSheetHeader(title = title, closeEnabled = closeEnabled, onClose = close)
                content()
            }
        }
    }
}

/**
 * iOS's `TujiSheetHeader`: the ink edge, then the title and ✕. No centred
 * title, no 完成, no 取消 on the left — those together are the system sheet's
 * toolbar.
 */
@Composable
internal fun TujiSheetHeader(title: String, closeEnabled: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(TujiBorder.Bw3).background(TujiColor.Ink))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TujiType.h2, color = TujiColor.Ink, maxLines = 1, modifier = Modifier.weight(1f))
        val closeLabel = stringResource(R.string.form_close)
        Box(
            Modifier
                .offset(x = TujiSpace.S3)
                .size(48.dp)
                .semantics { contentDescription = closeLabel }
                .tujiClickable(enabled = closeEnabled, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Close(size = 18.dp, tint = if (closeEnabled) TujiColor.Ink2 else TujiColor.Ink3)
        }
    }
}
