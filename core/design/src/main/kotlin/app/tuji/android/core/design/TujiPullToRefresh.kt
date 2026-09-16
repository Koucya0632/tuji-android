package app.tuji.android.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Pull down to re-read the page.
 *
 * Material's own indicator is a circle that spins inside a raised pill — a
 * spinner, an elevation and a lozenge, which is three of the things 紙與墨 rules
 * out in one control. So only the *gesture* is Material's; what it draws is the
 * app's own 3dp rule, and there is nothing new to invent because the two states
 * already have marks:
 *
 * - **being pulled** — [TujiProgressBar] filling with the finger. The gesture
 *   has a threshold, so "this far" is exactly what a progress rule means, and
 *   it is the one place that bar must *not* animate: 120ms of catching up
 *   reads as the bar resisting the thumb.
 * - **refreshing** — [TujiIndeterminateBar] sweeping. The wait has no known
 *   end, which is the whole reason that bar exists.
 *
 * [onRefresh] is suspending and the flag is held here, so a screen says what to
 * re-read and nothing about when it is over. Four screens writing their own
 * `var refreshing` is four chances to leave it true on a thrown exception.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TujiPullToRefresh(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                // `finally`, because a refresh that throws must still let go of
                // the gesture. A stuck indicator is a screen that can never be
                // pulled again.
                try {
                    onRefresh()
                } finally {
                    refreshing = false
                }
            }
        },
        state = state,
        modifier = modifier,
        indicator = {
            Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                val pulled = state.distanceFraction
                when {
                    refreshing -> TujiIndeterminateBar()
                    pulled > 0f -> TujiProgressBar(
                        progress = pulled.coerceIn(0f, 1f).toDouble(),
                        animated = false,
                    )
                }
            }
        },
        content = content,
    )
}
