package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiHeadwordSize
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyReportPayload
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.headwordDisplay
import app.tuji.android.core.study.StudyReportIssue
import app.tuji.android.core.study.StudyReportSubject
import app.tuji.android.core.study.StudyReports
import app.tuji.android.form.FormChoices
import app.tuji.android.form.FormDetailField
import app.tuji.android.form.FormSubmit
import app.tuji.android.form.FormSuccess
import app.tuji.android.form.TujiFormSheet
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Where a session's 報錯 goes, handed in by the shell. Null on a screen means
 * no ⋯ at all — which is what a test or a preview wants.
 */
class StudyReporter(
    val appVersion: String,
    val uiLang: String,
    /** Throws on anything the server did not accept. */
    val submit: suspend (StudyReportPayload) -> Unit,
)

/**
 * The 報錯 path a session owns: ⋯ → 報錯 → the form, or the 自製卡片 notice.
 * iOS's `StudySessionShell.report`, shared by 複習 and 學新字 so the two cannot
 * drift apart.
 */
@Stable
internal class StudyReportState {
    var menuOpen by mutableStateOf(false)
    var customCardNotice by mutableStateOf(false)
    var draft by mutableStateOf<StudyReportSubject?>(null)

    /**
     * 報錯 from the menu. The subject is read at the tap, not when ⋯ opened —
     * the card under the menu is the one being reported.
     */
    fun report(subject: StudyReportSubject?) {
        menuOpen = false
        val s = subject ?: return
        if (StudyReports.accepts(s.item)) draft = s else customCardNotice = true
    }
}

/** Everything the 報錯 path draws over a session. */
@Composable
internal fun StudyReportLayer(
    state: StudyReportState,
    mode: StudyMode,
    reporter: StudyReporter,
) {
    if (state.customCardNotice) {
        TujiPrompt(
            title = stringResource(R.string.study_report_custom_title),
            message = stringResource(R.string.study_report_custom_message),
            confirm = stringResource(R.string.study_report_custom_ok),
            cancel = null,
            onConfirm = { state.customCardNotice = false },
            onCancel = { state.customCardNotice = false },
        )
    }
    state.draft?.let { draft ->
        StudyReportSheet(
            subject = draft,
            mode = mode,
            reporter = reporter,
            onDismiss = { state.draft = null },
        )
    }
}

/**
 * ⋯'s menu, dropped from the ⋯ itself — iOS's `Menu`, which opens where it was
 * tapped rather than rising from the bottom edge. Draw it inside the box that
 * holds ⋯ so it anchors there.
 */
@Composable
internal fun StudyReportMenu(state: StudyReportState, subject: () -> StudyReportSubject?) {
    if (!state.menuOpen) return
    // Resolved out here: a popup is a view of its own and would read the
    // device's language rather than the app's (see `TujiWindow`).
    val label = stringResource(R.string.study_report)
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 44.dp.roundToPx()) },
        onDismissRequest = { state.menuOpen = false },
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .widthIn(min = 200.dp)
                .background(TujiColor.Paper)
                .border(TujiBorder.Bw1, TujiColor.Rule),
        ) {
            Text(
                label,
                style = TujiType.body,
                color = TujiColor.Ink,
                modifier = Modifier
                    .tujiClickable { state.report(subject()) }
                    .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            )
        }
    }
}

/**
 * 回報學習問題 — iOS's `StudyReportSheet`. A kind and a description, both
 * required; 謝謝 only once the server has the report, and the text kept on a
 * failure so a retry costs nothing.
 */
@Composable
private fun StudyReportSheet(
    subject: StudyReportSubject,
    mode: StudyMode,
    reporter: StudyReporter,
    onDismiss: () -> Unit,
) {
    // One id per opened form, so a retry after a timeout is the same report.
    val requestId = remember { UUID.randomUUID().toString() }
    var issue by remember { mutableStateOf<StudyReportIssue?>(null) }
    var detail by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TujiFormSheet(
        title = stringResource(R.string.study_report_title),
        closeEnabled = !submitting,
        onClose = onDismiss,
    ) {
        if (submitted) {
            FormSuccess(stringResource(R.string.study_report_thanks), onDone = onDismiss)
            return@TujiFormSheet
        }

        // With its reading: this is the screen a reader uses to say the
        // reading is wrong.
        ReportHeadword(subject.item)

        val issues = StudyReportIssue.entries
        FormChoices(
            question = stringResource(R.string.study_report_question),
            options = issues.map { stringResource(it.label()) },
            selected = issue?.let(issues::indexOf),
            onSelect = { issue = issues[it] },
        )
        FormDetailField(
            label = stringResource(R.string.study_report_detail),
            placeholder = stringResource(R.string.study_report_placeholder),
            value = detail,
            onValueChange = { detail = it },
            enabled = !submitting,
            limit = StudyReports.DETAIL_LIMIT,
        )
        FormSubmit(
            title = stringResource(R.string.study_report_submit),
            submittingTitle = stringResource(R.string.study_report_submitting),
            submitting = submitting,
            enabled = StudyReports.canSubmit(issue, detail, submitting),
            error = if (failed) stringResource(R.string.study_report_failed) else null,
            onSubmit = {
                val type = issue ?: return@FormSubmit
                submitting = true
                failed = false
                scope.launch {
                    val payload = StudyReports.payload(
                        requestId = requestId,
                        subject = subject,
                        mode = mode,
                        issue = type,
                        detail = detail,
                        appVersion = reporter.appVersion,
                        uiLang = reporter.uiLang,
                    )
                    submitted = runCatching { reporter.submit(payload) }.isSuccess
                    failed = !submitted
                    submitting = false
                }
            },
        )
    }
}

/** iOS's `TujiHeadword`: ruby when there is a reading to set, else the word alone. */
@Composable
private fun ReportHeadword(item: StudyQueueItem) {
    val language = item.word.targetLanguage ?: TargetLanguage.EN
    when (val display = item.word.headwordDisplay(language)) {
        is HeadwordDisplay.Ruby -> FuriganaHeadword(display.segments)
        else -> Text(
            item.word.word,
            style = TujiType.headword(TujiHeadwordSize.Base),
            color = TujiColor.Ink,
            maxLines = if (language == TargetLanguage.JA) 1 else 2,
        )
    }
}

private fun StudyReportIssue.label(): Int = when (this) {
    StudyReportIssue.Image -> R.string.study_report_issue_image
    StudyReportIssue.Content -> R.string.study_report_issue_content
    StudyReportIssue.Audio -> R.string.study_report_issue_audio
    StudyReportIssue.Answer -> R.string.study_report_issue_answer
    StudyReportIssue.Ui -> R.string.study_report_issue_ui
    StudyReportIssue.Other -> R.string.study_report_issue_other
}
