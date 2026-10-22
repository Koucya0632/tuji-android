package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.BlockList
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasSearching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 物見搜尋 — iOS's `AtlasSearchModel`.
 *
 * Server-only, unlike 搜尋: 物見 is other people's work and none of it is on
 * the device, so there is no local half to show while the request is out. One
 * debounced request answers both shelves, 合集 and 單字卡片.
 *
 * Blocked authors are filtered here the way [CommunityViewModel] filters its
 * shelves: the raw answer is kept, so a block made from a pushed page applies
 * on the way back without asking again.
 */
class CommunitySearchViewModel(
    private val remote: AtlasSearching,
    private val direction: LearningDirection,
    /** Read at call time: the 物見 tab's list, which a block elsewhere changes. */
    private val blocks: () -> BlockList,
    private val debounceMillis: Long = DEBOUNCE_MS,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    enum class Phase { Idle, Loading, Ready, Failed }

    data class Results(
        /** What these rows answer — not what has been typed since. */
        val query: String = "",
        val phase: Phase = Phase.Idle,
        val collections: List<AtlasPublicCollection> = emptyList(),
        val items: List<AtlasPublicItem> = emptyList(),
    ) {
        val isEmpty: Boolean get() = collections.isEmpty() && items.isEmpty()
    }

    private val _results = MutableStateFlow(Results())
    val results: StateFlow<Results> = _results.asStateFlow()

    private var raw = Results()
    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var pending: Job? = null

    private val lang: String
        get() = if (direction.targetLanguage == TargetLanguage.JA) "ja" else "en"

    fun query(text: String) = start(text, debounceMillis)

    /** 重試, with nothing to wait for. */
    fun queryNow(text: String) = start(text, 0L)

    /** Re-apply the block list — after a block or unblock on a pushed page. */
    fun refilter() {
        _results.value = filtered(raw)
    }

    private fun start(text: String, wait: Long) {
        pending?.cancel()
        val q = normalized(text)
        if (q.isEmpty()) {
            raw = Results()
            _results.value = raw
            return
        }
        // Rows already on screen stay while the next answer is fetched.
        raw = raw.copy(query = q, phase = Phase.Loading)
        _results.value = filtered(raw)
        pending = work.launch {
            if (wait > 0) delay(wait)
            fetch(q)
        }
    }

    private suspend fun fetch(q: String) {
        val response = try {
            remote.search(q, lang)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "物見 search '$q' failed", failure)
            if (raw.query != q) return
            raw = Results(query = q, phase = Phase.Failed)
            _results.value = raw
            return
        }
        // The user kept typing. This answers a question no longer on screen.
        if (raw.query != q) return
        raw = Results(query = q, phase = Phase.Ready, collections = response.collections, items = response.items)
        _results.value = filtered(raw)
    }

    private fun filtered(r: Results): Results {
        val list = blocks()
        return r.copy(
            collections = list.filter(r.collections) { it.author },
            items = list.filter(r.items) { it.author },
        )
    }

    companion object {
        private const val TAG = "TujiCommunitySearch"

        /** iOS's window. */
        const val DEBOUNCE_MS = 300L

        /** The backend caps `q` at 40 and 400s past it; a long paste is trimmed instead. */
        const val MAX_QUERY = 40

        fun normalized(text: String): String = text.trim().take(MAX_QUERY)
    }
}
