package app.tuji.android.atlas

import androidx.compose.foundation.background
import app.tuji.android.core.study.MasteryLevel
import app.tuji.android.core.design.MasteryBar
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.design.WordPicture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.WordDetail

/**
 * One catalogue entry in full.
 *
 * The pronunciation button plays the same pre-generated clip 聽句 plays, through
 * the same [app.tuji.android.core.model.ClipPlaying] seam — which is the whole
 * reason that seam is not called `SentencePlaying` any more.
 */
@Composable
fun WordDetailScreen(
    vm: WordDetailViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
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
    onOpenRelated: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is WordDetailViewModel.State.Loading -> Centered(stringResource(R.string.atlas_loading))
        is WordDetailViewModel.State.Failed -> Centered(stringResource(R.string.atlas_failed))

        is WordDetailViewModel.State.Loaded -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            val word = s.word
            Spacer(Modifier.height(TujiSpace.S2))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(TujiColor.Paper2),
                contentAlignment = Alignment.Center,
            ) {
                WordPicture(
                    url = word.imageUrl,
                    kind = WordImageKind.of(word.category),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Headline(
                word = word,
                playing = s.playing,
                canPlay = vm.canPlay(word),
                bookmarked = bookmarked,
                onPlay = vm::play,
                onBookmark = onBookmark,
            )

            word.chinese?.let {
                Text(it, style = TujiType.h3, color = TujiColor.Ink2)
            }

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
            )

            (word.chineseDefinition ?: word.targetDefinition)?.let {
                Section(stringResource(R.string.word_definition)) {
                    Text(it, style = TujiType.body, color = TujiColor.Ink)
                }
            }
            // Both, when both exist — the 釋義 in the language being learned is
            // study material, not a duplicate of the gloss.
            val target = word.targetDefinition
            if (word.chineseDefinition != null && target != null) {
                Text(target, style = TujiType.body, color = TujiColor.Ink2)
            }

            if (word.examples.isNotEmpty()) {
                Section(stringResource(R.string.word_examples)) {
                    word.examples.forEach { example ->
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = TujiSpace.S2),
                            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                        ) {
                            example.target?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = TujiType.body, color = TujiColor.Ink)
                            }
                            example.zh?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = TujiType.bodySm, color = TujiColor.Ink3)
                            }
                        }
                    }
                }
            }

            if (word.relatedWords.isNotEmpty()) {
                Section(stringResource(R.string.word_related)) {
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        // An id the catalogue does not know is dropped, not
                        // drawn as itself: a row that opens onto a 404 is worse
                        // than one fewer row.
                        word.relatedWords.mapNotNull(resolve).forEach { related ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .tujiClickable { onOpenRelated(related.id) }
                                    .padding(vertical = TujiSpace.S1),
                                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                            ) {
                                Text(
                                    related.word,
                                    style = TujiType.bodySmStrong,
                                    color = TujiColor.Accumulation,
                                )
                                related.chinese?.let {
                                    Text(it, style = TujiType.bodySm, color = TujiColor.Ink3)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
        }
    }
}

@Composable
private fun Headline(
    word: WordDetail,
    playing: Boolean,
    canPlay: Boolean,
    bookmarked: Boolean,
    onPlay: () -> Unit,
    onBookmark: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            val segments = word.readingSegments
            if (!segments.isNullOrEmpty()) {
                FuriganaHeadword(segments = segments, modifier = Modifier.fillMaxWidth())
            } else {
                Text(word.word, style = TujiType.h1, color = TujiColor.Ink)
            }
        }
        if (canPlay) {
            val label = stringResource(R.string.word_play)
            Box(
                Modifier
                    .size(48.dp)
                    .background(if (playing) TujiColor.Current else TujiColor.Paper2)
                    .semantics { contentDescription = label }
                    .tujiClickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Speaker(tint = TujiColor.Ink)
            }
        }
        // The mark is **passive**: it changes nothing about what is scheduled
        // for review. That is the whole difference between 書籤 and 學習主題,
        // and it is why this sits beside the word rather than in the study
        // controls.
        if (onBookmark != null) {
            val mark = stringResource(R.string.word_bookmark)
            Box(
                Modifier
                    .size(48.dp)
                    .background(TujiColor.Paper2)
                    .semantics { contentDescription = mark }
                    .tujiClickable(onClick = onBookmark),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Star(filled = bookmarked, tint = TujiColor.Ink)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
    ) {
        Text(title, style = TujiType.label, color = TujiColor.Ink3)
        content()
    }
}
