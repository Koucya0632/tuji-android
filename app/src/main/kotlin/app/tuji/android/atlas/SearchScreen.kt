package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.EmptyStatePlacement
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiErrorState
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind

/**
 * 搜尋 — a field, and what it has found.
 *
 * iOS's `SearchView`, opened from the magnifier on 今天 and 圖鑑 and left by
 * 取消. The local half answers in the same frame as the keystroke; the request
 * that follows is a supplement, and [SearchViewModel] owns the rules about
 * when it is worth showing. This file draws them.
 *
 * **The field's text is this screen's, the results are the model's.** They are
 * not the same state: what has been typed changes on every keystroke, while
 * what is on screen answers the last query that produced anything.
 */
@Composable
fun AtlasSearchScreen(
    vm: SearchViewModel,
    direction: LearningDirection,
    showChinese: Boolean,
    recents: RecentSearchStore,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
) {
    // Saveable, so coming back from a result finds the query still typed. A
    // TextFieldValue rather than a String so a picked recent query can put the
    // cursor after itself; a String field keeps the old selection, which on an
    // empty field is 0, and the next keystroke landed in front of the word.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val typed = field.text
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by recents.queries.collectAsStateWithLifecycle()

    // Above the keyboard, as iOS's safe area is: the empty state is placed at
    // 35% of the space *left*, not of a screen the keyboard is covering. The
    // navigation bar is excluded because the shell already pads for it.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
        SearchBar(
            value = field,
            placeholder = stringResource(
                if (direction == LearningDirection.ZH_JA) R.string.search_placeholder_ja
                else R.string.search_placeholder_en,
            ),
            onValueChange = {
                // A cursor move is not a new query.
                if (it.text != field.text) vm.query(it.text)
                field = it
            },
            onCancel = onCancel,
        )

        val trimmed = typed.trim()
        when {
            trimmed.isEmpty() && recent.isEmpty() -> EmptyStatePlacement {
                MascotEmptyState(
                    title = stringResource(R.string.search_empty_title),
                    message = stringResource(
                        if (direction == LearningDirection.ZH_JA) R.string.search_empty_message_ja
                        else R.string.search_empty_message_en,
                    ),
                )
            }

            trimmed.isEmpty() -> RecentList(
                queries = recent,
                onPick = {
                    field = TextFieldValue(it, selection = TextRange(it.length))
                    vm.queryNow(it)
                },
                onClear = recents::clear,
            )

            results.words.isNotEmpty() -> ResultList(
                words = results.words,
                query = results.query,
                searching = results.searching,
                showChinese = showChinese,
                onOpen = onOpen,
            )

            // 「找不到」 while a request that may still find one is in flight
            // is a wrong answer that arrives before the right one.
            results.searching -> SkeletonRows()

            results.failed -> Box(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                contentAlignment = Alignment.Center,
            ) {
                TujiErrorState(
                    title = stringResource(R.string.search_failed),
                    message = stringResource(R.string.atlas_search_offline),
                ) {
                    TujiButton(text = stringResource(R.string.retry), onClick = { vm.queryNow(typed) })
                }
            }

            else -> EmptyStatePlacement {
                MascotEmptyState(
                    title = stringResource(R.string.search_not_found, trimmed),
                    // Names the one thing that most often works, rather than
                    // telling the user to go and think of a better word.
                    message = stringResource(R.string.search_try_chinese),
                )
            }
        }
    }
}

/**
 * A square block of 紙2 — a hole in the paper rather than an outline drawn on
 * it — with the 瞳黃 ring only while it has focus, and 取消 beside it.
 */
@Composable
private fun SearchBar(
    value: TextFieldValue,
    placeholder: String,
    onValueChange: (TextFieldValue) -> Unit,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .background(TujiColor.Paper2)
                .then(if (focused) Modifier.border(TujiBorder.Bw2, TujiColor.Current) else Modifier)
                .padding(start = TujiSpace.S3, end = TujiSpace.S1),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TujiGlyph.Search(size = 17.dp, tint = TujiColor.Ink2)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TujiType.body.copy(color = TujiColor.Ink),
                cursorBrush = SolidColor(TujiColor.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                interactionSource = interaction,
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(placeholder, style = TujiType.body, color = TujiColor.Ink3, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            if (value.text.isNotEmpty()) {
                // A bare ✕, not a grey filled disc — that is the platform's own
                // clear button, and it gives the field away as a system control.
                val label = stringResource(R.string.search_clear)
                Box(
                    Modifier
                        .size(44.dp)
                        .tujiClickable {
                            onValueChange(TextFieldValue())
                            focus.requestFocus()
                        }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    TujiGlyph.Close(size = 14.dp, tint = TujiColor.Ink2)
                }
            }
        }
        TextAction(stringResource(R.string.cancel), onCancel)
    }
}

@Composable
private fun RecentList(queries: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = TujiSpace.S3, bottom = TujiSpace.S5)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_recent),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                    modifier = Modifier.weight(1f),
                )
                TextAction(stringResource(R.string.search_clear_all), onClear)
            }
        }
        itemsIndexed(queries, key = { _, q -> q }) { index, query ->
            if (index > 0) InsetRule()
            Row(
                Modifier
                    .fillMaxWidth()
                    .tujiClickable { onPick(query) }
                    .height(56.dp)
                    .padding(horizontal = TujiSpace.S4),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    query,
                    style = TujiType.body,
                    color = TujiColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Up and to the left, into the field it will refill: "put this
                // back", not another row that opens a screen.
                TujiGlyph.ArrowLeft(size = 17.dp, tint = TujiColor.Ink3, modifier = Modifier.rotate(45f))
            }
        }
    }
}

@Composable
private fun ResultList(
    words: List<Word>,
    query: String,
    searching: Boolean,
    showChinese: Boolean,
    onOpen: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = TujiSpace.S5)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S2, bottom = TujiSpace.S3),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_results, words.size),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                )
                if (searching) {
                    Box(Modifier.width(40.dp).height(TujiBorder.Bw3).background(TujiColor.Current))
                }
            }
        }
        itemsIndexed(words, key = { _, w -> w.id }) { index, word ->
            if (index > 0) InsetRule()
            ResultRow(word, query, showChinese) { onOpen(word.id) }
        }
    }
}

@Composable
private fun ResultRow(word: Word, query: String, showChinese: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tujiClickable(onClick = onClick)
            .height(88.dp)
            .padding(horizontal = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The square holds the picture, so a portrait cut-out cannot make its
        // own row taller than its neighbours'.
        Box(Modifier.size(56.dp).background(TujiColor.Paper2)) {
            WordPicture(
                url = word.imageUrl,
                kind = WordImageKind.of(word.category),
                inset = TujiSpace.S1,
                ground = TujiColor.Paper2,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                highlighted(word.word, query),
                style = TujiType.h3,
                color = TujiColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showChinese) {
                word.chinese?.let {
                    Text(
                        highlighted(it, query),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TujiGlyph.ArrowLeft(size = 16.dp, tint = TujiColor.Ink3, modifier = Modifier.rotate(180f))
    }
}

/**
 * The matched part on a 瞳黃 ground rather than in another colour: a recoloured
 * glyph is hard to pick out mid-sentence in CJK, and a highlighter mark is the
 * way this app says "this is the part you asked for". No-op when nothing
 * matches — a server hit often matched a synonym that is not in the label.
 */
private fun highlighted(text: String, query: String): AnnotatedString {
    val needle = query.trim()
    val at = if (needle.isEmpty()) -1 else text.indexOf(needle, ignoreCase = true)
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(background = TujiColor.Current, color = TujiColor.Ink), at, at + needle.length)
    }
}

/** Skeleton rows, not a spinner: results are about to occupy this space. */
@Composable
private fun SkeletonRows() {
    Column(Modifier.padding(top = TujiSpace.S2)) {
        repeat(3) { index ->
            if (index > 0) InsetRule()
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3)
                    .height(64.dp)
                    .background(TujiColor.Paper2),
            )
        }
    }
}

@Composable
private fun InsetRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = TujiSpace.S4)
            .height(TujiBorder.Bw1)
            .background(TujiColor.Rule),
    )
}

/** An underlined word — with no colour that means "tappable", the line does. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).tujiClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TujiType.label,
            color = TujiColor.Ink,
            textDecoration = TextDecoration.Underline,
        )
    }
}
