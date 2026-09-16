package app.tuji.android.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import kotlinx.coroutines.launch

/**
 * The three pages before 歡迎, on the very first launch.
 *
 * `LaunchRouting` has routed here since the launch module was written, and
 * `TujiRoot` drew 歡迎 for it — so Android opened on a sign-in wall with no
 * word about what the app is. iOS has never done that.
 *
 * Swipeable, with 跳過 on the first two pages and 開始使用 on the last; either
 * way out sets `introDone`, which is what lets routing move to 歡迎.
 *
 * The artwork is deliberately made of this app's own parts — tiles, option
 * rows, a heatmap — rather than illustration. iOS's version says in its own
 * comment that its SF Symbols are placeholders; these are the same three
 * subjects drawn the way the app draws everything else.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val last = pager.currentPage == PAGES.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
            // 跳過 disappears on the last page rather than turning into
            // something else: by then the only thing left to do is the button
            // underneath, and two ways out of one page is one too many.
            if (!last) {
                Text(
                    stringResource(R.string.intro_skip),
                    style = TujiType.bodySmStrong,
                    color = TujiColor.Ink3,
                    modifier = Modifier
                        .tujiClickable(onClick = onDone)
                        .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
                )
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            IntroPage(PAGES[index])
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = TujiSpace.S3),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PAGES.indices.forEach { i ->
                val on = i == pager.currentPage
                // The current page's dot is a **bar**, not a bigger dot: three
                // dots of two sizes is a reading test, and the stretch says
                // "you are here" while it is still moving.
                val width by animateDpAsState(
                    if (on) 22.dp else 7.dp,
                    TujiMotion.easeInOut(INDICATOR_MS),
                    label = "introDot",
                )
                Box(
                    Modifier
                        .size(width = width, height = 7.dp)
                        .background(if (on) TujiColor.Current else TujiColor.Paper2),
                )
            }
        }

        TujiButton(
            text = stringResource(if (last) R.string.intro_start else R.string.intro_next),
            onClick = {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TujiSpace.S4)
                .padding(bottom = TujiSpace.S5),
        )
    }
}

private class IntroPageSpec(
    val artwork: Artwork,
    val mascot: MascotPose?,
    val title: Int,
    val lines: List<Int>,
) {
    enum class Artwork { Grid, Srs, Streak }
}

private val PAGES = listOf(
    IntroPageSpec(
        artwork = IntroPageSpec.Artwork.Grid,
        mascot = MascotPose.Face,
        title = R.string.intro_p1_title,
        lines = listOf(R.string.intro_p1_a, R.string.intro_p1_b),
    ),
    IntroPageSpec(
        artwork = IntroPageSpec.Artwork.Srs,
        mascot = null,
        title = R.string.intro_p2_title,
        lines = listOf(R.string.intro_p2_a, R.string.intro_p2_b),
    ),
    IntroPageSpec(
        artwork = IntroPageSpec.Artwork.Streak,
        mascot = MascotPose.Wave,
        title = R.string.intro_p3_title,
        lines = listOf(R.string.intro_p3_a, R.string.intro_p3_b),
    ),
)

@Composable
private fun IntroPage(page: IntroPageSpec) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S4)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (page.artwork == IntroPageSpec.Artwork.Streak) {
                            Modifier
                        } else {
                            Modifier.border(TujiBorder.Bw1, TujiColor.Rule)
                        },
                    )
                    .padding(if (page.artwork == IntroPageSpec.Artwork.Streak) 0.dp else TujiSpace.S4),
            ) {
                when (page.artwork) {
                    IntroPageSpec.Artwork.Grid -> GridArtwork()
                    IntroPageSpec.Artwork.Srs -> SrsArtwork()
                    IntroPageSpec.Artwork.Streak -> StreakArtwork()
                }
            }
            page.mascot?.let {
                // Straddling the corner rather than sitting inside it, the way
                // the cat straddles 今日's card — iOS offsets it (14, −20) past
                // the frame's edge for the same reason.
                MascotFigure(
                    pose = it,
                    size = 96.dp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-20).dp),
                )
            }
        }
        Text(
            stringResource(page.title),
            style = TujiType.h2,
            color = TujiColor.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TujiSpace.S2, start = TujiSpace.S4, end = TujiSpace.S4),
        )
        Column(
            Modifier.padding(top = TujiSpace.S2, start = TujiSpace.S4, end = TujiSpace.S4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            page.lines.forEach {
                Text(stringResource(it), style = TujiType.body, color = TujiColor.Ink2, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 用圖學語言: four picture tiles, which is what the whole app looks like. */
@Composable
private fun GridArtwork() {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        listOf(
            listOf<@Composable (Dp) -> Unit>(
                { TujiGlyph.Fork(size = it, tint = TujiColor.BrandSecondary) },
                { TujiGlyph.Cup(size = it, tint = TujiColor.BrandSecondary) },
            ),
            listOf<@Composable (Dp) -> Unit>(
                { TujiGlyph.Leaf(size = it, tint = TujiColor.BrandSecondary) },
                { TujiGlyph.Carrot(size = it, tint = TujiColor.BrandSecondary) },
            ),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                row.forEach { glyph ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(TujiColor.BrandSecondary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        glyph(40.dp)
                    }
                }
            }
        }
    }
}

/** 每天 3 分鐘: the 選字 stage, mid-answer. */
@Composable
private fun SrsArtwork() {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(TujiColor.BrandSecondary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            TujiGlyph.Carrot(size = 40.dp, tint = TujiColor.BrandSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            OptionStub("lettuce", picked = false)
            OptionStub("carrot", picked = true)
            OptionStub("cucumber", picked = false)
        }
    }
}

/**
 * @param word a vocabulary word being *illustrated*, not interface copy — these
 *   rows mock up 選字, so "carrot" stays "carrot" in every interface language.
 *   Named `word` rather than `text` to say so, which is also what keeps the
 *   localisation check from asking for it.
 */
@Composable
private fun OptionStub(word: String, picked: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (picked) TujiColor.Current.copy(alpha = 0.18f) else TujiColor.Paper)
            .then(if (picked) Modifier else Modifier.border(TujiBorder.Bw1, TujiColor.Rule))
            .padding(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(word, style = TujiType.bodySmStrong, color = TujiColor.Ink2)
        Spacer(Modifier.weight(1f))
        if (picked) TujiGlyph.Check(size = 14.dp, tint = TujiColor.Ink)
    }
}

/** 看見自己變強: the streak and the heatmap, on ink. */
@Composable
private fun StreakArtwork() {
    Column(
        Modifier.fillMaxWidth().background(TujiColor.Ink).padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TujiGlyph.Flame(size = 16.dp, tint = TujiColor.Accumulation)
            Text(
                stringResource(R.string.intro_streak),
                style = TujiType.label,
                color = TujiColor.Paper.copy(alpha = 0.7f),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Text(stringResource(R.string.intro_streak_days), style = TujiType.display, color = TujiColor.Paper)
            Text(
                stringResource(R.string.intro_streak_unit),
                style = TujiType.h3,
                color = TujiColor.Paper.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(7) { col ->
                        // Fixed, not random: an illustration that changes every
                        // recomposition is a flicker, not a heatmap.
                        val strength = ((row * 7 + col) * 7 + 3) % 10
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    TujiColor.Paper.copy(
                                        alpha = when {
                                            strength < 3 -> 0.12f
                                            strength < 6 -> 0.35f
                                            else -> 0.85f
                                        },
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** iOS: `.easeOut(duration: 0.25)` on the indicator. */
private const val INDICATOR_MS = 250
