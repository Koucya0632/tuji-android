package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.GrammarComparison
import app.tuji.android.core.model.GrammarExplanation
import app.tuji.android.core.model.GrammarLine
import app.tuji.android.core.model.GrammarPractice
import app.tuji.android.core.model.GrammarVerdict
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.study.SentenceSpeakerButton

/** What an example sentence needs to be read aloud. */
internal class GrammarLines(val target: TargetLanguage, val speech: WordSpeaking?, val accent: String)

/** One titled block of a grammar card, on the paper-2 ground the 查詞 cards use. */
@Composable
internal fun GrammarSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(TujiColor.Paper2).padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Text(title, style = TujiType.h3, color = TujiColor.Ink, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** An example sentence: the target language, its reading, the translation and what it shows. */
@Composable
private fun GrammarLineView(line: GrammarLine, lines: GrammarLines) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
            Text(line.text, style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.weight(1f))
            SentenceSpeakerButton(line.text, lines.target, lines.speech, lines.accent)
        }
        line.reading?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
        line.translation?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
        line.note?.let { Text(it, style = TujiType.label, color = TujiColor.Ink2) }
    }
}

/** 語法解釋: 語法摘要, 結構拆解, 規則說明, 典型例句, 使用場景. */
@Composable
internal fun GrammarExplanationCard(value: GrammarExplanation, lines: GrammarLines) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(value.summary.name, style = TujiType.h2, color = TujiColor.Ink)
                Text(value.summary.meaning, style = TujiType.bodyStrong, color = TujiColor.Ink)
                Text(value.summary.explanation, style = TujiType.body, color = TujiColor.Ink)
                value.summary.highlight?.let {
                    Text(
                        stringResource(R.string.grammar_highlight, it),
                        style = TujiType.label,
                        color = TujiColor.Ink,
                        modifier = Modifier.background(TujiColor.Current).padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
                    )
                }
            }
            GrammarSection(stringResource(R.string.grammar_section_structure)) {
                Text(
                    value.structure.formula,
                    style = TujiType.bodyStrong,
                    color = TujiColor.Ink,
                    modifier = Modifier.fillMaxWidth().background(TujiColor.Paper).padding(TujiSpace.S3),
                )
                value.structure.parts.forEach {
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(it.part, style = TujiType.bodySmStrong, color = TujiColor.Ink)
                        Text(it.role, style = TujiType.bodySm, color = TujiColor.Ink)
                    }
                }
                value.structure.breakdown?.let { Labelled(stringResource(R.string.grammar_breakdown), it) }
                value.structure.forms?.let { Labelled(stringResource(R.string.grammar_forms), it) }
            }
            GrammarSection(stringResource(R.string.grammar_section_rules)) {
                value.rules.forEachIndexed { index, rule ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                        Text("${index + 1}.", style = TujiType.bodyStrong, color = TujiColor.Ink)
                        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                            Text(rule.title, style = TujiType.bodyStrong, color = TujiColor.Ink)
                            Text(rule.explanation, style = TujiType.bodySm, color = TujiColor.Ink)
                        }
                    }
                }
            }
            GrammarSection(stringResource(R.string.grammar_section_examples)) {
                value.examples.forEach { GrammarLineView(it, lines) }
            }
            GrammarSection(stringResource(R.string.grammar_section_scenes)) {
                value.scenes.forEach { scene ->
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(scene.situation, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        Marked(true, scene.use, TujiColor.Ink)
                        scene.avoid?.let { Marked(false, it, TujiColor.Ink2) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Labelled(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Text(title, style = TujiType.label, color = TujiColor.Ink3)
        Text(text, style = TujiType.bodySm, color = TujiColor.Ink)
    }
}

/** A ✓ or ✕ beside a line, so the mark is a shape and not colour alone. */
@Composable
private fun Marked(good: Boolean, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp)) {
            if (good) TujiGlyph.Check(size = 12.dp, tint = color) else TujiGlyph.Close(size = 12.dp, tint = color)
        }
        Text(text, style = TujiType.bodySm, color = color)
    }
}

/**
 * 近似語法: 比較摘要, 近義對比, 異同辨析, 誤用預警, 替換建議. A over B, never a
 * wide table: a phone reads the two sides one under the other.
 */
@Composable
internal fun GrammarComparisonCard(value: GrammarComparison, lines: GrammarLines) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text("${value.a.name} ／ ${value.b.name}", style = TujiType.h2, color = TujiColor.Ink)
                Text(value.summary, style = TujiType.body, color = TujiColor.Ink)
                value.chosenBecause?.let {
                    Text(stringResource(R.string.grammar_chosen_because, value.b.name, it), style = TujiType.label, color = TujiColor.Ink2)
                }
            }
            GrammarSection(stringResource(R.string.grammar_section_similarity)) {
                Text(value.similarity.shared, style = TujiType.body, color = TujiColor.Ink)
                Side("A", value.a.name, value.similarity.aFocus)
                Side("B", value.b.name, value.similarity.bFocus)
                Verdict(value.similarity.interchangeable)
            }
            GrammarSection(stringResource(R.string.grammar_section_dimensions)) {
                value.dimensions.forEach { dimension ->
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(dimension.aspect, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        Side("A", null, dimension.a)
                        Side("B", null, dimension.b)
                    }
                }
            }
            GrammarSection(stringResource(R.string.grammar_section_pitfalls)) {
                value.pitfalls.forEach { pitfall ->
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(
                            stringResource(pitfallTitle(pitfall.kind)),
                            style = TujiType.label,
                            color = TujiColor.Ink,
                            modifier = Modifier.border(1.dp, TujiColor.Ink3).padding(horizontal = TujiSpace.S1),
                        )
                        val wrong = stringResource(R.string.grammar_wrong, pitfall.wrong)
                        Row(
                            Modifier.clearAndSetSemantics { contentDescription = wrong },
                            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                        ) {
                            Box(Modifier.padding(top = 5.dp)) { TujiGlyph.Close(size = 12.dp, tint = TujiColor.Ink2) }
                            Text(pitfall.wrong, style = TujiType.body.copy(textDecoration = TextDecoration.LineThrough), color = TujiColor.Ink2)
                        }
                        val fixed = stringResource(R.string.grammar_fixed, pitfall.fixed)
                        Row(
                            Modifier.clearAndSetSemantics { contentDescription = fixed },
                            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                        ) {
                            Box(Modifier.padding(top = 5.dp)) { TujiGlyph.Check(size = 12.dp, tint = TujiColor.Ink) }
                            Text(pitfall.fixed, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        }
                        Text(pitfall.reason, style = TujiType.bodySm, color = TujiColor.Ink)
                    }
                }
            }
            GrammarSection(stringResource(R.string.grammar_section_substitutions)) {
                value.substitutions.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                        Text(item.situation, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        Tagged("A") { GrammarLineView(item.a, lines) }
                        Tagged("B") { GrammarLineView(item.b, lines) }
                        Verdict(item.verdict)
                        Text(item.explanation, style = TujiType.bodySm, color = TujiColor.Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun Side(tag: String, name: String?, text: String) = Tagged(tag) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        name?.let { Text(it, style = TujiType.bodySmStrong, color = TujiColor.Ink) }
        Text(text, style = TujiType.bodySm, color = TujiColor.Ink)
    }
}

@Composable
private fun Tagged(tag: String, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).background(TujiColor.Paper).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Text(tag, style = TujiType.label, color = TujiColor.Ink)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Verdict(verdict: GrammarVerdict) {
    Row(
        Modifier
            .background(if (verdict == GrammarVerdict.Interchangeable) TujiColor.Current else TujiColor.Paper)
            .padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (verdict) {
            GrammarVerdict.Interchangeable -> TujiGlyph.Check(size = 12.dp)
            GrammarVerdict.Conditional -> TujiGlyph.Warning(size = 14.dp)
            GrammarVerdict.Not -> TujiGlyph.Close(size = 12.dp)
        }
        Text(stringResource(verdictTitle(verdict)), style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}

private fun verdictTitle(verdict: GrammarVerdict): Int = when (verdict) {
    GrammarVerdict.Interchangeable -> R.string.grammar_verdict_interchangeable
    GrammarVerdict.Conditional -> R.string.grammar_verdict_conditional
    GrammarVerdict.Not -> R.string.grammar_verdict_not
}

private fun pitfallTitle(kind: GrammarComparison.Pitfall.Kind): Int = when (kind) {
    GrammarComparison.Pitfall.Kind.Grammar -> R.string.grammar_pitfall_grammar
    GrammarComparison.Pitfall.Kind.Meaning -> R.string.grammar_pitfall_meaning
    GrammarComparison.Pitfall.Kind.Register -> R.string.grammar_pitfall_register
    GrammarComparison.Pitfall.Kind.Unnatural -> R.string.grammar_pitfall_unnatural
}

// MARK: - 語法練習

/**
 * 語法練習 inside its card — iOS's `GrammarPracticeView`: one question at a
 * time, the answer and reasons hidden until 確認答案, and a review at the end.
 * Nothing here spends points.
 */
@Composable
internal fun GrammarPracticeCard(session: GrammarPracticeSession) {
    val state by session.state.collectAsStateWithLifecycle()
    val practice = session.practice
    Column(Modifier.testTag("grammar-practice"), verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(practice.focus, style = TujiType.h2, color = TujiColor.Ink)
            Text(
                "${stringResource(difficultyTitle(practice.difficulty))} · ${stringResource(R.string.grammar_question_count, practice.questions.size)}",
                style = TujiType.label,
                color = TujiColor.Ink2,
            )
        }
        when (state.screen) {
            GrammarPracticeSession.Screen.Overview -> Overview(session, state)
            GrammarPracticeSession.Screen.Question -> QuestionScreen(session, state)
            GrammarPracticeSession.Screen.Summary -> Summary(session, state)
            GrammarPracticeSession.Screen.Review -> Review(session, state)
        }
    }
}

@Composable
private fun Overview(session: GrammarPracticeSession, state: GrammarPracticeSession.State) {
    GrammarSection(stringResource(R.string.grammar_mode_practice)) {
        val unfinished = state.unfinished
        Text(
            if (unfinished != null) stringResource(R.string.grammar_answered, unfinished.answers.size, unfinished.questionIds.size)
            else stringResource(R.string.grammar_practice_intro),
            style = TujiType.bodySm,
            color = TujiColor.Ink,
        )
        Primary(
            stringResource(if (unfinished == null) R.string.grammar_practice_start else R.string.grammar_practice_continue),
            Modifier.testTag("grammar-practice-start"),
        ) { session.start() }
        Secondary(stringResource(R.string.grammar_practice_review)) { session.review() }
        if (unfinished == null && session.firstScore == null) {
            Text(stringResource(R.string.grammar_practice_review_note), style = TujiType.label, color = TujiColor.Ink3)
        }
    }
}

@Composable
private fun QuestionScreen(session: GrammarPracticeSession, state: GrammarPracticeSession.State) {
    val question = session.question ?: return
    val confirmed = state.confirmed
    val title = session.position?.let { (index, total) ->
        stringResource(
            if (state.attempt?.isFirst == false) R.string.grammar_practice_redo_position else R.string.grammar_practice_position,
            index,
            total,
        )
    }.orEmpty()
    GrammarSection(title) {
        Text(stringResource(questionKind(question.kind)), style = TujiType.label, color = TujiColor.Ink3)
        question.context?.let {
            Text(it, style = TujiType.bodySm, color = TujiColor.Ink, modifier = Modifier.fillMaxWidth().background(TujiColor.Paper).padding(TujiSpace.S3))
        }
        Text(question.prompt, style = TujiType.bodyStrong, color = TujiColor.Ink)
        question.options.forEach { option -> OptionRow(option, question, confirmed, state.selection, session) }
        if (confirmed != null) {
            Feedback(question, confirmed)
            val last = state.attempt?.let { a -> a.questionIds.all { a.answers[it] != null } } ?: true
            Primary(
                stringResource(if (last) R.string.grammar_practice_results else R.string.grammar_practice_next),
                Modifier.testTag("grammar-practice-next"),
            ) { session.next() }
        } else {
            Primary(
                stringResource(R.string.grammar_practice_confirm),
                Modifier.testTag("grammar-practice-confirm").alpha(if (state.selection == null) 0.5f else 1f),
                enabled = state.selection != null,
            ) { session.confirm() }
        }
    }
}

@Composable
private fun OptionRow(
    option: GrammarPractice.Question.Option,
    question: GrammarPractice.Question,
    confirmed: String?,
    selection: String?,
    session: GrammarPracticeSession,
) {
    val selected = (confirmed ?: selection) == option.id
    val isAnswer = option.id == question.answer
    // After checking, the right answer and a wrong pick each say so in words
    // and a mark, not colour alone.
    val mark = when {
        confirmed == null -> null
        isAnswer -> true to stringResource(R.string.grammar_correct_answer)
        selected -> false to stringResource(R.string.grammar_your_answer)
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (confirmed != null && isAnswer) TujiColor.Current else TujiColor.Paper)
            .border(if (selected) 2.dp else 1.dp, if (selected) TujiColor.Ink else TujiColor.Rule)
            // Not disabled once confirmed: that would grey out the very answer being explained.
            .tujiClickable(enabled = confirmed == null) { session.select(option.id) }
            .semantics { this.selected = selected }
            .padding(TujiSpace.S3)
            .testTag("grammar-option-${option.id}"),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.Top,
    ) {
        Text(option.id.uppercase(), style = TujiType.bodySmStrong, color = TujiColor.Ink, modifier = Modifier.width(20.dp))
        Text(option.text, style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.weight(1f))
        mark?.let { (right, label) ->
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (right) TujiGlyph.Check(size = 14.dp) else TujiGlyph.Close(size = 14.dp)
                Text(label, style = TujiType.label, color = TujiColor.Ink)
            }
        }
    }
}

@Composable
private fun Feedback(question: GrammarPractice.Question, chosen: String) {
    val right = chosen == question.answer
    Column(
        Modifier.fillMaxWidth().background(TujiColor.Paper).padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Row(
            Modifier.testTag("grammar-practice-verdict"),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (right) TujiGlyph.Check(size = 14.dp) else TujiGlyph.Close(size = 14.dp)
            Text(stringResource(if (right) R.string.grammar_right else R.string.grammar_wrong_answer), style = TujiType.bodyStrong, color = TujiColor.Ink)
        }
        Text(question.solution, style = TujiType.body, color = TujiColor.Ink)
        question.options.forEach { option ->
            question.reason(option.id)?.let { reason ->
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Text(option.id.uppercase(), style = TujiType.label, color = TujiColor.Ink, modifier = Modifier.width(20.dp))
                    Text(
                        reason,
                        style = TujiType.bodySm,
                        color = if (option.id == question.answer || option.id == chosen) TujiColor.Ink else TujiColor.Ink2,
                    )
                }
            }
        }
        Text(stringResource(R.string.grammar_point, question.point), style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}

@Composable
private fun Summary(session: GrammarPracticeSession, state: GrammarPracticeSession.State) {
    GrammarSection(stringResource(R.string.grammar_practice_summary)) {
        session.firstScore?.let { score ->
            if (score.answered < score.total) {
                Text(stringResource(R.string.grammar_first_answered, score.answered, score.total), style = TujiType.h3, color = TujiColor.Ink)
            } else {
                Text(
                    stringResource(R.string.grammar_first_correct, score.correct, score.total),
                    style = TujiType.h3,
                    color = TujiColor.Ink,
                    modifier = Modifier.testTag("grammar-practice-score"),
                )
            }
        }
        state.attempt?.takeIf { !it.isFirst }?.let { attempt ->
            Text(
                stringResource(R.string.grammar_redo_correct, session.correct(attempt), attempt.questionIds.size),
                style = TujiType.bodySm,
                color = TujiColor.Ink,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(stringResource(R.string.grammar_takeaways), style = TujiType.label, color = TujiColor.Ink3)
            session.practice.takeaways.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp).size(5.dp).background(TujiColor.Ink))
                    Text(line, style = TujiType.bodySm, color = TujiColor.Ink)
                }
            }
        }
        state.attempt?.let { session.wrong(it) }?.takeIf { it.isNotEmpty() }?.let { wrong ->
            Primary(stringResource(R.string.grammar_redo_wrong, wrong.size), Modifier.testTag("grammar-practice-redo-wrong")) {
                session.redoWrong()
            }
        }
        Secondary(stringResource(R.string.grammar_redo_all)) { session.redoAll() }
        Secondary(stringResource(R.string.grammar_practice_review)) { session.review() }
        Text(stringResource(R.string.grammar_free_note), style = TujiType.label, color = TujiColor.Ink3)
    }
}

@Composable
private fun Review(session: GrammarPracticeSession, state: GrammarPracticeSession.State) {
    GrammarSection(stringResource(R.string.grammar_review_title)) {
        session.practice.questions.forEachIndexed { index, question ->
            Column(Modifier.padding(bottom = TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(stringResource(R.string.grammar_question_number, index + 1), style = TujiType.label, color = TujiColor.Ink3)
                question.context?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink) }
                Text(question.prompt, style = TujiType.bodyStrong, color = TujiColor.Ink)
                val correctLabel = stringResource(R.string.grammar_correct_answer)
                question.options.forEach { option ->
                    val isAnswer = option.id == question.answer
                    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 3.dp)
                                .size(14.dp)
                                .then(if (isAnswer) Modifier.background(TujiColor.Ink) else Modifier.border(1.dp, TujiColor.Ink3))
                                .semantics { if (isAnswer) contentDescription = correctLabel },
                            contentAlignment = Alignment.Center,
                        ) { if (isAnswer) TujiGlyph.Check(size = 10.dp, tint = TujiColor.Paper) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.text, style = TujiType.bodySmStrong, color = TujiColor.Ink)
                            question.reason(option.id)?.let { Text(it, style = TujiType.label, color = TujiColor.Ink2) }
                        }
                    }
                }
                Text(stringResource(R.string.grammar_point, question.point), style = TujiType.bodySm, color = TujiColor.Ink)
            }
        }
        Primary(
            stringResource(if (state.attempt?.finished == true) R.string.grammar_back_to_results else R.string.grammar_back_to_practice),
        ) { session.showOverview() }
    }
}

@Composable
private fun Primary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodyStrong,
        color = TujiColor.Ink,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(TujiColor.Current)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
    )
}

@Composable
private fun Secondary(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodySmStrong.copy(textDecoration = TextDecoration.Underline),
        color = TujiColor.Ink,
        modifier = Modifier.heightIn(min = 44.dp).tujiClickable(onClick = onClick).padding(vertical = TujiSpace.S3),
    )
}

private fun questionKind(kind: GrammarPractice.Question.Kind): Int =
    if (kind == GrammarPractice.Question.Kind.Choice) R.string.grammar_type_choice else R.string.grammar_type_context
