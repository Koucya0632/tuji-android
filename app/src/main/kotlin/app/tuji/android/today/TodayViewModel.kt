package app.tuji.android.today

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.StudyStatsReading
import app.tuji.android.core.study.TodayDecisions
import app.tuji.android.core.study.TodayInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 今日's numbers.
 *
 * It holds [TodayInputs] and hands out a [TodayDecisions] over them: the
 * verdicts are pure and tested next door, and this owns only the fetch and when
 * to repeat it.
 *
 * **A failed fetch leaves the inputs as they were.** Stats a minute stale still
 * describe the day; stats replaced by null would put the screen back to
 * 「正在看今天的進度…」 and grey both buttons, which is a worse answer than the
 * one it already had.
 */
class TodayViewModel(
    private val stats: StudyStatsReading,
    private val direction: LearningDirection,
    private val isGuest: () -> Boolean,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    private val _inputs = MutableStateFlow(TodayInputs(isGuest = isGuest()))
    val inputs: StateFlow<TodayInputs> = _inputs.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun refresh() {
        work.launch {
            val loaded = runCatching { stats.stats(direction).stats }.getOrElse {
                Log.w(TAG, "stats refresh failed", it)
                return@launch
            }
            _inputs.value = _inputs.value.copy(isGuest = isGuest(), stats = loaded)
        }
    }

    private companion object {
        const val TAG = "TujiToday"
    }
}
