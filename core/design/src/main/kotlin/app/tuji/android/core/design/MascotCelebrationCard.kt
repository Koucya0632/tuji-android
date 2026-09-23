package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The 慶祝 surface: an ink block with the cat standing on its top edge.
 *
 * Ported from `Tuji/Components/MascotSurfaces.swift`. Both study flows end on
 * one — 學新字 with what was learned, 複習 with how many words went past.
 *
 * **The block is always ink.** iOS made that optional once and regretted it in
 * its own comment: on pale yellow a black cat is a cat on a card, the
 * silhouette collapses and only the eyes survive. Two callers passed `dark:
 * true` and two didn't, so the app shipped both versions of its most
 * recognisable image.
 *
 * The cat straddles the top edge rather than sitting inside the block — the
 * same move the mark makes on 今日's card, and the reason the block is pushed
 * down by [lift] instead of the cat being pushed up.
 */
@Composable
fun MascotCelebrationCard(
    title: String,
    modifier: Modifier = Modifier,
    pose: MascotPose = MascotPose.Cheer,
    detail: @Composable ColumnScope.() -> Unit = {},
) {
    val figureSize = 88.dp
    val overlap = 20.dp
    val lift = (figureSize * pose.visibleHeightRatio - overlap).coerceAtLeast(0.dp)

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = lift)
                .background(TujiColor.Ink)
                .padding(horizontal = TujiSpace.S4)
                .padding(top = overlap + TujiSpace.S4, bottom = TujiSpace.S4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Text(
                title,
                style = TujiType.h2,
                color = TujiColor.Paper,
                textAlign = TextAlign.Center,
            )
            detail()
        }
        MascotFigure(pose = pose, size = figureSize, glow = true)
    }
}
