package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.BlockList
import app.tuji.android.core.community.CollectionLearnAction
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.CollectionBookmarking
import app.tuji.android.core.network.CollectionLearning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One 合集, opened — iOS's `CollectionDetailVM`.
 *
 * Three things a reader does here, and they are different on purpose:
 * **收藏** unlocks the member list and counts toward the author, and puts
 * nothing in the reader's 圖鑑; **全部加入學習** puts the remaining members
 * into the study queue; a member's own page is where one word is taken in.
 * Naming the first after the second would be a button that lies.
 */
class CollectionDetailViewModel(
    private val slug: String,
    private val atlas: AtlasReading,
    private val bookmarks: CollectionBookmarking,
    private val learning: CollectionLearning,
    /** Who is looking, so an author opening their own collection sees 你的合集 rather than 收藏. */
    private val viewerHandle: String?,
    private val signedIn: Boolean,
    private val blocked: () -> BlockList = { BlockList.none },
    /** A bookmark landed — 物見's 已收藏 shelf is drawn from the same answer. */
    private val onBookmarkChanged: () -> Unit = {},
    /** Members went into the queue — 今日 and 圖鑑 count them. */
    private val onLearned: () -> Unit = {},
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class State(
        val collection: AtlasPublicCollection? = null,
        val items: List<AtlasPublicItem> = emptyList(),
        val loading: Boolean = true,
        /** The slug no longer resolves — withdrawn, or never published. */
        val notFound: Boolean = false,
        val failed: Boolean = false,
        /** Whether the member list is open to this reader: saved, owner, or a server that never locks. */
        val unlocked: Boolean = false,
        val isOwner: Boolean = false,
        val saved: Boolean = false,
        /** False until the reader's bookmark is known — the button waits rather than guessing. */
        val bookmarkKnown: Boolean = false,
        val bookmarkBusy: Boolean = false,
        val learningCount: Int = 0,
        val totalCount: Int = 0,
        val learningBusy: Boolean = false,
        val error: Error? = null,
    ) {
        val learnAction: CollectionLearnAction get() = CollectionLearnAction.of(learningCount, totalCount)
        val remaining: Int get() = (totalCount - learningCount).coerceAtLeast(0)
    }

    /** What the last action's failure should say. Cleared by [dismissError]. */
    enum class Error { Bookmark, LearnLimit, Learn }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun open() {
        _state.value = State()
        work.launch {
            if (!reload()) return@launch
            // Older responses carry no `access`; ask the bookmark route itself
            // rather than draw a 收藏 button that may be wrong.
            if (signedIn && !_state.value.bookmarkKnown && !_state.value.isOwner) readSaveState()
            if (!signedIn) _state.value = _state.value.copy(bookmarkKnown = true)
        }
    }

    /** 收藏. Guests are turned away by the screen before this is reached. */
    fun save() = changeBookmark(save = true)

    /** 取消收藏 — the screen asks first. */
    fun unsave() = changeBookmark(save = false)

    /** 全部加入學習 — the remaining members, not the ones already in the queue. */
    fun learnRemaining() {
        val now = _state.value
        if (!now.unlocked || now.remaining == 0 || now.learningBusy) return
        _state.value = now.copy(learningBusy = true, error = null)
        work.launch {
            val outcome = attempt { learning.learnCollection(slug) }
            _state.value = outcome.fold(
                onSuccess = { result ->
                    onLearned()
                    _state.value.copy(
                        learningBusy = false,
                        learningCount = result.learningCount,
                        totalCount = result.totalCount,
                    )
                },
                onFailure = { failure ->
                    // 429 `save_limit` is the one failure with something to do
                    // about it — remove some saved words — so it gets its own
                    // sentence.
                    val limit = (failure as? ApiError.Http)?.body?.contains("save_limit") == true
                    _state.value.copy(learningBusy = false, error = if (limit) Error.LearnLimit else Error.Learn)
                },
            )
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun changeBookmark(save: Boolean) {
        val now = _state.value
        if (now.bookmarkBusy || now.isOwner) return
        _state.value = now.copy(bookmarkBusy = true, error = null)
        work.launch {
            val result = attempt { if (save) bookmarks.saveCollection(slug) else bookmarks.unsaveCollection(slug) }
                .getOrNull()
            if (result == null) {
                _state.value = _state.value.copy(bookmarkBusy = false, error = Error.Bookmark)
                return@launch
            }
            val lockChanged = _state.value.unlocked != result.saved
            _state.value = _state.value.copy(
                bookmarkBusy = false,
                bookmarkKnown = true,
                saved = result.saved,
                collection = _state.value.collection?.copy(saveCount = result.saveCount),
            )
            onBookmarkChanged()
            // 收藏 is what unlocks the members, and a locked response carries a
            // preview of them rather than all — so flipping a flag here would
            // open a list the screen does not have. Re-read, but only when the
            // lock actually moved.
            if (lockChanged) reload(keepHeader = true)
        }
    }

    /** @return whether the detail arrived. */
    private suspend fun reload(keepHeader: Boolean = false): Boolean {
        val detail = try {
            atlas.collection(slug)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "collection failed: $slug", failure)
            val notFound = (failure as? ApiError.Http)?.status == 404
            if (!keepHeader) _state.value = _state.value.copy(loading = false, failed = true, notFound = notFound)
            return false
        }
        val collection = detail.collection ?: run {
            if (!keepHeader) _state.value = _state.value.copy(loading = false, failed = true, notFound = true)
            return false
        }
        val access = detail.access
        val owner = access?.isOwner ?: (viewerHandle != null && viewerHandle.equals(collection.author?.handle, ignoreCase = true))
        _state.value = _state.value.copy(
            collection = collection,
            items = blocked().filter(detail.items) { it.author },
            loading = false,
            failed = false,
            // No `access` means an older server that always sent the whole list.
            unlocked = access?.unlocked ?: true,
            isOwner = owner,
            saved = access?.isSaved ?: _state.value.saved,
            bookmarkKnown = access != null || _state.value.bookmarkKnown,
            learningCount = access?.learningCount ?: _state.value.learningCount,
            totalCount = access?.totalCount ?: collection.itemCount,
        )
        return true
    }

    private suspend fun readSaveState() {
        val result = attempt { bookmarks.collectionSaveState(slug) }.getOrNull()
        _state.value = _state.value.copy(
            // Known either way: a missing route must not leave the button
            // spinning forever over a collection that loaded fine.
            bookmarkKnown = true,
            saved = result?.saved ?: _state.value.saved,
            collection = result?.let { r -> _state.value.collection?.copy(saveCount = r.saveCount) } ?: _state.value.collection,
        )
    }

    /** Not `runCatching`: that swallows cancellation too, and a torn-down call must stay torn down. */
    private suspend fun <T> attempt(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(TAG, "collection action failed: $slug", failure)
        Result.failure(failure)
    }

    private companion object {
        const val TAG = "TujiCollection"
    }
}
