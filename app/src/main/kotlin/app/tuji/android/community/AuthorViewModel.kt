package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.AuthorShelf
import app.tuji.android.core.community.LanguageGroup
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 作者主頁 — iOS's `AuthorProfileVM`.
 *
 * The old page waited for an author that a failed request never delivered, so
 * 載入物見中… stayed up for good. Loading, not found and failed are three
 * different nothings here, and only the last is worth a 重試.
 *
 * Nothing is filtered by the block list. A profile is where the reader goes to
 * see one person's work — including someone they blocked, since this is also
 * where 解除封鎖 is.
 */
class AuthorViewModel(
    val handle: String,
    private val atlas: AtlasReading,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    enum class Phase { Loading, Ready, NotFound, Failed }

    data class State(
        val phase: Phase = Phase.Loading,
        val author: AtlasAuthor? = null,
        val collections: List<AtlasPublicCollection> = emptyList(),
        val groups: List<LanguageGroup> = emptyList(),
    ) {
        val showsSegments: Boolean get() = AuthorShelf.showsSegments(collections)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun load() {
        _state.value = _state.value.copy(phase = Phase.Loading)
        work.launch {
            val page = try {
                atlas.author(handle)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "author page failed: $handle", failure)
                val notFound = (failure as? ApiError.Http)?.status == 404
                _state.value = State(phase = if (notFound) Phase.NotFound else Phase.Failed)
                return@launch
            }
            val author = page.author ?: run {
                _state.value = State(phase = Phase.NotFound)
                return@launch
            }
            _state.value = State(
                phase = Phase.Ready,
                author = author,
                collections = page.collections,
                groups = AuthorShelf.groups(page.items),
            )
        }
    }

    private companion object {
        const val TAG = "TujiAuthor"
    }
}
