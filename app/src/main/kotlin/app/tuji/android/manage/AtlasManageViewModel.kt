package app.tuji.android.manage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.AtlasShelf
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.community.ShelfRow
import app.tuji.android.core.community.ShelfState
import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasShelfManaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 圖鑑管理's 卡片 — iOS's `AtlasShelfModel` over `AtlasStore`: browse what you
 * photographed, open one, delete it, or take it off 物見.
 *
 * Held above the two screens that use it, the list and one card's page, so a
 * delete made on the page is already gone from the list behind it.
 */
class AtlasManageViewModel(
    private val shelf: AtlasShelfManaging,
    private val language: TargetLanguage,
    /** A card or its public copy is gone — 我做的, 今日 and 物見 draw them. */
    private val onChanged: () -> Unit = {},
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class State(
        val images: List<AtlasImageSummary> = emptyList(),
        val items: List<AtlasItem> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
        val selecting: Boolean = false,
        val selected: Set<String> = emptySet(),
        val deleting: Boolean = false,
        val publishing: Boolean = false,
        val withdrawing: Boolean = false,
        /** The last delete or withdraw failed. */
        val actionFailed: Boolean = false,
        val language: TargetLanguage = TargetLanguage.EN,
    ) {
        val rows: List<ShelfRow> get() = AtlasShelf.rows(images, items, language)
        val hidden: Int get() = AtlasShelf.hiddenCount(images, rows)
        val shelf: ShelfState get() = AtlasShelf.state(rows, hidden, loading, failed)
        val selectionWarning: DeleteWarning get() = AtlasShelf.warning(rows, selected)
        fun row(imageId: String): ShelfRow? = rows.firstOrNull { it.id == imageId }
    }

    private val _state = MutableStateFlow(State(language = language))
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun load() {
        _state.value = _state.value.copy(loading = true, failed = false)
        work.launch {
            val bundle = try {
                shelf.sync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "atlas sync failed", failure)
                _state.value = _state.value.copy(loading = false, failed = true)
                return@launch
            }
            val now = _state.value
            val next = now.copy(images = bundle.images, items = bundle.items, loading = false, failed = false)
            // A selection that went off the shelf is not a selection.
            _state.value = next.copy(selected = now.selected intersect next.rows.map { it.id }.toSet())
        }
    }

    fun setSelecting(on: Boolean) {
        _state.value = _state.value.copy(selecting = on, selected = if (on) _state.value.selected else emptySet())
    }

    fun toggle(imageId: String) {
        val now = _state.value
        _state.value = now.copy(selected = if (imageId in now.selected) now.selected - imageId else now.selected + imageId)
    }

    /**
     * One at a time, so one failure does not take the rest with it. What failed
     * stays selected for another go; the rest leave the shelf.
     */
    fun delete(imageIds: Collection<String>, onDone: () -> Unit = {}) {
        if (imageIds.isEmpty() || _state.value.deleting) return
        _state.value = _state.value.copy(deleting = true, actionFailed = false)
        work.launch {
            val failed = mutableSetOf<String>()
            for (id in imageIds) {
                try {
                    shelf.deleteImage(id)
                    _state.value = _state.value.let { s ->
                        s.copy(images = s.images.filterNot { it.id == id }, items = s.items.filterNot { it.imageId == id })
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "delete failed: $id", failure)
                    failed += id
                }
            }
            _state.value = _state.value.copy(
                deleting = false,
                actionFailed = failed.isNotEmpty(),
                selected = failed,
                selecting = _state.value.selecting && failed.isNotEmpty(),
            )
            if (failed.size < imageIds.size) onChanged()
            if (failed.isEmpty()) onDone()
        }
    }

    /** 取消公開. The server owns the resulting state, so the shelf re-reads rather than guessing it. */
    /**
     * Put one card up for 物見 review.
     *
     * The mirror of [withdraw]. It used to live only on the screen shown right
     * after a card was made — so a word somebody decided to share a day later
     * had nowhere to be shared from, and that screen is gone now anyway: the
     * card is finished by 生成佇列, after the capture page has closed.
     */
    fun publish(itemId: String) {
        if (_state.value.publishing) return
        _state.value = _state.value.copy(publishing = true, actionFailed = false)
        work.launch {
            val ok = try {
                shelf.publishItem(itemId)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "publish failed: $itemId", failure)
                false
            }
            _state.value = _state.value.copy(publishing = false, actionFailed = !ok)
            if (ok) {
                onChanged()
                load()
            }
        }
    }

    fun withdraw(itemId: String) {
        if (_state.value.withdrawing) return
        _state.value = _state.value.copy(withdrawing = true, actionFailed = false)
        work.launch {
            val ok = try {
                shelf.withdrawItem(itemId)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "withdraw failed: $itemId", failure)
                false
            }
            _state.value = _state.value.copy(withdrawing = false, actionFailed = !ok)
            if (ok) {
                onChanged()
                load()
            }
        }
    }

    private companion object {
        const val TAG = "TujiManage"
    }
}
