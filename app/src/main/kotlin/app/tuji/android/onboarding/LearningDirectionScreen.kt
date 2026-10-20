package app.tuji.android.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.tuji.android.R
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.MascotSpeechBubble
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.LearningDirection

/**
 * 你想學哪種語言？ — step 1 of the run-in, a port of iOS's
 * `LearningDirectionOnboardingView`. Also shown on its own to a signed-in
 * account with no direction yet: every screen past this point is scoped to a
 * language (see `LaunchRouting`).
 *
 * Tapping a choice is the whole answer — no 下一步, because a two-option
 * question with a confirm button is two taps for one decision.
 *
 * Centred when it fits, scrollable when it does not: at large text sizes on a
 * small screen, two `Spacer`s would squeeze the options off the screen instead
 * of letting the user reach them.
 */
@Composable
fun LearningDirectionScreen(
    onPick: (LearningDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(top = insets.calculateTopPadding()),
    ) {
        OnboardingStepBar(OnboardingStep.Language)
        BoxWithConstraints(Modifier.weight(1f)) {
            val minHeight = this.maxHeight
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = TujiSpace.S4)
                    .padding(top = TujiSpace.S5, bottom = insets.calculateBottomPadding() + TujiSpace.S5),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4, Alignment.CenterVertically),
            ) {
                MascotSpeechBubble(MascotPose.Wave, stringResource(R.string.onboarding_greeting))
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    Text(
                        stringResource(R.string.onboarding_which_language),
                        style = TujiType.h1,
                        color = TujiColor.Ink,
                    )
                    Text(
                        stringResource(R.string.onboarding_which_language_detail),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    DirectionOption(
                        LearningDirection.ZH_EN,
                        glyph = "EN",
                        title = stringResource(R.string.direction_en_title),
                        detail = stringResource(R.string.direction_en_detail),
                        onPick = onPick,
                    )
                    DirectionOption(
                        LearningDirection.ZH_JA,
                        glyph = "あ",
                        title = stringResource(R.string.direction_ja_title),
                        detail = stringResource(R.string.direction_ja_detail),
                        onPick = onPick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(
    direction: LearningDirection,
    glyph: String,
    title: String,
    detail: String,
    onPick: (LearningDirection) -> Unit,
) {
    OnboardingChoice(title = title, detail = detail, onClick = { onPick(direction) }) { content ->
        // The script itself, not a flag: it is the language being learned, so
        // it does not translate with the interface.
        Text(glyph, style = TujiType.h1, color = content, modifier = Modifier.clearAndSetSemantics {})
    }
}
