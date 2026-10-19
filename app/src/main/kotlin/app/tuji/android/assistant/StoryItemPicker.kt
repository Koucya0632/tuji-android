package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.atlas.ThemeCoverTile
import app.tuji.android.core.catalog.CardsSource
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.catalog.WordSearch
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordTile
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Word
import app.tuji.android.core.study.ThemeStatus
import app.tuji.android.form.TujiSheet

/**
 * 情境故事's 選單字, laid out the way the 圖鑑 tab is — iOS's `StoryItemPicker`.
 * Picking a word for a story is browsing the 圖鑑, so it looks like it: the
 * source chips, 官方 as the shelf of theme covers, and a theme's words as the
 * same two columns of tiles — tapping one picks it instead of opening it.
 */
@Composable
internal fun StoryItemPicker(
    model: StoryBuilderModel,
    shelves: List<CategoryShelf.Shelf>,
    themeStatus: (String) -> ThemeStatus,
    bookmarked: Set<String>,
    uiLang: String,
    onDismiss: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(CardsSource.Official) }
    var theme by remember { mutableStateOf<CategoryShelf.Shelf?>(null) }
    val candidates = remember { model.candidateWords }
    val official = remember(candidates) { candidates.filter { !CardsSourceRules.isCustom(it.id) && !CardsSourceRules.isSaved(it.id) } }

    TujiSheet(
        title = stringResource(R.string.story_picked_count, state.picked.size, StoryBuilderModel.MAX_ITEMS),
        onDismiss = { if (theme != null) theme = null else onDismiss() },
        maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f,
    ) {
        PaperField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.story_search),
            style = TujiType.body,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        )
        val trimmed = query.trim()
        val current = theme
        when {
            trimmed.isNotEmpty() -> WordGrid(WordSearch.matches(trimmed, candidates), state, model, emptyText = stringResource(R.string.story_no_match))
            current != null -> {
                Text(
                    "← " + CategoryShelf.title(current.category, uiLang),
                    style = TujiType.bodyStrong,
                    color = TujiColor.Ink,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .tujiClickable { theme = null }
                        .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                )
                WordGrid(CategoryShelf.words(current.category.id, official), state, model)
            }
            else -> {
                SourceChips(source) { source = it }
                if (source == CardsSource.Official) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = TujiSpace.S4, end = TujiSpace.S4, bottom = TujiSpace.S4),
                        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    ) {
                        items(shelves, key = { it.category.id }) { shelf ->
                            // No padlock — a story is not study, and nothing about writing one is locked.
                            ThemeCoverTile(shelf = shelf, status = themeStatus(shelf.category.id), uiLang = uiLang, onClick = { theme = shelf })
                        }
                    }
                } else {
                    val words = CardsSourceRules.words(
                        source,
                        official = official,
                        mine = candidates.filter { CardsSourceRules.isCustom(it.id) },
                        taken = candidates.filter { CardsSourceRules.isSaved(it.id) },
                        bookmarked = bookmarked,
                    )
                    WordGrid(words, state, model, source = source)
                }
            }
        }
    }
}

@Composable
private fun SourceChips(selected: CardsSource, onSelect: (CardsSource) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = TujiSpace.S4, end = TujiSpace.S4, bottom = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        listOf(CardsSource.Official, CardsSource.Mine, CardsSource.Taken, CardsSource.Bookmarked).forEach { value ->
            val on = value == selected
            Box(
                Modifier
                    .height(36.dp)
                    .background(if (on) TujiColor.Ink else TujiColor.Paper2)
                    .tujiClickable { onSelect(value) }
                    .semantics { this.selected = on }
                    .padding(horizontal = TujiSpace.S3),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        when (value) {
                            CardsSource.Official -> R.string.atlas_source_official
                            CardsSource.Bookmarked -> R.string.atlas_source_bookmarked
                            CardsSource.Mine -> R.string.atlas_source_mine
                            CardsSource.Taken -> R.string.atlas_source_taken
                        },
                    ),
                    style = TujiType.label,
                    color = if (on) TujiColor.Paper else TujiColor.Ink2,
                )
            }
        }
    }
}

@Composable
private fun WordGrid(
    words: List<Word>,
    state: StoryBuilderModel.State,
    model: StoryBuilderModel,
    source: CardsSource? = null,
    emptyText: String? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = TujiSpace.S4, end = TujiSpace.S4, bottom = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        if (words.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                when {
                    emptyText != null -> Text(emptyText, style = TujiType.bodySm, color = TujiColor.Ink3, modifier = Modifier.padding(vertical = TujiSpace.S4))
                    source != null && source != CardsSource.Official -> {
                        val (title, hint) = when (source) {
                            CardsSource.Mine -> R.string.atlas_mine_empty to R.string.atlas_mine_empty_hint
                            CardsSource.Bookmarked -> R.string.atlas_bookmarked_empty to R.string.atlas_bookmarked_empty_hint
                            else -> R.string.atlas_taken_empty to R.string.atlas_taken_empty_hint
                        }
                        Box(Modifier.fillMaxWidth().height(320.dp).padding(top = 112.dp), contentAlignment = Alignment.TopCenter) {
                            MascotEmptyState(title = stringResource(title), message = stringResource(hint))
                        }
                    }
                }
            }
        }
        items(words, key = { it.id }) { word -> PickTile(word, state, model) }
    }
}

/** A 圖鑑 tile that picks instead of opening. The check sits top-left — top-right is the tile's source dot. */
@Composable
private fun PickTile(word: Word, state: StoryBuilderModel.State, model: StoryBuilderModel) {
    val picked = state.isPicked(word)
    val blocked = !picked && !state.canPickMore
    Box(
        Modifier
            .alpha(if (blocked) 0.4f else 1f)
            .then(if (picked) Modifier.border(TujiBorder.Bw3, TujiColor.Ink).padding(TujiSpace.S1) else Modifier.padding(TujiSpace.S1))
            .tujiClickable(enabled = !blocked) { model.toggle(word) }
            .semantics { selected = picked },
    ) {
        WordTile(word = word)
        Box(
            Modifier
                .padding(TujiSpace.S2)
                .size(20.dp)
                .background(if (picked) TujiColor.Ink else TujiColor.Paper)
                .border(1.dp, if (picked) TujiColor.Ink else TujiColor.Ink3),
            contentAlignment = Alignment.Center,
        ) {
            if (picked) TujiGlyph.Check(size = 12.dp, tint = TujiColor.Paper)
        }
    }
}
