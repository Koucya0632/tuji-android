package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.tuji.android.core.study.StudyOptionState

/**
 * The colours for one option row.
 *
 * The *decision* is `StudyOptionState` in `core:study`, where it is tested;
 * this is only what each verdict looks like.
 *
 * Ink inversion and a 3dp edge are **shape** differences, not colour-only ones,
 * so the language survives colour blindness. 積累 marks the right one rather
 * than a new green: the palette has six meanings and none of them is green.
 */
val StudyOptionState.ground: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Ink
        else -> TujiColor.Paper2
    }

val StudyOptionState.foreground: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Paper
        StudyOptionState.Dim -> TujiColor.Ink2
        else -> TujiColor.Ink
    }

val StudyOptionState.letterGround: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Current
        else -> TujiColor.Paper3
    }

val StudyOptionState.letterForeground: Color
    get() = when (this) {
        StudyOptionState.Right, StudyOptionState.Answer -> TujiColor.Ink
        else -> TujiColor.Ink2
    }

/**
 * Which row the user tapped, and how it went. [StudyOptionState.Answer]
 * deliberately has none: it is the answer they did *not* pick, and the ink
 * ground already says so — a frame there would claim a tap that never happened.
 */
val StudyOptionState.borderColor: Color?
    get() = when (this) {
        StudyOptionState.Right -> TujiColor.Accumulation
        StudyOptionState.Wrong -> TujiColor.Alert
        else -> null
    }

/** The unrelated options recede rather than disappear — the user may reread them. */
val StudyOptionState.dimAlpha: Float
    get() = if (this == StudyOptionState.Dim) 0.4f else 1f

@Composable
fun StudyOptionRow(
    label: String,
    letter: String,
    state: StudyOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescriptionText: String? = null,
) {
    val shape = RoundedCornerShape(TujiRadius.R0)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(state.dimAlpha)
            .defaultMinSize(minHeight = 60.dp)
            .background(state.ground, shape)
            .then(
                state.borderColor?.let { Modifier.border(TujiBorder.Bw3, it, shape) } ?: Modifier
            )
            .tujiClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TujiSpace.S3)
            .semantics { stateDescriptionText?.let { stateDescription = it } },
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(state.letterGround, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = TujiType.label, color = state.letterForeground)
        }
        Text(label, style = TujiType.bodyStrong, color = state.foreground)
    }
}
