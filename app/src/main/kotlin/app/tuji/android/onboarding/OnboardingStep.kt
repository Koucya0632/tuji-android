package app.tuji.android.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStepIndicator
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * The run-in a new user walks before 今日: pick a language, make an account —
 * iOS's `OnboardingStep`.
 *
 * The account's first settings are written without asking ([AutoSetupScreen]),
 * and the feature tour takes over inside the app, so neither is a step here.
 * There used to be three marketing pages between the language and the account
 * and a themes-and-goal picker after it; every one of those was a cost paid
 * before the first word (D.11.4).
 */
enum class OnboardingStep { Language, Account }

/** The D.10 step bar, cut into the onboarding's steps. */
@Composable
fun OnboardingStepBar(step: OnboardingStep, modifier: Modifier = Modifier) {
    TujiStepIndicator(
        total = OnboardingStep.entries.size,
        current = step.ordinal,
        modifier = modifier.padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S3),
    )
}

/**
 * A full-width choice on paper2 that inverts to an ink block when chosen
 * (D.11.4). The language step advances on tap, so the press is the only moment
 * its selection is ever visible — pressed reads as chosen.
 */
@Composable
fun OnboardingChoice(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: @Composable RowScope.(contentColor: androidx.compose.ui.graphics.Color) -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val inverted = selected || pressed
    val background by animateColorAsState(
        if (inverted) TujiColor.Ink else TujiColor.Paper2,
        TujiMotion.ease(TujiMotion.D1),
        label = "choice",
    )
    val content = if (inverted) TujiColor.Paper else TujiColor.Ink
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(background)
            .tujiClickable(interactionSource = source, onClick = onClick)
            .padding(horizontal = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(title, style = TujiType.h2, color = content)
            Text(detail, style = TujiType.bodySm, color = content.copy(alpha = 0.72f))
        }
        trailing(content)
    }
}
