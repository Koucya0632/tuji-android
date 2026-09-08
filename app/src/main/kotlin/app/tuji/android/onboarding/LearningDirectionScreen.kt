package app.tuji.android.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRadius
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.LearningDirection
import androidx.compose.ui.unit.dp

/**
 * 想學哪一種語言？ — the first screen of a first launch.
 *
 * A port of `LearningDirectionOnboardingView`. It comes **before** Welcome and
 * before the intro, and it gates every account state: every screen past this
 * point is scoped to a language, so an app without one has nowhere to land
 * (see `LaunchRouting`).
 *
 * Scrollable rather than centred-with-spacers for the reason iOS gives: at
 * large text sizes on a small screen, two `Spacer`s squeeze the content off
 * the screen instead of letting the user reach it.
 */
@Composable
fun LearningDirectionScreen(
    onPick: (LearningDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = TujiType
    val insets = WindowInsets.systemBars.asPaddingValues()

    // Centred when it fits, scrollable when it does not — the direct port of
    // iOS's `Spacer / content / Spacer` inside a ScrollView with
    // `.frame(minHeight: geo.size.height)`. At large text sizes on a small
    // screen the two Spacers would otherwise squeeze the options off-screen,
    // which is the specific failure that comment on iOS is about.
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(TujiColor.Paper),
    ) {
        val minHeight = this.maxHeight
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.Center,
        ) {
        Spacer(Modifier.height(insets.calculateTopPadding() + TujiSpace.S5))

        MascotFigure(
            pose = MascotPose.Wave,
            size = 112.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(TujiSpace.S4))

        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Text(
                stringResource(R.string.onboarding_which_language),
                style = type.h2,
                color = TujiColor.Ink,
            )
            Text(
                stringResource(R.string.onboarding_which_language_detail),
                style = type.bodySm,
                color = TujiColor.Ink3,
            )
        }

        Spacer(Modifier.height(TujiSpace.S4))

        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            DirectionOption(
                direction = LearningDirection.ZH_EN,
                badge = "EN",
                title = stringResource(R.string.direction_en_title),
                subtitle = stringResource(R.string.direction_en_detail),
                onPick = onPick,
            )
            DirectionOption(
                direction = LearningDirection.ZH_JA,
                badge = "日",
                title = stringResource(R.string.direction_ja_title),
                subtitle = stringResource(R.string.direction_ja_detail),
                onPick = onPick,
            )
        }

        Spacer(Modifier.height(insets.calculateBottomPadding() + TujiSpace.S5))
        }
    }
}

@Composable
private fun DirectionOption(
    direction: LearningDirection,
    badge: String,
    title: String,
    subtitle: String,
    onPick: (LearningDirection) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper, RoundedCornerShape(TujiRadius.R0))
            .border(
                TujiBorder.Bw1,
                TujiColor.Rule.copy(alpha = 0.2f),
                RoundedCornerShape(TujiRadius.R0),
            )
            .tujiClickable { onPick(direction) }
            .padding(TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A circle, which the radius scale allows for exactly three things —
        // and this is none of them. It is here because iOS draws it, and a
        // divergence in the first screen is where 對等 starts to rot.
        Box(
            Modifier
                .size(48.dp)
                .background(TujiColor.BrandSecondary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, style = TujiType.h3, color = TujiColor.BrandSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TujiType.h3, color = TujiColor.Ink)
            Text(subtitle, style = TujiType.label, color = TujiColor.Ink3)
        }
        Text("›", style = TujiType.h3, color = TujiColor.Ink3)
    }
}
