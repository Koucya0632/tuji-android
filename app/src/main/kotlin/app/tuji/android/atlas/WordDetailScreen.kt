package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.catalog.WordDetailContent
import app.tuji.android.core.catalog.WordDetailTab
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.MasteryBar
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiErrorState
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordExample
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.gloss.GlossBookmarks
import app.tuji.android.gloss.GlossCardHost
import app.tuji.android.gloss.InteractiveSentenceText
import app.tuji.android.core.model.WordForm
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.model.headwordDisplay
import app.tuji.android.core.model.language
import app.tuji.android.core.study.MasteryLevel

/**
 * One entry in full — iOS's `WordDetailPage`.
 *
 * This is a picture dictionary, so the picture is the first event on the page:
 * a full-bleed square with 返回 and 書籤 floating over it, not a photo inset in
 * a card under a bar. That means no shared horizontal padding; every section
 * below carries its own.
 *
 * The pronunciation button plays the same pre-generated clip 聽句 plays, through
 * the same [app.tuji.android.core.model.ClipPlaying] seam.
 *
 * Two things here iOS does not have, kept on purpose: the 下次複習 line under
 * the mastery bar, and 相關的詞 at the foot.
 */
@Composable
fun WordDetailScreen(
    vm: WordDetailViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
    /** The deck being studied — the fallback language for an untagged entry. */
    session: TargetLanguage,
    uiLang: String,
    showChinese: Boolean,
    /**
     * A catalogue id → the row, from the copy already in memory.
     *
     * The payload gives 相關的詞 as bare ids, and an id is not a word: 「oven」
     * under a Japanese entry tells a learner nothing and looks like a bug. The
     * catalogue is right there, so the row is resolved rather than fetched.
     */
    resolve: (String) -> app.tuji.android.core.model.Word?,
    /** Whether this word carries a 書籤 — held by `CardsSourceStore`, because
     *  the grid draws the same answer and two copies would disagree. */
    bookmarked: Boolean,
    /**
     * Null draws no star.
     *
     * 書籤 is a mark on a **dictionary** entry: the shelf filters the catalogue
     * by the marked ids, so a mark on a card the catalogue has never heard of
     * would save, sync, and then never appear anywhere. A control that works
     * and shows nothing is worse than no control.
     */
    onBookmark: (() -> Unit)?,
    scores: MasteryStore.Scores,
    onBack: () -> Unit,
    onOpenRelated: (String) -> Unit,
    /** Says a tapped 詞塊 out loud. Always synthesised — a 詞塊 has no clip. */
    speech: WordSpeaking? = null,
    accent: String = "us",
    /** 書籤 from inside a 詞塊 card, for the spans that are catalogue words. */
    glossBookmarks: GlossBookmarks? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    GlossCardHost(
        partOfSpeech = { WordDetailContent.partOfSpeech(it, uiLang) },
        speech = speech,
        accent = accent,
        onOpenWord = onOpenRelated,
        bookmarks = glossBookmarks,
    ) {
    when (val s = state) {
        is WordDetailViewModel.State.Loading -> Column(Modifier.fillMaxSize()) {
            FloatingBar(bookmarked = false, onBack = onBack, onBookmark = null)
            Centered(stringResource(R.string.atlas_loading))
        }

        is WordDetailViewModel.State.Failed -> Column(Modifier.fillMaxSize()) {
            FloatingBar(bookmarked = false, onBack = onBack, onBookmark = null)
            Box(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                contentAlignment = Alignment.Center,
            ) {
                TujiErrorState(
                    title = stringResource(R.string.word_not_found),
                    message = stringResource(R.string.atlas_failed),
                ) {
                    TujiButton(text = stringResource(R.string.atlas_back), onClick = onBack)
                }
            }
        }

        is WordDetailViewModel.State.Loaded -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            val word = s.word
            Hero(word = word, bookmarked = bookmarked, onBack = onBack, onBookmark = onBookmark)

            TitleRow(
                word = word,
                session = session,
                uiLang = uiLang,
                playing = s.playing,
                canPlay = vm.canPlay(word),
                onPlay = vm::play,
                modifier = Modifier.padding(horizontal = TujiSpace.S4),
            )

            // Spelled out here, not drawn as a scale: this is the one screen
            // given over to a single word, so it is the one place where "how
            // well do I know this, and when does it come back" is worth a
            // sentence rather than five segments of teal.
            val score = scores.score(word.id)
            MasteryBar(
                score = score,
                levelLabel = MasteryLevel.of(score).label(),
                value = if (score != null) "$score" else stringResource(R.string.mastery_no_record),
                nextReview = scores.nextReview(word.id)?.let { nextReviewLabel(it) },
                modifier = Modifier.padding(horizontal = TujiSpace.S4),
            )

            WordDetailSections(
                word = word,
                uiLang = uiLang,
                showChinese = showChinese,
                session = session,
                modifier = Modifier.padding(horizontal = TujiSpace.S4),
            )

            val related = word.relatedWords.mapNotNull(resolve)
            if (related.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = TujiSpace.S4),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                ) {
                    SectionTitle(stringResource(R.string.word_related))
                    // An id the catalogue does not know is dropped, not drawn as
                    // itself: a row that opens onto a 404 is worse than one
                    // fewer row.
                    related.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .tujiClickable { onOpenRelated(row.id) }
                                .padding(vertical = TujiSpace.S1),
                            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                        ) {
                            Text(row.word, style = TujiType.bodySmStrong, color = TujiColor.Accumulation)
                            if (showChinese) {
                                row.chinese?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink3) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(bottomPadding + TujiSpace.S5))
        }
    }
    }
}

/**
 * The square. The picture rules — fit a cut-out with an inset, fill a
 * photograph — are [WordPicture]'s, the same ones the grid uses, so the page
 * given over to one word never shows less of it than the tile you tapped.
 */
@Composable
private fun Hero(word: WordDetail, bookmarked: Boolean, onBack: () -> Unit, onBookmark: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(TujiColor.Paper2)) {
        WordPicture(
            url = word.imageUrl,
            kind = WordImageKind.of(word.category),
            inset = TujiSpace.S5,
            ground = TujiColor.Paper2,
            modifier = Modifier.fillMaxSize(),
        )
        FloatingBar(bookmarked = bookmarked, onBack = onBack, onBookmark = onBookmark)
    }
}

/** 返回 and 書籤 over the artwork: a transparent bar, so the page starts with the picture. */
@Composable
private fun FloatingBar(bookmarked: Boolean, onBack: () -> Unit, onBookmark: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = TujiSpace.S1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarControl(label = stringResource(R.string.atlas_back), onClick = onBack) {
            TujiGlyph.ArrowLeft(tint = TujiColor.Ink)
        }
        Spacer(Modifier.weight(1f))
        // The mark is **passive**: it changes nothing about what is scheduled
        // for review. That is the whole difference between 書籤 and 學習主題.
        if (onBookmark != null) {
            BarControl(label = stringResource(R.string.word_bookmark), onClick = onBookmark) {
                TujiGlyph.Star(filled = bookmarked, tint = TujiColor.Ink)
            }
        }
    }
}

@Composable
private fun BarControl(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    // Transparent, and that is also why these two are the controls with no
    // press ground: they float over the hero picture, and a rectangle
    // appearing on a photograph under the thumb reads as a drawing bug rather
    // than as an answer. The tap still buzzes.
    TujiIconButton(
        label = label,
        onClick = onClick,
        size = 48.dp,
        ground = Color.Transparent,
        content = icon,
    )
}

/**
 * The headword, its reading, its part of speech — and the pronunciation button
 * beside the block rather than inside it, so a long ruby word keeps the width.
 */
@Composable
internal fun TitleRow(
    word: WordDetail,
    session: TargetLanguage,
    uiLang: String,
    playing: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            val display = word.headwordDisplay(session)
            if (display is HeadwordDisplay.Ruby) {
                FuriganaHeadword(segments = display.segments, modifier = Modifier.fillMaxWidth())
            } else {
                Text(word.word, style = TujiType.h1, color = TujiColor.Ink)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (display is HeadwordDisplay.Line) {
                    Text(
                        display.text,
                        style = if (word.language(session) == TargetLanguage.JA) TujiType.bodySm else TujiType.monoLabel,
                        color = TujiColor.Ink2,
                    )
                }
                word.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        WordDetailContent.partOfSpeech(it, uiLang),
                        style = TujiType.label.copy(fontStyle = FontStyle.Italic),
                        color = TujiColor.Ink3,
                    )
                }
                word.cefrLevel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = TujiType.label,
                        color = TujiColor.Ink2,
                        modifier = Modifier
                            .background(TujiColor.Paper2)
                            .padding(horizontal = TujiSpace.S2, vertical = 2.dp),
                    )
                }
            }
        }
        if (canPlay) {
            TujiIconButton(
                label = stringResource(R.string.word_play),
                onClick = onPlay,
                size = 48.dp,
                ground = if (playing) TujiColor.Current else TujiColor.Paper2,
            ) {
                TujiGlyph.Speaker(tint = TujiColor.Ink)
            }
        }
    }
}

/**
 * 字詞資料 and 例句 — iOS's `WordDetailSections`.
 *
 * Shared with 物見's word page, whose payload is the same [WordDetail]: a
 * second copy of these cards is how two screens come to describe one word
 * differently.
 */
@Composable
internal fun WordDetailSections(
    word: WordDetail,
    uiLang: String,
    showChinese: Boolean,
    /** The deck being read — the fallback language for an untagged entry. */
    session: TargetLanguage,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
        Details(word = word, uiLang = uiLang, showChinese = showChinese, session = session)

        val examples = word.examples
            .filter { !it.target.isNullOrBlank() }
            .take(WordDetailContent.MAX_EXAMPLES)
        if (examples.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                SectionTitle(stringResource(R.string.word_examples_title))
                examples.forEach { ExampleCard(it, showChinese, session) }
            }
        }
    }
}

/** 字詞資料 · DETAILS — one card at a time, with a pill per kind of fact the entry has. */
@Composable
private fun Details(
    word: WordDetail,
    uiLang: String,
    showChinese: Boolean,
    session: TargetLanguage,
    modifier: Modifier = Modifier,
) {
    val tabs = WordDetailContent.tabs(word, showChinese)
    if (tabs.isEmpty()) return
    var chosen by rememberSaveable(word.id) { mutableStateOf(WordDetailTab.Definition) }
    // Kept valid when what is available shifts — turning 中文 off can take 譯義
    // away — so the card drawn always has its pill on screen.
    val selected = if (chosen in tabs) chosen else tabs.first()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
        SectionTitle(stringResource(R.string.word_details_title))
        if (tabs.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Box(
                        Modifier
                            .height(36.dp)
                            .background(if (active) TujiColor.Ink else TujiColor.Paper2)
                            .tujiClickable { chosen = tab }
                            .padding(horizontal = TujiSpace.S3),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(tab.label()),
                            style = TujiType.label,
                            color = if (active) TujiColor.Paper else TujiColor.Ink2,
                        )
                    }
                }
            }
        }
        when (selected) {
            WordDetailTab.Definition -> DefinitionCard(word, uiLang, showChinese, session)
            WordDetailTab.Forms -> FormsCard(word.forms)
            WordDetailTab.Origin -> word.etymology?.let { OriginCard(it) }
            WordDetailTab.Collocations -> CollocationsRow(word.collocations, word.collocationsZh)
        }
    }
}

@Composable
private fun DefinitionCard(word: WordDetail, uiLang: String, showChinese: Boolean, session: TargetLanguage) {
    // Monolingual mode — the interface language is the one being learned —
    // makes the gloss and the target definition the same string. Show it once.
    val target = word.targetDefinition?.takeIf { it.isNotBlank() }
    val glossDupesTarget = target != null && target == word.chinese
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Bottom) {
            if (showChinese && !glossDupesTarget) {
                word.chinese?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = TujiType.h3, color = TujiColor.Ink)
                }
            }
            word.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
                Text(
                    WordDetailContent.partOfSpeech(it, uiLang),
                    style = TujiType.label.copy(fontStyle = FontStyle.Italic),
                    color = TujiColor.Ink3,
                )
            }
        }
        // The 譯義 line is a sentence in the language being learned, so it is
        // tappable on the same terms an example is.
        target?.let {
            InteractiveSentenceText(
                sentence = it,
                spans = word.targetDefinitionSpans,
                language = word.language(session),
                style = TujiType.bodySm,
            )
        }
        // Written in the reader's own language, so it stays plain: glossing a
        // Chinese explainer for a Chinese reader teaches nothing.
        if (showChinese) {
            word.chineseDefinition?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = TujiType.label, color = TujiColor.Ink3)
            }
        }
    }
}

@Composable
private fun FormsCard(forms: List<WordForm>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.25f)),
    ) {
        forms.forEachIndexed { index, form ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule.copy(alpha = 0.2f)))
            }
            Row(Modifier.fillMaxWidth().padding(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                Text(form.label, style = TujiType.bodySm, color = TujiColor.Ink2, modifier = Modifier.weight(1f))
                Text(form.value, style = TujiType.monoLabel, color = TujiColor.Ink)
            }
        }
    }
}

/** 來源: 棕 on a 棕 wash with its edge on the left — history, not a claim about you. */
@Composable
private fun OriginCard(etymology: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(TujiColor.BrandSecondary.copy(alpha = 0.1f)),
    ) {
        Box(Modifier.width(TujiBorder.Bw3).fillMaxHeight().background(TujiColor.BrandSecondary))
        Text(
            etymology,
            style = TujiType.bodySm,
            color = TujiColor.BrandSecondary,
            modifier = Modifier.padding(start = TujiSpace.S4, end = TujiSpace.S3, top = TujiSpace.S3, bottom = TujiSpace.S3),
        )
    }
}

@Composable
private fun CollocationsRow(collocations: List<String>, zh: List<String>?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        collocations.forEachIndexed { index, phrase ->
            Column(
                Modifier
                    .background(TujiColor.BrandSecondary.copy(alpha = 0.1f))
                    .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(phrase, style = TujiType.label, color = TujiColor.Ink3)
                zh?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = TujiType.label, color = TujiColor.BrandSecondary)
                }
            }
        }
    }
}

@Composable
private fun ExampleCard(example: WordExample, showChinese: Boolean, session: TargetLanguage) {
    Card {
        example.target?.let {
            InteractiveSentenceText(sentence = it, spans = example.spans, language = session)
        }
        if (showChinese) {
            example.zh?.takeIf { it.isNotBlank() }?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.25f))
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = TujiType.label.copy(letterSpacing = 2.sp),
        color = TujiColor.Ink3,
        modifier = Modifier.padding(top = TujiSpace.S2),
    )
}

private fun WordDetailTab.label(): Int = when (this) {
    WordDetailTab.Definition -> R.string.word_tab_definition
    WordDetailTab.Forms -> R.string.word_tab_forms
    WordDetailTab.Origin -> R.string.word_tab_origin
    WordDetailTab.Collocations -> R.string.word_tab_collocations
}
