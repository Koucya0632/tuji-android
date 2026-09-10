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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiTextField
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Word

/**
 * 搜尋 — the rows already in memory, and then the ones only the server can see.
 *
 * The local half answers in the same frame as the keystroke and keeps working
 * on a plane; the request that follows is a supplement, and [SearchViewModel]
 * owns the rules about when it is worth showing. This file draws them.
 *
 * **The field's text is this screen's, the results are the model's.** They are
 * not the same state: what has been typed changes on every keystroke, while
 * what is on screen answers the last query that produced anything.
 */
@Composable
fun AtlasSearchScreen(
    vm: SearchViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TujiTextField(
            value = typed,
            onValueChange = {
                typed = it
                vm.query(it)
            },
            placeholder = stringResource(R.string.atlas_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        )

        when {
            typed.isBlank() -> Centered(stringResource(R.string.atlas_search_prompt))
            results.words.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(
                    start = TujiSpace.S4, end = TujiSpace.S4,
                    bottom = bottomPadding + TujiSpace.S6,
                ),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                items(results.words, key = { it.id }) { word ->
                    ResultRow(word = word, onClick = { onOpen(word.id) })
                }
            }
            // 「沒有相符的詞」 while a request that may still find one is in
            // flight is a wrong answer that arrives before the right one.
            results.searching -> Centered(stringResource(R.string.atlas_search_searching))
            results.failed -> Centered(stringResource(R.string.atlas_search_offline))
            else -> Centered(stringResource(R.string.atlas_search_empty))
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
            WordPicture(
                url = word.imageUrl,
                kind = WordImageKind.of(word.category),
                // A 56dp thumbnail inset by the page margin would have nothing
                // left in the middle, and its ground is 紙3, not 紙2.
                inset = TujiSpace.S1,
                ground = TujiColor.Paper3,
                modifier = Modifier.fillMaxSize(),
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
