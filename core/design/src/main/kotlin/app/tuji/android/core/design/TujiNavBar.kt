package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** What the leading control does. */
enum class TujiNavLeading { Back, Close, None }

/**
 * The bar a pushed screen draws for itself.
 *
 * Drawn in the content rather than by the platform, the way iOS does it: a
 * system toolbar puts its own chrome over a study screen, and this app's
 * screens already say what they are.
 *
 * [onLeading] **replaces** dismissal rather than running alongside it — a
 * screen that must ask before it goes (leaving a review mid-way) passes the
 * question here instead of leaving and apologising.
 */
@Composable
fun TujiNavBar(
    onLeading: () -> Unit,
    modifier: Modifier = Modifier,
    leading: TujiNavLeading = TujiNavLeading.Back,
    leadingLabel: String? = null,
    title: String? = null,
    showsRule: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = TujiSpace.S4),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != TujiNavLeading.None) {
                // The icon sits on the page margin, not inset from it, so every
                // screen's first element starts on the same vertical line; the
                // 48dp target reaches back into the gutter to get there.
                Box(
                    Modifier
                        .offset(x = -TujiSpace.S3)
                        .size(48.dp)
                        .tujiClickable(onClick = onLeading)
                        .semantics { leadingLabel?.let { contentDescription = it } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (leading == TujiNavLeading.Close) {
                        TujiGlyph.Close(tint = TujiColor.Ink)
                    } else {
                        TujiGlyph.ArrowLeft(tint = TujiColor.Ink)
                    }
                }
            }
            title?.let {
                Text(it, style = TujiType.h3, color = TujiColor.Ink, maxLines = 1)
            }
            Box(Modifier.weight(1f))
            trailing?.invoke()
        }
        if (showsRule) {
            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        }
    }
}

/**
 * A 44×48 icon action for the bar's trailing edge — 搜尋, 更多, 書籤. At most
 * two, never a row of them.
 */
@Composable
fun TujiNavIcon(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(width = 44.dp, height = 48.dp)
            .tujiClickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/**
 * The screen's real title, in the content flow under the bar rather than in it.
 * With the title moved out of the bar it can be set large, and that contrast
 * against the quiet 56dp above it is where the page gets its rhythm.
 */
@Composable
fun TujiScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TujiType.h1,
        color = TujiColor.Ink,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S2, bottom = TujiSpace.S4)
            .semantics { heading() },
    )
}
