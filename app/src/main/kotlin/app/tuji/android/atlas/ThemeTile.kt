package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.study.ThemeStatus

/**
 * A theme, drawn as a tile: its name, its word count, and whether it is done.
 *
 * Two screens draw it — 主題's index and 今日's strip — as iOS's `CategoryTile`
 * is shared between the same two. They were two private copies once, and the
 * strip's copy had no status at all: a theme finished in 主題 looked unstarted
 * on 今日.
 */
@Composable
fun ThemeTile(
    shelf: CategoryShelf.Shelf,
    status: ThemeStatus,
    uiLang: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The edge carries the claim, so a finished theme is legible in a grid
    // without reading any of them: 墨 for 全精通, 積累 for 完成, paper otherwise.
    val edge = when (status) {
        ThemeStatus.Mastered -> TujiColor.Ink
        ThemeStatus.Completed -> TujiColor.Accumulation
        ThemeStatus.None -> TujiColor.Paper3
    }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                .border(if (status == ThemeStatus.None) 1.dp else 2.dp, edge)
                .tujiClickable(onClick = onClick)
                .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                CategoryShelf.title(shelf.category, uiLang),
                style = TujiType.bodySmStrong,
                color = TujiColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.atlas_count, shelf.count),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
        }
        if (status != ThemeStatus.None) {
            Text(
                stringResource(if (status == ThemeStatus.Mastered) R.string.theme_mastered else R.string.theme_completed),
                style = TujiType.label,
                color = if (status == ThemeStatus.Mastered) TujiColor.Current else TujiColor.Paper,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(edge)
                    .padding(horizontal = TujiSpace.S1, vertical = 2.dp),
            )
        }
    }
}
