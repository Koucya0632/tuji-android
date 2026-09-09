package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    title: String? = null,
    showsRule: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().background(TujiColor.Paper)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = TujiSpace.S4),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != TujiNavLeading.None) {
                Box(
                    Modifier
                        .size(44.dp)
                        .tujiClickable(onClick = onLeading),
                    contentAlignment = Alignment.Center,
                ) {
                    if (leading == TujiNavLeading.Close) {
                        TujiGlyph.Close(tint = TujiColor.Ink)
                    } else {
                        Text("←", style = TujiType.h3, color = TujiColor.Ink)
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
