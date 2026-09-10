package app.tuji.android.atlas

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.MasteryReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every word's score, once, for everyone who draws a badge.
 *
 * Four surfaces read it — 圖鑑's tiles, 主題's completion badge, 單字詳情's bar,
 * and (when it exists) 我的's distribution — and they must agree. A tile saying
 * 精通 beside a detail page saying 熟練 is the kind of bug that looks like a
 * server problem for a week.
 *
 * **No TTL, deliberately.** [CatalogStore] is loaded once because the catalogue
 * does not change; this is loaded once for a sharper reason. Decay is applied
 * server-side at read, but a score only *moves* when **this** user answers
 * something, and every path that answers something ends in a session that can
 * say so. A time-based refresh would re-fetch the whole map on every tab swap
 * to buy a number that cannot have changed.
 *
 * The cost of that choice, stated rather than discovered: a session on another
 * device does not show up here until this one is relaunched. That is the same
 * trade iOS makes.
 *
 * **A failed load keeps what it had.** Emptying the map on a dropped connection
 * would silently demote every word on screen to 未學 — a wrong answer that
 * looks exactly like a true one.
 */
class MasteryStore(private val progress: MasteryReading) : AccountScopedStore {

    data class Scores(
        val byId: Map<String, Int> = emptyMap(),
        /** Word id → next review, epoch millis. Only scheduled cards appear. */
        val nextReviewById: Map<String, Long> = emptyMap(),
        /** True once a load has *succeeded*, so a failure does not look loaded. */
        val loaded: Boolean = false,
    ) {
        /** Null means no `user_words` row at all — 未學, not zero. */
        fun score(wordId: String): Int? = byId[wordId]

        fun nextReview(wordId: String): Long? = nextReviewById[wordId]
    }

    private val _scores = MutableStateFlow(Scores())
    val scores: StateFlow<Scores> = _scores.asStateFlow()

    private val gate = Mutex()

    /**
     * Fill it once per direction.
     *
     * [force] is what a finished study session passes: the scores it just moved
     * are the whole reason the user might look.
     */
    suspend fun load(learning: LearningDirection, force: Boolean = false) {
        gate.withLock {
            if (_scores.value.loaded && !force) return
            val items = try {
                progress.mastery(learning).items
            } catch (cancelled: CancellationException) {
                // `runCatching` would have caught this too, and that is the
                // trap: a cancelled load is not a failed one, and swallowing
                // it breaks the caller's structured concurrency. It also
                // logged 「載入失敗」 for a coroutine that was merely torn down,
                // which is how the self-cancelling effect above hid for a while.
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "mastery load failed — keeping the last scores", failure)
                return
            }
            Log.i(TAG, "loaded ${items.size} mastery rows (force=$force)")
            _scores.value = Scores(
                byId = items.associate { it.wordId to it.mastery },
                nextReviewById = items.mapNotNull { entry ->
                    entry.nextReviewAt?.let { parseIsoMillis(it) }?.let { entry.wordId to it }
                }.toMap(),
                loaded = true,
            )
        }
    }

    /** Drop what was fetched for the old deck — the two score separately. */
    fun retune() {
        _scores.value = Scores()
    }

    /**
     * Sign-out. The same clearing as [retune], for a different reason: these
     * scores belong to the account that just left, and the next one's badges
     * must not start as theirs.
     */
    override fun reset() = retune()

    private companion object {
        const val TAG = "TujiMastery"
    }
}

/**
 * Epoch millis from the server's ISO-8601, or null.
 *
 * `Instant.parse` handles the fractional seconds the server emits, but null is
 * still the answer for anything it does not: one unparseable timestamp must
 * cost that word its countdown, not the whole map its scores.
 */
internal fun parseIsoMillis(iso: String): Long? =
    runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
