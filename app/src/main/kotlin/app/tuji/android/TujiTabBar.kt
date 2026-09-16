package app.tuji.android

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.tuji.android.core.design.MascotEye
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.tour.TourTarget
import app.tuji.android.tour.tourAnchor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.design.tujiClickable

/**
 * The tab bar — flush to the bottom, full width, ink.
 *
 * iOS's `TujiTabBar`. A strip of ink along the bottom of every screen presses
 * the page onto the paper, and the ink runs on under the gesture handle rather
 * than stopping at the inset and leaving a strip of paper below it.
 *
 * Selection is a 3dp 瞳黃 bar on the item's top edge — not a pill and not a
 * dot, both of which are rounded vocabulary this app does not use.
 */
@Composable
fun TujiTabBar(
    selected: AppRoute.Tab?,
    bottomInset: Dp,
    onSelect: (AppRoute.Tab) -> Unit,
    onCapture: () -> Unit,
) {
    LightGestureHandle()
    val haptics = rememberTujiHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Ink)
            .tourAnchor(TourTarget.TabBar)
            .padding(bottom = bottomInset),
    ) {
        TabShell.tabs.forEach { tab ->
            TabButton(
                tab = tab,
                selected = selected == tab,
                onClick = {
                    if (selected != tab) {
                        haptics.soft()
                        onSelect(tab)
                    }
                },
            )
            if (tab == TabShell.captureFollows) CaptureButton(onCapture)
        }
    }
}

@Composable
private fun RowScope.TabButton(tab: AppRoute.Tab, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(labelOf(tab))
    val tint = if (selected) TujiColor.Paper else TujiColor.Paper.copy(alpha = 0.6f)
    Box(
        Modifier
            .weight(1f)
            .height(64.dp)
            .tujiClickable(onClick = onClick)
            // One element that says the tab's name, and nothing else: the icon
            // and the label would otherwise be announced as two things.
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            },
    ) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TujiBorder.Bw3)
                    .background(TujiColor.BrandPrimary),
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            TabIcon(tab, tint)
            Text(
                label,
                // No tracking, unlike every other label: the +0.5sp is a Latin
                // adjustment, and on a full-width CJK glyph it only buys width.
                style = TujiType.label.copy(letterSpacing = 0.sp),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TabIcon(tab: AppRoute.Tab, tint: Color) = when (tab) {
    AppRoute.Today -> TujiGlyph.Sun(tint = tint)
    AppRoute.Atlas -> TujiGlyph.Books(tint = tint)
    AppRoute.Community -> TujiGlyph.Binoculars(tint = tint)
    AppRoute.Me -> TujiGlyph.Person(tint = tint)
}

/**
 * 拍照, in the middle of the bar — the mascot's eye as a shutter.
 *
 * Not a fifth tab: it is something you do, not a place you are. Its ground
 * stays ink like its neighbours' so the eye is the only figure, and the whole
 * slot is the target rather than just the 44dp circle.
 */
@Composable
private fun RowScope.CaptureButton(onClick: () -> Unit) {
    val label = stringResource(R.string.nav_capture)
    Box(
        Modifier
            .weight(1f)
            .height(64.dp)
            .tujiClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        // The anchor is the eye, not the 64dp target around it: what the
        // tour frames is the one circle in the app, and a round hole cut to
        // the target's bounds would be a circle round nothing.
        MascotEye(size = 44.dp, modifier = Modifier.tourAnchor(TourTarget.Capture))
    }
}

private fun labelOf(tab: AppRoute.Tab): Int = when (tab) {
    AppRoute.Today -> R.string.nav_today
    AppRoute.Atlas -> R.string.nav_atlas
    AppRoute.Community -> R.string.nav_community
    AppRoute.Me -> R.string.nav_me
}

/**
 * The gesture handle drawn light while the bar is on screen.
 *
 * `enableEdgeToEdge` picks a dark handle for a light theme, which on the ink
 * bar is a dark line on a dark ground — the handle disappears, and with it the
 * only visible hint that the bottom edge is a gesture. Restored when the bar
 * leaves, because the study flows end on paper.
 */
@Composable
private fun LightGestureHandle() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightNavigationBars = false
        onDispose { before?.let { controller.isAppearanceLightNavigationBars = it } }
    }
}
