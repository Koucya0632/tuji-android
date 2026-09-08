package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.catalog.WordSearch
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiTextField
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Word
import coil3.compose.AsyncImage

/**
 * 搜尋 over the catalogue already in memory.
 *
 * No debounce and no request: [WordSearch] filters 557 rows, which is fast
 * enough that a keystroke and its result are the same frame — and it keeps
 * working with no network, which the study flows next door already assume.
 */
@Composable
fun AtlasSearchScreen(
    words: List<Word>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, words) { WordSearch.matches(query, words) }

    Column(Modifier.fillMaxSize()) {
        TujiTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.atlas_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        )

        when {
            query.isBlank() -> Centered(stringResource(R.string.atlas_search_prompt))
            results.isEmpty() -> Centered(stringResource(R.string.atlas_search_empty))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = TujiSpace.S4, end = TujiSpace.S4,
                    bottom = bottomPadding + TujiSpace.S6,
                ),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                items(results, key = { it.id }) { word ->
                    ResultRow(word = word, onClick = { onOpen(word.id) })
                }
            }
        }
    }
}

@Composable
private fun ResultRow(word: Word, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onClick)
            .padding(TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).background(TujiColor.Paper3),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = word.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(TujiSpace.S1),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                word.word,
                style = TujiType.bodyStrong,
                color = TujiColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            word.chinese?.let {
                Text(
                    it,
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
