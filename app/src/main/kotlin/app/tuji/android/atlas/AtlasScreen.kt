package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import app.tuji.android.core.study.ThemeStatus
import app.tuji.android.core.study.MasteryLevel
import app.tuji.android.core.design.MasteryBadge
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.catalog.CardsListPaging
import app.tuji.android.core.catalog.CardsSource
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiErrorState
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.design.TujiSkeleton
import app.tuji.android.core.design.TujiImagePlaceholder
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.capture.AtlasCaptureQueue
import app.tuji.android.core.model.CaptureProgress
import app.tuji.android.capture.CaptureQueueTile
import app.tuji.android.core.design.WordTile
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Category
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import coil3.compose.AsyncImage

/**
 * 圖鑑 — one filter row, and what the chosen source has.
 *
 * **官方 does not show words at all.** It shows the themes, each with its
 * cover, and the words behind one are on the theme's own page. The dictionary
 * is 800 words long; a flat grid of it was a list you paged through sixty at a
 * time, with the themes hidden behind a small 主題 → link that had its own
 * screen. That screen is retired: this *is* it, at the top of the tab where
 * browsing starts.
 *
 * The other three sources still show word tiles — a photographed card or one
 * taken in from 物見 belongs to no theme. Those grids page rather than
 * rendering everything: see [CardsListPaging], where the window rule lives
 * because nobody can see it is wrong by looking.
 */
@Composable
fun AtlasCardsScreen(
    words: List<Word>,
    personal: CardsSourceStore.Personal,
    scores: MasteryStore.Scores,
    loading: Boolean,
    bottomPadding: Dp,
    /** The dictionary's themes, for 官方. */
    shelves: List<CategoryShelf.Shelf>,
    /** A theme's (seen, total) from the server, or null while unfetched. */
    seenAndTotal: (String) -> Pair<Int, Int>?,
    uiLang: String,
    onOpenTheme: (String) -> Unit,
    /** Another go at the catalogue, for the error state. */
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit = {},
    isGuest: Boolean = false,
    /** 管理 → on 我做的. Null for a guest, who has made nothing. */
    onOpenManage: (() -> Unit)? = null,
    /** 當前圖鑑語言, for the reading line a peek draws. */
    session: TargetLanguage = TargetLanguage.EN,
    showChinese: Boolean = true,
    onBookmark: ((String) -> Unit)? = null,
    /** Captures still being made. Drawn at the head of 我做的. */
    captureJobs: List<AtlasCaptureQueue.Item> = emptyList(),
    onRetryCapture: (String) -> Unit = {},
) {
    var source by rememberSaveable { mutableStateOf(CardsSource.Official) }
    // The word a long press is holding up. Not saveable: a peek is a look, and
    // a look that survives the process being killed is a window the reader
    // never asked to come back to.
    var peek by remember { mutableStateOf<Word?>(null) }
    val haptics = rememberTujiHaptics()
    val showsThemes = source == CardsSource.Official
    val shown = remember(source, words, personal) {
        CardsSourceRules.words(source, words, personal.mine, personal.taken, personal.bookmarked)
    }

    // The dictionary failing to load is the tab failing to load; the other two
    // shelves being empty is an answer, and it has its own sentence.
    //
    // An error is only worth the whole screen when there is nothing behind it —
    // and unlike the sentence this used to print, it offers a way out. iOS's
    // `TujiErrorState` carries 重試 for the same reason: a dead end that names
    // the problem is still a dead end.
    if (words.isEmpty()) {
        if (loading) {
            CardGridSkeleton(bottomPadding)
        } else {
            Box(Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4), Alignment.Center) {
                TujiErrorState(
                    title = stringResource(R.string.atlas_failed),
                    actions = {
                        TujiButton(
                            text = stringResource(R.string.retry),
                            onClick = onRetry,
                            modifier = Modifier.padding(top = TujiSpace.S4),
                        )
                    },
                )
            }
        }
        return
    }
    var visibleCount by rememberSaveable(source) { mutableIntStateOf(CardsListPaging.PAGE_SIZE) }
    val page = remember(shown, visibleCount) { CardsListPaging.page(shown, visibleCount) }

    peek?.let { held ->
        WordPeekSheet(
            word = held,
            session = session,
            showChinese = showChinese,
            bookmarked = held.id in personal.bookmarked,
            onBookmark = onBookmark?.let { toggle -> { toggle(held.id) } },
            onOpen = { peek = null; onOpen(held.id) },
            onDismiss = { peek = null },
        )
    }

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
        // Actions only, as on iOS. A title here would say 「圖鑑」 directly above
        // the tab that says it, lit, at the moment you are looking at it.
        item(span = { GridItemSpan(maxLineSpan) }) {
            val label = stringResource(R.string.search_open)
            // 24dp of row with a 48dp target overflowing it: the glyph sits
            // where iOS's does, and the thumb still gets the whole square.
            Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .offset(x = 14.dp)
                        .requiredSize(48.dp)
                        .tujiClickable(onClick = onSearch)
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    TujiGlyph.Search(size = 18.dp, tint = TujiColor.Ink2)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SourceRow(selected = source, isGuest = isGuest, onSelect = { source = it })
        }

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
                // 我做的 is the only source with an action now. 官方 used to
                // carry 主題 → beside the count; the themes *are* the grid, so
                // the link would point at the screen the reader is on.
                //
                // The count stays in words even on 官方 — 807 字 is what the
                // official half of the catalogue has, which is the number worth
                // knowing, and it reuses the one 「%1$d 字」 key rather than
                // minting a second concept.
                if (source == CardsSource.Mine && onOpenManage != null) {
                    Text(
                        stringResource(R.string.atlas_manage_link),
                        style = TujiType.label,
                        color = TujiColor.Ink,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .tujiClickable(onClick = onOpenManage)
                            .padding(vertical = TujiSpace.S1),
                    )
                }
            }
        }

        // Captures being made sit at the head of their own shelf, in the grid
        // rather than in a strip above it: a band pinned over the chips says
        // "notification about a card" for something that *is* a card, and it
        // costs a permanent stripe of the tab for as long as any job is alive.
        if (source == CardsSource.Mine && !showsThemes) {
            items(captureJobs, key = { "job:" + it.id }) { job ->
                // Three tiles, three taps, as iOS routes them: retry what can
                // be retried, and send everything else to 圖鑑管理 — which is
                // where the card landed, and where the one way out of 已達上限
                // (delete something) lives.
                val tap: (() -> Unit)? = when {
                    job.progress.canRetry -> ({ onRetryCapture(job.id) })
                    job.progress.isFailed || job.progress == CaptureProgress.Ready -> onOpenManage
                    else -> null
                }
                CaptureQueueTile(
                    job = job,
                    modifier = tap?.let { Modifier.tujiClickable(onClick = it) } ?: Modifier,
                )
            }
        }

        // An empty shelf is an answer, and it goes *inside* the grid so the
        // chips stay on screen: a sentence that replaces the whole page would
        // take away the only way back to a shelf that has something on it.
        val empty = if (showsThemes) shelves.isEmpty() else page.words.isEmpty()
        if (empty && !(source == CardsSource.Mine && captureJobs.isNotEmpty())) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // 官方 empties differently from the other three: what is missing
                // is the catalogue itself, not a word of yours, so it says so
                // in the theme grid's own words — and has no hint, because
                // there is nothing the reader can do to put a theme there.
                val (title, hint) = when (source) {
                    CardsSource.Official -> R.string.atlas_themes_empty to (null as Int?)
                    CardsSource.Bookmarked -> R.string.atlas_bookmarked_empty to R.string.atlas_bookmarked_empty_hint
                    CardsSource.Mine -> R.string.atlas_mine_empty to R.string.atlas_mine_empty_hint
                    else -> R.string.atlas_taken_empty to R.string.atlas_taken_empty_hint
                }
                // A fixed 320dp stage with the cat 35% down it, as iOS frames
                // the same state: a lazy grid item has no height of its own to
                // take a fraction of.
                Box(
                    Modifier.fillMaxWidth().height(320.dp).padding(top = 112.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    MascotEmptyState(title = stringResource(title), message = hint?.let { stringResource(it) })
                }
            }
        }

        if (showsThemes) {
            items(shelves, key = { "theme:" + it.category.id }) { shelf ->
                val status = remember(shelf.category.id, words, scores) {
                    ThemeStatus.of(
                        wordIds = CategoryShelf.words(shelf.category.id, words).map { it.id },
                        masteryScore = scores::score,
                        seenAndTotal = seenAndTotal(shelf.category.id),
                    )
                }
                ThemeCoverTile(
                    shelf = shelf,
                    status = status,
                    uiLang = uiLang,
                    onClick = { onOpenTheme(shelf.category.id) },
                )
            }
        }

        items(if (showsThemes) emptyList() else page.words, key = { it.id }) { word ->
            WordTile(
                word = word,
                modifier = Modifier.tujiClickable(
                    // The heavier tap: a long press commits to raising
                    // something, and the card it raises takes a moment.
                    onLongClick = { haptics.firm(); peek = word },
                ) { onOpen(word.id) },
                badge = { MasteryScale(scores.score(word.id)) },
            )
        }

        if (page.canShowMore && !showsThemes) {
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
 * The source chips.
 *
 * One is always lit and tapping the lit one does nothing — see [CardsSource]
 * for why there is no 全部. Selected is ink on paper reversed, which is the
 * same "this one" the tab bar uses; unselected is the 紙2 ground every other
 * available-but-not-chosen control in the app sits on.
 */
@Composable
private fun SourceRow(selected: CardsSource, isGuest: Boolean, onSelect: (CardsSource) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        CardsSource.available(isGuest).forEach { source ->
            val lit = source == selected
            Text(
                stringResource(
                    when (source) {
                        CardsSource.Official -> R.string.atlas_source_official
                        CardsSource.Bookmarked -> R.string.atlas_source_bookmarked
                        CardsSource.Mine -> R.string.atlas_source_mine
                        CardsSource.Taken -> R.string.atlas_source_taken
                    },
                ),
                style = TujiType.bodySmStrong,
                color = if (lit) TujiColor.Paper else TujiColor.Ink2,
                // A fixed 36, as iOS sets it: the chips are a row of one
                // shape, and a height that falls out of the padding moves with
                // the font and stops being one.
                modifier = Modifier
                    .height(36.dp)
                    .background(if (lit) TujiColor.Ink else TujiColor.Paper2)
                    .tujiClickable { if (!lit) onSelect(source) }
                    .padding(horizontal = TujiSpace.S3)
                    .wrapContentHeight(),
            )
        }
    }
}

/** The five-segment scale, with its copy resolved. */
@Composable
private fun MasteryScale(score: Int?) {
    val level = MasteryLevel.of(score)
    MasteryBadge(level = level, label = level.label(), spoken = masterySpoken(score))
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
    scores: MasteryStore.Scores,
    uiLang: String,
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
                            // 棕 and tracked, as iOS sets its section headers:
                            // 瞳黃 on paper was the one label on the page that
                            // failed contrast, and it is not a selection.
                            style = TujiType.label.copy(letterSpacing = 2.sp),
                            color = TujiColor.BrandSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${words.size}", style = TujiType.label, color = TujiColor.Ink3)
                    }
                }

                if (words.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = TujiSpace.S5), contentAlignment = Alignment.Center) {
                            MascotEmptyState(title = stringResource(R.string.atlas_theme_empty), compact = true)
                        }
                    }
                }

                items(words, key = { it.id }) { word ->
                    WordTile(
                        word = word,
                        modifier = Modifier.tujiClickable { onOpen(word.id) },
                        badge = { MasteryScale(scores.score(word.id)) },
                    )
                }
            }
        }

        // Floating over the hero rather than taking a row above it, so the page
        // still opens with the picture — the same arrow, on the same margin, as
        // every other pushed screen's bar (`TujiNavBar`).
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            // No status-bar padding of its own: the shell already starts every
            // screen below it, and adding it again sank the arrow into the photo.
            modifier = Modifier.align(Alignment.TopStart),
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

/**
 * The grid that is coming, in blocks.
 *
 * Two columns and a square each, because that is the shape the tiles land in:
 * the whole point of a skeleton over a line of text is that nothing moves when
 * the real ones arrive under a thumb that is already scrolling.
 */
@Composable
private fun CardGridSkeleton(bottomPadding: Dp) {
    val label = stringResource(R.string.atlas_loading)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = TujiSpace.S4, end = TujiSpace.S4,
            top = TujiSpace.S5, bottom = bottomPadding + TujiSpace.S6,
        ),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        modifier = Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = label },
        userScrollEnabled = false,
    ) {
        items(6) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) { TujiImagePlaceholder() }
                TujiSkeleton(height = 14.dp, width = 72.dp)
            }
        }
    }
}
