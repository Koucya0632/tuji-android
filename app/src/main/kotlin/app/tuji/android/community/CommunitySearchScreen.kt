package app.tuji.android.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.atlas.SearchBar
import app.tuji.android.core.design.EmptyStatePlacement
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiErrorState
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiSkeletonRows
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.AtlasPublicItem

/**
 * 物見搜尋 — iOS's `AtlasSearchView`: one field, two shelves. 合集 by title or
 * author, and the words everyone photographed by spelling or 中文, in the
 * language being learned. The field is 搜尋's own, so the two read as one
 * control.
 */
@Composable
fun CommunitySearchScreen(
    vm: CommunitySearchViewModel,
    onCancel: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    // Saveable, so coming back from a result finds the query still typed.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
        SearchBar(
            value = field,
            placeholder = stringResource(R.string.community_search_placeholder),
            onValueChange = {
                // A cursor move is not a new query.
                if (it.text != field.text) vm.query(it.text)
                field = it
            },
            onCancel = onCancel,
        )

        val trimmed = field.text.trim()
        when {
            trimmed.isEmpty() -> EmptyStatePlacement {
                MascotEmptyState(
                    title = stringResource(R.string.community_search_empty_title),
                    message = stringResource(R.string.community_search_empty_message),
                )
            }

            !results.isEmpty -> ResultList(
                results = results,
                onOpenCollection = onOpenCollection,
                onOpenItem = onOpenItem,
                onOpenAuthor = onOpenAuthor,
            )

            results.phase == CommunitySearchViewModel.Phase.Loading ->
                TujiSkeletonRows(count = 3, height = 80.dp, label = stringResource(R.string.community_loading))

            results.phase == CommunitySearchViewModel.Phase.Failed -> Box(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                contentAlignment = Alignment.Center,
            ) {
                TujiErrorState(
                    title = stringResource(R.string.search_failed),
                    message = stringResource(R.string.community_failed),
                ) {
                    TujiButton(text = stringResource(R.string.retry), onClick = { vm.queryNow(field.text) })
                }
            }

            else -> EmptyStatePlacement {
                MascotEmptyState(
                    title = stringResource(R.string.search_not_found, results.query.ifEmpty { trimmed }),
                    message = stringResource(R.string.community_search_not_found_message),
                )
            }
        }
    }
}

@Composable
private fun ResultList(
    results: CommunitySearchViewModel.Results,
    onOpenCollection: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    val searching = results.phase == CommunitySearchViewModel.Phase.Loading
    LazyColumn(contentPadding = PaddingValues(top = TujiSpace.S2, bottom = TujiSpace.S5)) {
        if (results.collections.isNotEmpty()) {
            item(key = "h-collections") {
                SectionHeader(stringResource(R.string.author_segment_collections), results.collections.size, searching)
            }
            results.collections.forEachIndexed { index, collection ->
                item(key = "c-${collection.id}") {
                    if (index > 0) RowRule()
                    CollectionRow(collection, onOpen = { onOpenCollection(collection.slug) })
                }
            }
        }
        if (results.items.isNotEmpty()) {
            item(key = "h-items") {
                Column {
                    if (results.collections.isNotEmpty()) Gap(TujiSpace.S4)
                    SectionHeader(stringResource(R.string.community_search_words), results.items.size, searching)
                }
            }
            itemGrid(results.items, onOpenItem, onOpenAuthor)
        }
    }
}

/** Two columns, as a collection's own page lays its members out. */
private fun LazyListScope.itemGrid(
    items: List<AtlasPublicItem>,
    onOpenItem: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    items.chunked(2).forEachIndexed { row, pair ->
        item(key = "i-${pair.first().id}") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S4)
                    .padding(top = if (row > 0) TujiSpace.S3 else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                pair.forEach { item ->
                    PublicItemTile(
                        item = item,
                        enabled = true,
                        onOpen = { onOpenItem(item.slug) },
                        onOpenAuthor = item.author?.let { author -> { onOpenAuthor(author.handle) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd last tile keeps its column's width.
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, searching: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Section labels are 墨3, like 搜尋's 「最近搜尋」.
        Text(title, style = TujiType.label, color = TujiColor.Ink3)
        Text("$count", style = TujiType.label, color = TujiColor.Ink3)
        if (searching) TujiIndeterminateBar(Modifier.width(40.dp))
    }
}
