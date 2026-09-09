package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.catalog.CardsListPaging
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordTile
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Category
import app.tuji.android.core.model.Word
import coil3.compose.AsyncImage

/**
 * 圖鑑 — every word, two to a row.
 *
 * It was a grid of shelves, and browsing by theme was the *only* way in. That
 * put a whole screen between the user and the thing the tab is named after,
 * and it made the 557-word dictionary look like ten rooms rather than one
 * book. Themes are still there — the count row links to them — but the tab
 * opens on the words.
 *
 * The grid pages rather than rendering 557 tiles: see [CardsListPaging], where
 * the window rule lives because nobody can see it is wrong by looking.
 */
@Composable
fun AtlasCardsScreen(
    words: List<Word>,
    loading: Boolean,
    bottomPadding: Dp,
    onOpenThemes: () -> Unit,
    onOpen: (String) -> Unit,
) {
    if (words.isEmpty()) {
        Centered(stringResource(if (loading) R.string.atlas_loading else R.string.atlas_failed))
        return
    }
    var visibleCount by rememberSaveable { mutableIntStateOf(CardsListPaging.PAGE_SIZE) }
    val page = remember(words, visibleCount) { CardsListPaging.page(words, visibleCount) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = TujiSpace.S4, end = TujiSpace.S4,
            top = TujiSpace.S3, bottom = bottomPadding + TujiSpace.S6,
        ),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Count on the left, one action on the right. 主題 sits here rather
        // than in a bar because it is about *these* words — and it keeps the
        // tab root free of a top bar, which is how every other root reads.
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = TujiSpace.S1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.atlas_count, page.matchCount),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.atlas_themes_link),
                    style = TujiType.label,
                    color = TujiColor.Ink,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .tujiClickable(onClick = onOpenThemes)
                        .padding(vertical = TujiSpace.S1),
                )
            }
        }

        items(page.words, key = { it.id }) { word ->
            WordTile(word = word, modifier = Modifier.tujiClickable { onOpen(word.id) })
        }

        if (page.canShowMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.atlas_show_more),
                    style = TujiType.bodySmStrong,
                    color = TujiColor.Ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tujiClickable { visibleCount += CardsListPaging.PAGE_SIZE }
                        .padding(vertical = TujiSpace.S3),
                )
            }
        }
    }
}

/**
 * 主題 — the index of every theme in the dictionary.
 *
 * Text tiles, not the photographic covers this screen used to draw. The cover
 * art belongs to the theme's own page, where it is a hero worth the height;
 * here it made ten rooms compete with each other at the size of a thumbnail,
 * and pushed the count off the fold.
 */
@Composable
fun AtlasThemesScreen(
    shelves: List<CategoryShelf.Shelf>,
    uiLang: String,
    loading: Boolean,
    bottomPadding: Dp,
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
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shelves, key = { it.category.id }) { shelf ->
            ThemeTile(shelf = shelf, uiLang = uiLang, onClick = { onOpen(shelf.category.id) })
        }
    }
}

@Composable
private fun ThemeTile(shelf: CategoryShelf.Shelf, uiLang: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(1.dp, TujiColor.Paper3)
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
}

/**
 * One theme's page: a bleeding photograph with its name laid over the bottom,
 * the description, then the words.
 *
 * The hero bleeds, so there is no shared horizontal padding — every section
 * below carries its own. A block with paper either side of it is "a card"; one
 * that touches the screen edge is "this part of the screen", and the two carry
 * very different weight.
 */
@Composable
fun AtlasThemeScreen(
    category: Category?,
    words: List<Word>,
    uiLang: String,
    topPadding: Dp,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints {
            // The grid carries the page margin, and the hero is let back out of
            // it: a block with paper either side is "a card", one that touches
            // the screen edge is "this part of the screen", and the hero has to
            // be the second. `wrapContentWidth(unbounded)` is what allows a
            // child to be wider than the padding its parent imposed.
            val bleed = maxWidth
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = TujiSpace.S4, end = TujiSpace.S4,
                    bottom = bottomPadding + TujiSpace.S6,
                ),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (category != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ThemeHero(
                            category = category,
                            uiLang = uiLang,
                            modifier = Modifier.wrapContentWidth(unbounded = true).width(bleed),
                        )
                    }
                    description(category, uiLang)?.let { text ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(text, style = TujiType.body, color = TujiColor.Ink2)
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = TujiSpace.S2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.atlas_words_header),
                            style = TujiType.label,
                            color = TujiColor.Current,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${words.size}", style = TujiType.label, color = TujiColor.Ink3)
                    }
                }

                items(words, key = { it.id }) { word ->
                    WordTile(word = word, modifier = Modifier.tujiClickable { onOpen(word.id) })
                }
            }
        }

        // Floating over the hero rather than taking a row above it, so the page
        // still opens with the picture. Paper on ink, because ink is what it
        // sits on for the first screenful.
        Text(
            "←",
            style = TujiType.h3,
            color = TujiColor.Paper,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topPadding)
                .padding(TujiSpace.S2)
                .size(44.dp)
                .tujiClickable(onClick = onBack)
                .padding(TujiSpace.S2),
        )
    }
}

@Composable
private fun ThemeHero(category: Category, uiLang: String, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(16f / 9f).background(TujiColor.Paper2)) {
        AsyncImage(
            model = category.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // A one-way scrim for legibility, not decoration: nothing at the top,
        // ink by the bottom. It spans the whole hero rather than a band at its
        // foot — the name sits well above that foot, and a scrim that stops
        // short of the text it exists for is decoration after all.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(TujiColor.Ink.copy(alpha = 0f), TujiColor.Ink.copy(alpha = 0.7f)),
                    ),
                ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            Text(
                stringResource(R.string.atlas_theme_label),
                style = TujiType.label,
                color = TujiColor.Paper.copy(alpha = 0.6f),
            )
            Text(
                CategoryShelf.title(category, uiLang),
                style = TujiType.h1,
                color = TujiColor.Paper,
            )
            Text(
                category.name,
                style = TujiType.bodySm,
                color = TujiColor.Paper.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * The theme's own words, in the reader's language — never an id.
 *
 * Null rather than an invented sentence: iOS falls back to 「探索與X相關的常用
 * 單字」, which is a sentence that says nothing, and a paragraph of nothing
 * above the grid costs more height than it returns.
 */
private fun description(category: Category, uiLang: String): String? {
    val text = if (uiLang.startsWith("zh")) {
        category.description ?: category.descriptionEn
    } else {
        category.descriptionEn ?: category.description
    }
    return text?.takeIf { it.isNotBlank() }
}

@Composable
internal fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3)
    }
}
