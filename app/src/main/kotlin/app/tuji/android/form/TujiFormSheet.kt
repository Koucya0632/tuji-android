package app.tuji.android.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable

/**
 * iOS's `TujiFormSheet`: a full-height sheet for a *task* — a form to fill in —
 * rather than a choice. A 3dp ink edge, the title on the left, ✕ on the right,
 * and the content scrolling under it.
 *
 * 報錯 and 意見收集 are the same form with different words, so the pieces they
 * share live here and not in either: two copies are how the two drift apart.
 *
 * [closeEnabled] covers system back too — a ✕ that refuses while back still
 * dismisses is not protecting anything.
 */
@Composable
internal fun TujiFormSheet(
    title: String,
    closeEnabled: Boolean,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val close = { if (closeEnabled) onClose() }
    TujiWindow(onDismiss = close) {
        Column(
            Modifier
                .fillMaxSize()
                .background(TujiColor.Paper)
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
        ) {
            TujiSheetHeader(title = title, closeEnabled = closeEnabled, onClose = close)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
                content = content,
            )
        }
    }
}

/** A question over its options, as both forms ask it. */
@Composable
internal fun FormChoices(
    question: String,
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Text(question, style = TujiType.bodySmStrong, color = TujiColor.Ink)
        options.forEachIndexed { index, label ->
            ChoiceRow(label = label, selected = selected == index, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
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

/** The required description box and its count. Capped at [limit], as iOS trims at 1000. */
@Composable
internal fun FormDetailField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    limit: Int = DETAIL_LIMIT,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Text(label, style = TujiType.bodySmStrong, color = TujiColor.Ink)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(limit)) },
            enabled = enabled,
            textStyle = TujiType.body.copy(color = TujiColor.Ink),
            cursorBrush = SolidColor(TujiColor.Current),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .background(TujiColor.Paper)
                .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.25f))
                .padding(TujiSpace.S3),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = TujiType.bodySm, color = TujiColor.Ink3)
                    }
                    inner()
                }
            },
        )
        Text(
            "${value.length}/$limit",
            style = TujiType.label,
            color = TujiColor.Ink3,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The error line and the submit button under a form. */
@Composable
internal fun FormSubmit(
    title: String,
    submittingTitle: String,
    submitting: Boolean,
    enabled: Boolean,
    error: String?,
    onSubmit: () -> Unit,
) {
    error?.let { Text(it, style = TujiType.label, color = TujiColor.Alert) }
    TujiButton(
        text = if (submitting) submittingTitle else title,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        onClick = onSubmit,
    )
}

/** 謝謝 — only once the server has it. */
@Composable
internal fun FormSuccess(message: String, onDone: () -> Unit) {
    Column(
        // The form's own S4 inset is already above this, so together they make
        // iOS's S5 from the header.
        Modifier.fillMaxWidth().padding(top = TujiSpace.S5 - TujiSpace.S4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        Box(
            Modifier.size(64.dp).background(TujiColor.Accumulation, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Check(size = 28.dp, tint = TujiColor.Paper)
        }
        Text(message, style = TujiType.bodySmStrong, color = TujiColor.Ink, textAlign = TextAlign.Center)
        TujiButton(
            text = stringResource(R.string.manage_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Both forms' description cap. */
internal const val DETAIL_LIMIT = 1000
