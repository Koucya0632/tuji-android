package app.tuji.android.manage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.CollectionAuthoringRules
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.CollectionAuthoring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 圖鑑管理's 合集 — iOS's `MyCollectionsVM` and `CollectionCreateModel`: the
 * author's collections in the language being learned, a new one, and deleting
 * one. Deleting a collection never touches the cards in it.
 */
class MyCollectionsViewModel(
    private val authoring: CollectionAuthoring,
    private val language: TargetLanguage,
    /** A public collection went away or appeared — 物見 and the author's page draw it. */
    private val onChanged: () -> Unit = {},
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class State(
        val collections: List<AtlasMyCollection> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
        val creating: Boolean = false,
        val createFailed: Boolean = false,
        val deleting: Boolean = false,
        val deleteFailed: Boolean = false,
        val language: TargetLanguage = TargetLanguage.EN,
    ) {
        val visible: List<AtlasMyCollection> get() = CollectionAuthoringRules.visible(collections, language)
    }

    private val _state = MutableStateFlow(State(language = language))
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun load() {
        work.launch { reload() }
    }

    /** The same read, awaited, so a pull-to-refresh can wait for its own work. */
    suspend fun reload() {
        _state.value = _state.value.copy(loading = true, failed = false)
        attempt { authoring.myCollections() }
            .onSuccess { _state.value = _state.value.copy(collections = it, loading = false) }
            .onFailure { _state.value = _state.value.copy(loading = false, failed = true) }
    }

    /** @param onCreated the new collection, first on the shelf, for the screen to open. */
    fun create(title: String, description: String, onCreated: (AtlasMyCollection) -> Unit = {}) {
        if (_state.value.creating || !CollectionAuthoringRules.titleValid(title)) return
        _state.value = _state.value.copy(creating = true, createFailed = false)
        work.launch {
            attempt { authoring.createCollection(title.trim(), description.trim().ifEmpty { null }, language) }
                .onSuccess { created ->
                    _state.value = _state.value.copy(
                        creating = false,
                        collections = listOf(created) + _state.value.collections.filterNot { it.id == created.id },
                    )
                    onCreated(created)
                }
                .onFailure { _state.value = _state.value.copy(creating = false, createFailed = true) }
        }
    }

    fun delete(collection: AtlasMyCollection, onDone: () -> Unit = {}) {
        if (_state.value.deleting) return
        _state.value = _state.value.copy(deleting = true, deleteFailed = false)
        work.launch {
            attempt { authoring.deleteCollection(collection.id) }
                .onSuccess {
                    _state.value = _state.value.copy(deleting = false, collections = _state.value.collections.filterNot { it.id == collection.id })
                    onChanged()
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(deleting = false, deleteFailed = true) }
        }
    }

    fun dismissCreateError() {
        _state.value = _state.value.copy(createFailed = false)
    }

    /** Not `runCatching`: that swallows cancellation too. */
    private suspend fun <T> attempt(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(TAG, "collections call failed", failure)
        Result.failure(failure)
    }

    private companion object {
        const val TAG = "TujiMyCollections"
    }
}
