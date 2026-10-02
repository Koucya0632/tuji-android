package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiNavLeading
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
    subject: () -> StudyReportSubject?,
) {
    if (state.menuOpen) {
        StudyMoreSheet(
            onReport = { state.report(subject()) },
            onDismiss = { state.menuOpen = false },
        )
    }
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

/** ⋯ — one row today, a menu so the next item has somewhere to go. */
@Composable
private fun StudyMoreSheet(onReport: () -> Unit, onDismiss: () -> Unit) = TujiWindow(onDismiss = onDismiss) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Scrim)
            .tujiClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                // Swallows taps, so touching the sheet does not dismiss it
                // through the scrim underneath.
                .tujiClickable {}
                .navigationBarsPadding()
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(
                stringResource(R.string.study_report),
                style = TujiType.body,
                color = TujiColor.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .tujiClickable(onClick = onReport)
                    .padding(vertical = TujiSpace.S2),
            )
            TujiButton(
                text = stringResource(R.string.cancel),
                style = TujiButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
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

    val close = { if (!submitting) onDismiss() }

    TujiWindow(onDismiss = close) {
        Column(
            Modifier
                .fillMaxSize()
                .background(TujiColor.Paper)
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            TujiNavBar(
                onLeading = close,
                leading = TujiNavLeading.Close,
                leadingLabel = stringResource(R.string.study_close_label),
                title = stringResource(R.string.study_report_title),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
            ) {
                if (submitted) {
                    Success(onDone = onDismiss)
                    return@Column
                }

                // With its reading: this is the screen a reader uses to say
                // the reading is wrong.
                ReportHeadword(subject.item)

                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                    Text(stringResource(R.string.study_report_question), style = TujiType.bodySmStrong, color = TujiColor.Ink)
                    StudyReportIssue.entries.forEach { type ->
                        IssueRow(
                            label = stringResource(type.label()),
                            selected = issue == type,
                            onClick = { issue = type },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Text(stringResource(R.string.study_report_detail), style = TujiType.bodySmStrong, color = TujiColor.Ink)
                    BasicTextField(
                        value = detail,
                        onValueChange = { detail = it.take(StudyReports.DETAIL_LIMIT) },
                        enabled = !submitting,
                        textStyle = TujiType.bodySm.copy(color = TujiColor.Ink),
                        cursorBrush = SolidColor(TujiColor.Current),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .background(TujiColor.Paper2)
                            .border(TujiBorder.Bw1, TujiColor.Rule)
                            .padding(TujiSpace.S3),
                        decorationBox = { inner ->
                            Box {
                                if (detail.isEmpty()) {
                                    Text(
                                        stringResource(R.string.study_report_placeholder),
                                        style = TujiType.bodySm,
                                        color = TujiColor.Ink3,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Text(
                        "${detail.length}/${StudyReports.DETAIL_LIMIT}",
                        style = TujiType.label,
                        color = TujiColor.Ink3,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (failed) {
                    Text(stringResource(R.string.study_report_failed), style = TujiType.label, color = TujiColor.Alert)
                }

                TujiButton(
                    text = stringResource(if (submitting) R.string.study_report_submitting else R.string.study_report_submit),
                    enabled = StudyReports.canSubmit(issue, detail, submitting),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val type = issue ?: return@TujiButton
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
    }
}

@Composable
private fun IssueRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) TujiColor.Current.copy(alpha = 0.18f) else TujiColor.Paper)
            .border(TujiBorder.Bw1, if (selected) TujiColor.Current else TujiColor.Rule)
            .tujiClickable(onClick = onClick)
            .padding(TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .border(TujiBorder.Bw1 * 2, if (selected) TujiColor.Ink else TujiColor.Ink3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(TujiColor.Ink, CircleShape))
        }
        Text(label, style = TujiType.bodySm, color = TujiColor.Ink)
    }
}

@Composable
private fun ReportHeadword(item: StudyQueueItem) {
    when (val display = item.word.headwordDisplay(item.word.targetLanguage ?: TargetLanguage.EN)) {
        is HeadwordDisplay.Ruby -> FuriganaHeadword(display.segments)
        is HeadwordDisplay.Line -> Column {
            Text(item.word.word, style = TujiType.h1, color = TujiColor.Ink)
            Text(display.text, style = TujiType.bodySm, color = TujiColor.Ink2)
        }
        HeadwordDisplay.Plain -> Text(item.word.word, style = TujiType.h1, color = TujiColor.Ink)
    }
}

@Composable
private fun Success(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = TujiSpace.S5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        Box(
            Modifier.size(64.dp).background(TujiColor.Accumulation, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Check(size = 28.dp, tint = TujiColor.Paper)
        }
        Text(
            stringResource(R.string.study_report_thanks),
            style = TujiType.bodySmStrong,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier)
        TujiButton(
            text = stringResource(R.string.manage_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
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
