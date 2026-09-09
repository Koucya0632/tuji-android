package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.tuji.android.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * The bar every study session wears: 先離開, the unsynced count, which session
 * this is, and how far along it is.
 *
 * Named for what it is rather than for 複習, which is where it started — the
 * next flow that needs it has to be able to find it.
 *
 * [label] and [count] arrive as strings because the two flows count different
 * things — 複習 counts cards, 學新字 counts words that cleared a whole ladder —
 * and a header that tried to derive either would have to know about both.
 */
@Composable
internal fun StudyHeader(
    label: String,
    count: String?,
    progress: Double,
    unsynced: Int,
    onClose: () -> Unit,
) {
    Column(Modifier.padding(bottom = TujiSpace.S3)) {
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
            val closeLabel = stringResource(R.string.study_close_label)
            Box(
                Modifier
                    .size(44.dp)
                    .semantics { contentDescription = closeLabel }
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
        // Which session this is, and how much of it is left. 墨3 for the mode
        // and a *mono* count, so the digits hold their width — a proportional
        // 1 beside a 7 makes the number jitter as it climbs, which reads as
        // the layout moving rather than the count.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = TujiType.label, color = TujiColor.Ink3)
            if (count != null) {
                Text(count, style = TujiType.monoLabel, color = TujiColor.Ink2)
            }
        }
        Spacer(Modifier.height(TujiSpace.S2))
        TujiProgressBar(progress = progress)
    }
}
