package app.tuji.android.tour

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.tuji.android.AppRoute
import app.tuji.android.R
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.MascotSpeechBubble
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.tujiClickable

/**
 * A tab introducing itself the first time it is opened after the tour — iOS's
 * `TabTip`.
 *
 * The tour used to point at the tab bar and read every tab's purpose out in one
 * breath, before the reader had been to any of them. Now each tab says what it
 * is for when the reader arrives: one line from the cat, no dimming, gone on a
 * tap. 今日 has none — the tour itself happened there.
 */
object TabTip {
    /** Stored in `OnboardingStore.tabTipsSeen`; spelled once, here — and the same as iOS. */
    fun key(tab: AppRoute.Tab): String? = when (tab) {
        AppRoute.Today -> null
        AppRoute.Atlas -> "cards"
        AppRoute.Community -> "community"
        AppRoute.Me -> "me"
    }

    @StringRes
    fun text(tab: AppRoute.Tab): Int? = when (tab) {
        AppRoute.Today -> null
        AppRoute.Atlas -> R.string.tab_tip_atlas
        AppRoute.Community -> R.string.tab_tip_community
        AppRoute.Me -> R.string.tab_tip_me
    }

    /**
     * Only at a tab's root — a tip about the tab is noise over a pushed word
     * page — and never over the tour or a study session.
     */
    fun shows(
        tab: AppRoute.Tab,
        armed: Boolean,
        seen: Set<String>,
        atRoot: Boolean,
        tourRunning: Boolean,
        studyFocusActive: Boolean,
    ): Boolean {
        val key = key(tab) ?: return false
        return armed && key !in seen && atRoot && !tourRunning && !studyFocusActive
    }
}

/** The tip, sitting on the bar: a hairline above it, paper behind it. */
@Composable
fun TabTipBubble(@StringRes text: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val dismissLabel = stringResource(R.string.tab_tip_dismiss)
    Column(
        modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .tujiClickable(onClick = onDismiss)
            .semantics { onClick(label = dismissLabel) { onDismiss(); true } },
    ) {
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule.copy(alpha = 0.2f)))
        MascotSpeechBubble(
            MascotPose.Face,
            stringResource(text),
            Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
        )
    }
}
