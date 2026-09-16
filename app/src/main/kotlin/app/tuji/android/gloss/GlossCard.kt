package app.tuji.android.gloss

import app.tuji.android.R
import app.tuji.android.core.design.GlossCalloutPlacement
import app.tuji.android.core.design.GlossCalloutShape
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tuji.android.core.model.GlossSpan
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.asHeadworded
import app.tuji.android.core.model.headwordDisplay

/**
 * 詞塊卡片 — what a tapped 詞塊 in a sentence raises.
 *
 * An overlay rather than a bottom sheet, and that is the whole reason it has
 * this shape: the card **points at the word**, with a caret aimed at it and a
 * 螢光筆 under the word itself. A sheet would bury the sentence the reader was
 * in the middle of, which is the one thing the card is about.
 *
 * Placement is [GlossCalloutPlacement]'s answer; this only wears it. When there
 * is no answer — the word sits where the card fits neither above nor below —
 * [callout] is null, the card parks at the bottom and draws no caret, because a
 * caret aimed at nothing is worse than none.
 */
@Composable
internal fun GlossCard(
    span: GlossSpan,
    language: TargetLanguage,
    callout: GlossCalloutShape.Caret?,
    partOfSpeech: (String) -> String,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onOpenWord: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    bookmarked: Boolean = false,
    onBookmark: ((String) -> Unit)? = null,
) {
    val shape = GlossCalloutShape(callout)
    // Reserved on the bottom when there is no caret, so the measured height
    // never depends on which way the caret points.
    val pointsDown = callout?.pointsDown ?: true
    Column(
        modifier
            .background(TujiColor.Paper, shape)
            .border(TujiBorder.Bw1, TujiColor.Rule, shape)
            .padding(
                top = if (pointsDown) TujiSpace.S3 else TujiSpace.S3 + GlossCalloutPlacement.caretHeight,
                bottom = if (pointsDown) TujiSpace.S3 + GlossCalloutPlacement.caretHeight else TujiSpace.S3,
                start = TujiSpace.S3,
                end = TujiSpace.S3,
            ),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Header(span, language, canSpeak, onSpeak, bookmarked, onBookmark)
        DefinitionLine(span, partOfSpeech)
        BaseFormRow(span)
        val wordId = span.wordId
        if (wordId != null && onOpenWord != null) DetailAction { onOpenWord(wordId) }
    }
}

@Composable
private fun Header(
    span: GlossSpan,
    language: TargetLanguage,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    bookmarked: Boolean,
    onBookmark: ((String) -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            Text(span.text, style = TujiType.h2, color = TujiColor.Ink)
            val display = span.asHeadworded(language).headwordDisplay(language)
            if (display is HeadwordDisplay.Line) {
                Text(display.text, style = TujiType.label, color = TujiColor.Ink3)
            }
        }
        // Deliberately always synthesised, never played from the catalogue.
        // `wordId` is matched on the 原形, so a 詞塊 reading `documents` carries
        // `document`'s id — and `document`'s recording, which is not what is
        // written here. The same rule the 音標 line already follows.
        if (canSpeak) {
            TujiIconButton(
                label = stringResource(R.string.word_play),
                onClick = onSpeak,
                size = BUTTON,
            ) {
                TujiGlyph.Speaker(tint = TujiColor.Ink)
            }
        }
        // 書籤 needs a catalogue word to hang on, and most 詞塊 will never be
        // one — so it keeps the same company as 看完整詳情: both or neither.
        val wordId = span.wordId
        if (wordId != null && onBookmark != null) {
            TujiIconButton(
                label = stringResource(R.string.word_bookmark),
                onClick = { onBookmark(wordId) },
                size = BUTTON,
            ) {
                TujiGlyph.Star(filled = bookmarked, tint = TujiColor.Ink)
            }
        }
    }
}

/**
 * 詞性 and 釋義 as one paragraph rather than a labelled row and a body block.
 * They are read as one sentence — 「名詞, meaning …」 — and setting them as one
 * text also lets them wrap as one.
 */
@Composable
private fun DefinitionLine(span: GlossSpan, partOfSpeech: (String) -> String) {
    val gloss = span.gloss.orEmpty()
    val pos = span.partOfSpeech?.takeIf { it.isNotBlank() }
    if (gloss.isEmpty() && pos == null) return
    Text(
        buildAnnotatedString {
            if (pos != null) {
                withStyle(
                    SpanStyle(
                        color = TujiColor.Ink3,
                        fontStyle = FontStyle.Italic,
                        fontSize = TujiType.label.fontSize,
                    ),
                ) {
                    append(partOfSpeech(pos))
                }
                append("  ")
            }
            append(gloss)
        },
        style = TujiType.body,
        color = TujiColor.Ink,
    )
}

/**
 * A base form identical to the span teaches nothing; the whole point of showing
 * it is the `running` → `run` step.
 */
@Composable
private fun BaseFormRow(span: GlossSpan) {
    val baseForm = span.baseForm?.takeIf { it.isNotBlank() && it != span.text } ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Text(stringResource(R.string.gloss_base_form), style = TujiType.label, color = TujiColor.Ink3)
        Text(baseForm, style = TujiType.bodySm, color = TujiColor.Ink2)
    }
}

/**
 * A row rather than a full-width button. The card has to fit in the gap above a
 * word, and a button that tall would push it back to the bottom of the screen
 * on most sentences — which is the one thing the caret exists to stop.
 */
@Composable
private fun DetailAction(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        Row(
            Modifier.fillMaxWidth().tujiClickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.gloss_see_detail), style = TujiType.label, color = TujiColor.Ink)
            Text("→", style = TujiType.label, color = TujiColor.Ink2)
        }
    }
}

private val BUTTON = 32.dp
