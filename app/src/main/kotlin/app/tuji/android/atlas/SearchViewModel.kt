package app.tuji.android.atlas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.catalog.SearchMerge
import app.tuji.android.core.catalog.WordSearch
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.WordSearching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 搜尋 — local first, then whatever the server saw as well.
 *
 * The local half already existed and is not moving: [WordSearch] filters the
 * rows the app is holding, so a keystroke and its results are the same frame
 * and the screen keeps working on a plane. What it cannot see is what the
 * client never downloaded — 別名 and the full definitions — and those are the
 * matches that make someone type a word and conclude 圖鑑 does not have it.
 *
 * So the request **supplements**, and three rules follow from that:
 *
 *  - it is debounced, because the local list is already on screen and nobody is
 *    waiting for this;
 *  - an answer to a query the user has moved on from is dropped, whichever way
 *    it came back;
 *  - a failure changes nothing the user can see unless there was nothing to
 *    show. A dropped connection is not a reason to throw away answers the
 *    device already had.
 */
class SearchViewModel(
    private val remote: WordSearching,
    /**
     * The catalogue, read at call time rather than captured: it arrives after
     * this model is built, and a copy taken in the constructor is empty for
     * exactly as long as the user is most likely to be typing.
     */
    private val local: () -> List<Word>,
    private val lang: String,
    private val direction: LearningDirection,
    private val debounceMillis: Long = DEBOUNCE_MS,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class Results(
        /** What these rows answer — not what the user has typed since. */
        val query: String = "",
        val words: List<Word> = emptyList(),
        val searching: Boolean = false,
        /** Only ever true when there is nothing on screen to keep instead. */
        val failed: Boolean = false,
    )

    private val _results = MutableStateFlow(Results())
    val results: StateFlow<Results> = _results.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    private var pending: Job? = null

    fun query(text: String) {
        pending?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            // Not "everything": a field with nothing typed into it has not
            // asked for the whole dictionary.
            _results.value = Results()
            return
        }
        val hits = WordSearch.matches(trimmed, local())
        _results.value = Results(query = trimmed, words = hits, searching = true)
        pending = work.launch {
            delay(debounceMillis)
            supplement(trimmed)
        }
    }

    private suspend fun supplement(query: String) {
        val response = try {
            remote.search(query, lang, direction)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "search '$query' failed", failure)
            // One read of the state, one write: re-reading it between the two
            // is how a stale answer overwrites a fresh local list.
            val current = _results.value
            if (current.query != query) return
            _results.value = current.copy(
                searching = false,
                failed = current.words.isEmpty(),
            )
            return
        }
        val current = _results.value
        // The user kept typing. This answers a question no longer on screen.
        if (current.query != query) return
        // Re-matching locally rather than reusing `current.words`: the
        // catalogue may have finished loading during the debounce, and the
        // rows it added belong above the server's.
        _results.value = current.copy(
            words = SearchMerge.merge(WordSearch.matches(query, local()), response.results),
            searching = false,
            failed = false,
        )
    }

    private companion object {
        const val TAG = "TujiSearch"

        /** iOS's window. Long enough that a typed word is one request. */
        const val DEBOUNCE_MS = 250L
    }
}
