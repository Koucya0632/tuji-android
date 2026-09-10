package app.tuji.android.account

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.CategoryProgress
import app.tuji.android.core.model.HeatmapCell
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyStreak
import app.tuji.android.core.network.ProgressReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The streak, the heatmap and the per-theme rows.
 *
 * **Refetched on appearance, unlike the mastery map.** These numbers move
 * without the user doing anything: the streak turns over at midnight, the
 * heatmap gains a column, and 「目前連勝 5 天」 left on screen from yesterday is
 * a claim about today that nobody made. A score, by contrast, can only change
 * when this account answers something — which is why that store has no TTL and
 * this one simply asks again.
 *
 * **A failed load keeps what it had.** Zeroing the streak because one request
 * timed out tells a user on a 40-day run that they broke it.
 */
class ProgressStore(private val progress: ProgressReading) : AccountScopedStore {

    data class Snapshot(
        val streak: StudyStreak? = null,
        val heatmap: List<HeatmapCell> = emptyList(),
        val categories: List<CategoryProgress> = emptyList(),
        /** True once a load has *succeeded* — a failure must not look loaded. */
        val loaded: Boolean = false,
    ) {
        /** The theme's (seen, total), for [app.tuji.android.core.study.ThemeStatus]. */
        fun seenAndTotal(categoryId: String): Pair<Int, Int>? =
            categories.firstOrNull { it.category == categoryId }?.let { it.seen to it.total }

        val activeDays: Int get() = heatmap.count { it.count > 0 }
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val gate = Mutex()

    suspend fun load(learning: LearningDirection) {
        gate.withLock {
            val response = try {
                progress.progress(learning)
            } catch (cancelled: CancellationException) {
                // Not `runCatching`: it catches this too, so a torn-down load
                // would be logged as a failed one and the caller's cancellation
                // would never propagate.
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "progress load failed — keeping the last snapshot", failure)
                return
            }
            Log.i(TAG, "loaded progress: ${response.categories.size} themes")
            _snapshot.value = Snapshot(
                streak = response.streak,
                heatmap = response.heatmap,
                categories = response.categories,
                loaded = true,
            )
        }
    }

    /** Drop what was fetched for the old deck — the two progress separately. */
    fun retune() {
        _snapshot.value = Snapshot()
    }

    /**
     * Sign-out. The same clearing as [retune], for a different reason: a
     * streak is the strongest personal claim this app makes, and showing the
     * previous account's to whoever signs in next is the worst version of it.
     */
    override fun reset() = retune()

    private companion object {
        const val TAG = "TujiProgress"
    }
}
