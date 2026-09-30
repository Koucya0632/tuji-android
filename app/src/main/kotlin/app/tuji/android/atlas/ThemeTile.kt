package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import app.tuji.android.core.design.accent
import app.tuji.android.core.design.frameWidth
import app.tuji.android.core.design.onAccent
import coil3.compose.AsyncImage
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
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                .border(status.frameWidth, status.accent)
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
        ThemeStatusBadge(status, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * 封面主題磚 — 圖鑑's 官方 shelf.
 *
 * A separate composable rather than a flag on [ThemeTile], which is the split
 * iOS makes and for its reason: 今日's strip wants the opposite thing — two
 * rows of themes in the height one cover gives — and a tile that draws a cover
 * only sometimes would owe both callers an explanation.
 *
 * **The container owns the shape**, the way [app.tuji.android.core.design.WordTile]'s
 * square does: a 16:9 box measured from the cell, with the artwork filling it
 * from inside. Asking the picture for the ratio instead lets a 1280-wide cover
 * negotiate its own size and push the grid apart. 16:9 because that is the crop
 * 主題's hero shows, so a theme looks like itself on both screens.
 */
@Composable
fun ThemeCoverTile(
    shelf: CategoryShelf.Shelf,
    status: ThemeStatus,
    uiLang: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                .border(status.frameWidth, status.accent)
                .tujiClickable(onClick = onClick),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(TujiColor.Paper2)) {
                // A theme with no cover (`imageUrl` is an empty string for a
                // few of them) gets paper, not a broken-image glyph.
                if (!shelf.category.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = shelf.category.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S3),
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
        }
        ThemeStatusBadge(status, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * The corner marker: 完成 once every word has been seen, 全精通 once every word
 * reaches 精通. Nothing at all for a theme that has neither.
 */
@Composable
private fun ThemeStatusBadge(status: ThemeStatus, modifier: Modifier = Modifier) {
    if (status == ThemeStatus.None) return
    Text(
        stringResource(if (status == ThemeStatus.Mastered) R.string.theme_mastered else R.string.theme_completed),
        style = TujiType.label,
        color = status.onAccent,
        modifier = modifier
            .padding(5.dp)
            .background(status.accent)
            .padding(horizontal = TujiSpace.S1, vertical = 2.dp),
    )
}
