package app.tuji.android.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.core.study.ThemeStatus

/**
 * What a theme's state looks like, wherever a theme is drawn.
 *
 * 圖鑑's cover tile and 今日's text tile are two different shapes for the same
 * fact, and iOS keeps them apart for a stated reason — 今日 wants two rows of
 * themes in the height a cover gives one. **What they share, they share by
 * name**, which is this: the accent and the frame it is drawn at.
 *
 * The edge carries the claim, so a finished theme is legible in a grid without
 * reading any of them: 墨 for 全精通, 積累 for 完成, 紙3 otherwise.
 */
val ThemeStatus.accent: Color
    get() = when (this) {
        ThemeStatus.Mastered -> TujiColor.Ink
        ThemeStatus.Completed -> TujiColor.Accumulation
        ThemeStatus.None -> TujiColor.Paper3
    }

/** An unstarted theme gets the hairline; a claim gets twice it. */
val ThemeStatus.frameWidth: Dp
    get() = if (this == ThemeStatus.None) 1.dp else 2.dp

/** The badge's own ink, against [accent] as its ground. */
val ThemeStatus.onAccent: Color
    get() = if (this == ThemeStatus.Mastered) TujiColor.Current else TujiColor.Paper
