package app.tuji.android.atlas

import androidx.compose.foundation.background
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.design.WordPicture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Word
import coil3.compose.AsyncImage

/**
 * 圖鑑 — the shelves.
 *
 * A grid of categories rather than one long list of 557 words: the catalogue is
 * organised by *where you would find the thing*, and that is the only ordering
 * the words themselves carry.
 */
@Composable
fun AtlasShelvesScreen(
    shelves: List<CategoryShelf.Shelf>,
    uiLang: String,
    loading: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
) {
    if (shelves.isEmpty()) {
        Centered(stringResource(if (loading) R.string.atlas_loading else R.string.atlas_failed))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = TujiSpace.S4, end = TujiSpace.S4,
            top = TujiSpace.S3, bottom = bottomPadding + TujiSpace.S6,
        ),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shelves, key = { it.category.id }) { shelf ->
            ShelfCard(shelf = shelf, uiLang = uiLang, onClick = { onOpen(shelf.category.id) })
        }
    }
}

@Composable
private fun ShelfCard(shelf: CategoryShelf.Shelf, uiLang: String, onClick: () -> Unit) {
    Column(
        Modifier
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 2f), contentAlignment = Alignment.Center) {
            if (shelf.category.imageUrl != null) {
                AsyncImage(
                    model = shelf.category.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // The emoji is the server's own shorthand for the shelf, and it
                // is what the web app shows when there is no cover.
                Text(shelf.category.emoji.orEmpty(), style = TujiType.h1)
            }
        }
        Column(Modifier.padding(TujiSpace.S3)) {
            Text(
                CategoryShelf.title(shelf.category, uiLang),
                style = TujiType.bodyStrong,
                color = TujiColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.atlas_count, shelf.count),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
            )
        }
    }
}

/** One shelf's words. */
@Composable
fun AtlasWordsScreen(
    words: List<Word>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = TujiSpace.S4, end = TujiSpace.S4,
            top = TujiSpace.S3, bottom = bottomPadding + TujiSpace.S6,
        ),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(words, key = { it.id }) { word ->
            WordTile(word = word, onClick = { onOpen(word.id) })
        }
    }
}

@Composable
private fun WordTile(word: Word, onClick: () -> Unit) {
    Column(
        Modifier.tujiClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(TujiColor.Paper2),
            contentAlignment = Alignment.Center,
        ) {
            WordPicture(
                url = word.imageUrl,
                kind = WordImageKind.of(word.category),
                inset = TujiSpace.S2,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            word.word,
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        word.chinese?.let {
            Text(
                it,
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3)
    }
}
