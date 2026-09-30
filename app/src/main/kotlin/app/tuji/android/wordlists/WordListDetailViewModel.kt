package app.tuji.android.wordlists

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.WordListDetailResponse
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.WordListsAccess
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One 個人詞表 on screen — iOS's `WordListDetailModel`: its words, its counts,
 * and what this account may do with it, all as the server answered.
 *
 * Removing a word and deleting the list are never gated (a refund must not
 * trap data); the rest follow `canEdit` / `canStudy`.
 */
class WordListDetailViewModel(
    private val listId: String,
    private val remote: WordListsAccess,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data object Failed : State

        /** Deleted on another device: the screen leaves. */
        data object Missing : State
        data class Loaded(val detail: WordListDetailResponse) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun load() {
        work.launch { refresh() }
    }

    suspend fun refresh() {
        try {
            _state.value = State.Loaded(remote.wordList(listId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.Http) {
            if (e.status == 404) _state.value = State.Missing else fail(e)
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun fail(e: Exception) {
        Log.w(TAG, "word list load failed: $listId", e)
        // What was on screen stays; only a first load shows the failure.
        if (_state.value !is State.Loaded) _state.value = State.Failed
    }

    /** Optimistic; restored if the server refuses. */
    suspend fun remove(wordId: String): MemberWriteOutcome {
        val before = (_state.value as? State.Loaded)?.detail ?: return MemberWriteOutcome.Done
        _state.value = State.Loaded(before.copy(wordIds = before.wordIds - wordId))
        return try {
            remote.setWordInList(listId, wordId, present = false)
            // Counts are the server's to compute (they are per deck).
            refresh()
            MemberWriteOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = State.Loaded(before)
            MemberWriteOutcome.from(e)
        }
    }

    companion object {
        private const val TAG = "TujiWordList"

        /**
         * Which study buttons to offer. A mode with nothing in it is not
         * offered: an empty session is a dead end.
         */
        fun studyModes(detail: WordListDetailResponse): List<StudyMode> {
            if (!detail.canStudy) return emptyList()
            return buildList {
                if (detail.stats.unseen > 0) add(StudyMode.New)
                if (detail.stats.due > 0) add(StudyMode.Review)
            }
        }
    }
}
