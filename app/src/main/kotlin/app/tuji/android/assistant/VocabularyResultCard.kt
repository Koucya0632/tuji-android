package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.VocabularyAnalysis
import app.tuji.android.core.model.VocabularyCandidate
import app.tuji.android.core.model.VocabularyContent
import app.tuji.android.core.model.VocabularyHeadword
import app.tuji.android.core.model.VocabularyLine
import app.tuji.android.core.model.VocabularyLookup
import app.tuji.android.core.model.VocabularyMnemonic
import app.tuji.android.core.model.VocabularyMode
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.study.SentenceSpeakerButton

/** Moira's card — iOS's `VocabularyResultView`: one of 查詞, 深度解析 or 助記, section by section. */
@Composable
fun VocabularyResultCard(
    content: VocabularyContent,
    target: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    choose: (VocabularyCandidate, VocabularyMode) -> Unit,
) {
    val scope = CardScope(target, speech, accent, choose)
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
            when (content) {
                is VocabularyLookup -> scope.Lookup(content)
                is VocabularyAnalysis -> {
                    scope.Headword(content.headword)
                    scope.Analysis(content)
                }
                is VocabularyMnemonic -> {
                    scope.Headword(content.headword)
                    scope.Mnemonic(content)
                }
            }
        }
    }
}

private class CardScope(
    val target: TargetLanguage,
    val speech: WordSpeaking?,
    val accent: String,
    val choose: (VocabularyCandidate, VocabularyMode) -> Unit,
) {
    @Composable
    fun Headword(value: VocabularyHeadword) {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(value.text, style = TujiType.h2, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                SentenceSpeakerButton(value.text, target, speech, accent)
            }
            value.reading?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
            Text(value.partOfSpeech, style = TujiType.label, color = TujiColor.Ink3)
            Text(value.meaning, style = TujiType.body, color = TujiColor.Ink)
        }
    }

    @Composable
    fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Text(title, style = TujiType.h3, color = TujiColor.Ink, modifier = Modifier.semantics { heading() })
            content()
        }
    }

    @Composable
    fun Line(value: VocabularyLine) {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(value.text, style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                SentenceSpeakerButton(value.text, target, speech, accent)
            }
            value.reading?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
            value.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
            value.note?.let { Text(it, style = TujiType.label, color = TujiColor.Ink2) }
        }
    }

    @Composable
    fun NextSteps(candidate: VocabularyCandidate) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            Action(stringResource(R.string.vocabulary_analyze)) { choose(candidate, VocabularyMode.Analysis) }
            Action(stringResource(R.string.vocabulary_remember)) { choose(candidate, VocabularyMode.Mnemonic) }
        }
    }

    @Composable
    fun Action(text: String, onClick: () -> Unit) {
        Text(
            text,
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink,
            modifier = Modifier.heightIn(min = 44.dp).tujiClickable(onClick = onClick).padding(vertical = TujiSpace.S3),
        )
    }

    @Composable
    fun NothingToAdd() = Text(stringResource(R.string.vocabulary_nothing_to_add), style = TujiType.bodySm, color = TujiColor.Ink)

    @Composable
    fun Lookup(value: VocabularyLookup) {
        Section(stringResource(R.string.vocabulary_section_quick)) { Headword(value.headword) }
        Section(stringResource(R.string.vocabulary_section_senses)) {
            value.senses.forEach { sense ->
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    if (sense.relevant) {
                        Text(stringResource(R.string.vocabulary_relevant_sense), style = TujiType.label, color = TujiColor.Ink3)
                    }
                    Text(sense.term, style = TujiType.bodyStrong, color = TujiColor.Ink)
                    Text(sense.meaning, style = TujiType.body, color = TujiColor.Ink)
                    Text(sense.explanation, style = TujiType.bodySm, color = TujiColor.Ink)
                    NextSteps(VocabularyCandidate(sense.term, sense.meaning))
                }
            }
        }
        Section(stringResource(R.string.vocabulary_section_expressions)) { value.expressions.forEach { Line(it) } }
        Section(stringResource(R.string.vocabulary_section_related)) {
            if (value.related.isEmpty()) NothingToAdd()
            value.related.forEach {
                Text(it.term, style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(it.relation, style = TujiType.bodySm, color = TujiColor.Ink)
            }
        }
    }

    @Composable
    fun Analysis(value: VocabularyAnalysis) {
        Section(stringResource(R.string.vocabulary_section_comparisons)) {
            if (value.comparisons.isEmpty()) NothingToAdd()
            value.comparisons.forEach { item ->
                Text(item.term, style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(item.difference, style = TujiType.body, color = TujiColor.Ink)
                Text(item.whenToUse, style = TujiType.bodySm, color = TujiColor.Ink)
                item.examples.forEach { Line(it) }
            }
        }
        Section(stringResource(R.string.vocabulary_section_usage)) {
            value.usage.forEach {
                Text(it.title, style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(it.explanation, style = TujiType.bodySm, color = TujiColor.Ink)
            }
        }
        Section(stringResource(R.string.vocabulary_section_examples)) { value.examples.forEach { Line(it) } }
        Section(stringResource(R.string.vocabulary_section_collocations)) {
            value.collocations.forEach {
                Text(it.phrase, style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(it.meaning, style = TujiType.bodySm, color = TujiColor.Ink)
                Text(it.pattern, style = TujiType.label, color = TujiColor.Ink2)
            }
        }
        NextSteps(VocabularyCandidate(value.headword.text, value.headword.meaning))
    }

    @Composable
    fun Mnemonic(value: VocabularyMnemonic) {
        Section(stringResource(R.string.vocabulary_section_association)) {
            Text(value.association, style = TujiType.body, color = TujiColor.Ink)
        }
        Section(stringResource(R.string.vocabulary_section_technique)) {
            Text(value.technique, style = TujiType.body, color = TujiColor.Ink)
        }
        Section(stringResource(R.string.vocabulary_section_retelling)) {
            Text(value.retelling, style = TujiType.bodyStrong, color = TujiColor.Ink)
            Line(value.example)
        }
        Section(stringResource(R.string.vocabulary_section_quiz)) {
            var hinting by rememberSaveable { mutableStateOf(false) }
            var revealed by rememberSaveable { mutableStateOf(false) }
            Text(value.quiz.question, style = TujiType.body, color = TujiColor.Ink)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).tujiClickable { hinting = !hinting },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.vocabulary_quiz_hint), style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.weight(1f))
                TujiGlyph.ChevronUp(
                    tint = TujiColor.Ink3,
                    modifier = Modifier.rotate(if (hinting) 0f else 180f),
                )
            }
            if (hinting) Text(value.quiz.hint, style = TujiType.bodySm, color = TujiColor.Ink)
            Action(stringResource(if (revealed) R.string.vocabulary_quiz_hide else R.string.vocabulary_quiz_reveal)) {
                revealed = !revealed
            }
            if (revealed) {
                Text(
                    value.quiz.answer,
                    style = TujiType.body,
                    color = TujiColor.Ink,
                    modifier = Modifier.testTag("vocabulary-quiz-answer"),
                )
            }
        }
    }
}
