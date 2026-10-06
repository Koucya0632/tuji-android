package app.tuji.android.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.core.model.FeedbackPayload
import app.tuji.android.core.model.FeedbackType
import app.tuji.android.form.DETAIL_LIMIT
import app.tuji.android.form.FormChoices
import app.tuji.android.form.FormDetailField
import app.tuji.android.form.FormSubmit
import app.tuji.android.form.FormSuccess
import app.tuji.android.form.TujiFormSheet
import java.util.UUID
import kotlinx.coroutines.launch

/** Where 意見收集 goes, handed in by the shell. */
class FeedbackSender(
    val appVersion: String,
    val uiLang: String,
    /** Throws on anything the server did not accept. */
    val submit: suspend (FeedbackPayload) -> Unit,
)

/**
 * 意見收集 — iOS's `FeedbackSheet`. A kind and a description, both required;
 * 謝謝 only once the server has it, and the text kept on a failure.
 */
@Composable
internal fun FeedbackSheet(sender: FeedbackSender, onDismiss: () -> Unit) {
    // Stable per opened form, so a retry after a network failure is the same
    // message to the server (request_id is UNIQUE there).
    val requestId = remember { UUID.randomUUID().toString() }
    var type by remember { mutableStateOf<FeedbackType?>(null) }
    var detail by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TujiFormSheet(
        title = stringResource(R.string.feedback_title),
        closeEnabled = !submitting,
        onClose = onDismiss,
    ) {
        if (submitted) {
            FormSuccess(stringResource(R.string.feedback_thanks), onDone = onDismiss)
            return@TujiFormSheet
        }

        val types = FeedbackType.entries
        FormChoices(
            question = stringResource(R.string.feedback_question),
            options = types.map { stringResource(it.label()) },
            selected = type?.let(types::indexOf),
            onSelect = { type = types[it] },
        )
        FormDetailField(
            label = stringResource(R.string.feedback_detail),
            placeholder = stringResource(R.string.feedback_placeholder),
            value = detail,
            onValueChange = { detail = it },
            enabled = !submitting,
        )
        val description = detail.trim()
        FormSubmit(
            title = stringResource(R.string.feedback_submit),
            submittingTitle = stringResource(R.string.study_report_submitting),
            submitting = submitting,
            enabled = type != null && description.isNotEmpty() && !submitting,
            error = if (failed) stringResource(R.string.study_report_failed) else null,
            onSubmit = {
                val chosen = type ?: return@FormSubmit
                submitting = true
                failed = false
                scope.launch {
                    val payload = FeedbackPayload(
                        requestId = requestId,
                        feedbackType = chosen.wire,
                        description = description.take(DETAIL_LIMIT),
                        platform = "android",
                        appVersion = sender.appVersion,
                        uiLang = sender.uiLang,
                    )
                    submitted = runCatching { sender.submit(payload) }.isSuccess
                    failed = !submitted
                    submitting = false
                }
            },
        )
    }
}

private fun FeedbackType.label(): Int = when (this) {
    FeedbackType.Feature -> R.string.feedback_type_feature
    FeedbackType.Bug -> R.string.feedback_type_bug
    FeedbackType.Content -> R.string.feedback_type_content
    FeedbackType.Other -> R.string.feedback_type_other
}
