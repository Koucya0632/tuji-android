package app.tuji.android.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusEdgeLabel
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.CaptureFailure
import app.tuji.android.core.model.CaptureProgress
import coil3.compose.AsyncImage

/**
 * A card being made, drawn in the 圖鑑 grid alongside the finished ones.
 *
 * A tile rather than a banner. The obvious shape for "work in progress" is a
 * strip pinned above the grid, and that shape says *notification about a card*
 * — while the thing it is announcing is a card. A tile says the same thing with
 * no new component: this is where your word will be, and it is not ready yet.
 * It also costs no permanent band of space at the top of the tab.
 *
 * Owns none of its copy: 圖鑑管理 says the same words about the same photo, and
 * two screens deriving them separately is how one ends up saying 生成中 while
 * the other says 已上傳.
 */
@Composable
fun CaptureQueueTile(job: AtlasCaptureQueue.Item, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(
            // 紙3, a step deeper than the finished tiles' 紙2: the slot is
            // occupied but not yet filled, and the depth says so without a
            // spinner or a shimmer.
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(TujiColor.Paper3)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            job.thumbUrl?.let {
                // The frame the user just took, held back so the tile reads as
                // "your photo, being worked on" rather than as an empty box
                // with their word under it.
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.35f),
                )
            }
            val fraction = job.progress.fraction
            when {
                fraction != null -> TujiProgressBar(
                    progress = fraction.toDouble(),
                    track = TujiColor.Paper,
                    fill = TujiColor.Current,
                    modifier = Modifier.padding(horizontal = TujiSpace.S4),
                )
                job.progress.canRetry -> TujiGlyph.Refresh(size = 26.dp, tint = TujiColor.Ink2)
                // A capacity dead end. An arrow here would be an invitation to
                // do the one thing that cannot work.
                else -> TujiGlyph.Warning(size = 26.dp, tint = TujiColor.Ink2)
            }
        }
        Text(
            job.lemma,
            style = TujiType.h3,
            color = TujiColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = TujiSpace.S2),
        )
        TujiStatusEdgeLabel(
            text = job.progress.label(),
            edge = job.progress.edge,
            modifier = Modifier.padding(top = TujiSpace.S1),
        )
    }
}

/** 警示 for a failure, 積累 for a card that landed, 瞳黃 while it is working. */
private val CaptureProgress.edge: Color
    get() = when (this) {
        is CaptureProgress.Failed -> TujiColor.Alert
        CaptureProgress.Ready -> TujiColor.Accumulation
        else -> TujiColor.Current
    }

/**
 * What a screen says about this capture.
 *
 * One home for the copy — iOS keeps it on `CaptureProgress` itself, which
 * cannot work here because `core:model` has no resources to reach. Same rule,
 * one file away: the grid tile and 圖鑑管理 cannot disagree about one photo.
 */
@Composable
fun CaptureProgress.label(): String = when (this) {
    is CaptureProgress.Generating -> stringResource(R.string.capture_job_generating)
    is CaptureProgress.Enriching -> stringResource(R.string.capture_job_enriching)
    CaptureProgress.Ready -> stringResource(R.string.capture_job_ready)
    is CaptureProgress.Failed -> when (val reason = failure) {
        is CaptureFailure.AtCapacity ->
            reason.message ?: stringResource(R.string.capture_job_at_capacity)
        CaptureFailure.Transient -> stringResource(R.string.capture_job_retry)
    }
}
