package app.tuji.android.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A row of mutually exclusive sections — iOS's `TujiSegmented`.
 *
 * Words on the paper, and the chosen one inverted to ink: no track, no pill,
 * no sliding thumb, all of which are the platform's picture of a segmented
 * control rather than this app's.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TujiSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Smaller only where long labels would not fit side by side. */
    textStyle: TextStyle = TujiType.h3,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        options.forEach { (value, title) ->
            val on = value == selected
            // The inversion is a state change, so it moves at D1. A block that
            // teleports between two words reads as a redraw; the same 120ms
            // every other selected state in the app takes reads as an answer.
            val ground by animateColorAsState(
                if (on) TujiColor.Ink else TujiColor.Ink.copy(alpha = 0f),
                TujiMotion.ease(TujiMotion.D1),
                label = "segmentGround",
            )
            val ink by animateColorAsState(
                if (on) TujiColor.Paper else TujiColor.Ink2,
                TujiMotion.ease(TujiMotion.D1),
                label = "segmentInk",
            )
            // An option past the edge comes fully into view once chosen. Only
            // on a change: the first selection is where the row starts anyway,
            // and asking then could also scroll the page it sits in.
            val requester = remember { BringIntoViewRequester() }
            val initiallyOn = remember { on }
            LaunchedEffect(on) { if (on && !initiallyOn) requester.bringIntoView() }
            Box(
                Modifier
                    .bringIntoViewRequester(requester)
                    .height(40.dp)
                    .background(ground)
                    .tujiClickable { if (!on) onSelect(value) }
                    .semantics {
                        role = Role.Tab
                        this.selected = on
                    }
                    .padding(horizontal = TujiSpace.S3),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, style = textStyle, color = ink)
            }
        }
    }
}

/** A number over its label, on ink — iOS's `TujiInkStat`. Mono, so the digits hold their width. */
@Composable
fun TujiInkStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$value", style = TujiType.monoLabel, color = TujiColor.Paper)
        Text(label, style = TujiType.label, color = TujiColor.Paper.copy(alpha = 0.6f), maxLines = 1)
    }
}
