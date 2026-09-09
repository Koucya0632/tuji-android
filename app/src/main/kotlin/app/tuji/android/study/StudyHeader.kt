package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * The bar every study session wears: 先離開, the unsynced count, and how far
 * along the session is.
 *
 * Named for what it is rather than for 複習, which is where it started — the
 * next flow that needs it has to be able to find it.
 */
@Composable
internal fun StudyHeader(progress: Double, unsynced: Int, onClose: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ✕ rather than the word 先離開: this is the one control on a study
            // screen that is not about the question, and a word beside a
            // picture of a thing competes with it for the same reading.
            val label = stringResource(R.string.study_close_label)
            Box(
                Modifier
                    .size(44.dp)
                    .semantics { contentDescription = label }
                    .tujiClickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Close(tint = TujiColor.Ink)
            }
            if (unsynced > 0) {
                // Said out loud rather than swallowed: these answers are on
                // disk and will replay, and a silent count is how iOS's
                // predecessor lost them.
                Text(
                    stringResource(R.string.study_unsynced, unsynced),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                )
            }
        }
        // 3dp, the selection width — a progress bar is a selection of how far
        // along the session is, and 紙與墨 has no other way to draw one.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TujiBorder.Bw3)
                .background(TujiColor.Paper3),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.toFloat())
                    .height(TujiBorder.Bw3)
                    .background(TujiColor.Current),
            )
        }
    }
}
