package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.AtlasSaving
import app.tuji.android.core.study.SpokenVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One 物見 word, opened — iOS's `AtlasPublicDetailVM`.
 *
 * It used to live inside `CommunityViewModel` as one shared slot, and it never
 * asked where the reader's 加入學習 stood: every opening drew 加入學習, even
 * over a word already in their 圖鑑, and once saved there was no way back out.
 * The page now reads the save state as it opens and toggles both ways.
 */
class PublicItemViewModel(
    private val slug: String,
    private val atlas: AtlasReading,
    private val saver: AtlasSaving,
    private val audio: ClipPlaying,
    private val direction: LearningDirection,
    private val uiLang: String,
    private val signedIn: Boolean,
    /** The saved 發音口音, for [SpokenVoice]. */
    private val accent: String = "us",
    /**
     * A 加入學習 or 停止學習 landed. The card is added to or taken from the
     * reader's own 圖鑑 and study queue, which other tabs draw.
     */
    private val onSaveChanged: () -> Unit = {},
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class State(
        val item: AtlasPublicDetail? = null,
        val loading: Boolean = true,
        val failed: Boolean = false,
        val notFound: Boolean = false,
        val saved: Boolean = false,
        /** How many people are learning it. Null until the save route has answered. */
        val saveCount: Int? = null,
        /** A save-state read or a toggle in flight; the pill waits. */
        val busy: Boolean = false,
        val error: Error? = null,
        val playing: Boolean = false,
    )

    enum class Error { SaveLimit, Failed }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var clip: Job? = null

    fun open() {
        _state.value = State()
        work.launch { loadDetail() }
        // A guest has no 圖鑑 to have saved it into, and the route answers 401.
        if (signedIn) work.launch { loadSaveState() }
    }

    /**
     * 加入學習 when it is not in the reader's 圖鑑, 停止學習 when it is — the
     * screen asks before the second. A failed call leaves the toggle where it
     * was: a word the reader believes is saved and is not is worse than a
     * second tap.
     */
    fun toggleSave() {
        val now = _state.value
        if (now.busy || now.item == null) return
        val wasSaved = now.saved
        _state.value = now.copy(busy = true, error = null)
        work.launch {
            val result = try {
                if (wasSaved) saver.unsave(slug) else saver.save(slug)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "save toggle failed: $slug", failure)
                // 429 `save_limit` is the one failure with something to do
                // about it, so it gets its own sentence.
                val limit = (failure as? ApiError.Http)?.body?.contains("save_limit") == true
                _state.value = _state.value.copy(busy = false, error = if (limit) Error.SaveLimit else Error.Failed)
                return@launch
            }
            _state.value = _state.value.copy(busy = false, saved = result.saved, saveCount = result.saveCount)
            onSaveChanged()
        }
    }

    /** Whether there is a recording to play, so the button can say so. */
    fun canPlay(): Boolean = clipUrl() != null

    fun play() {
        val url = clipUrl() ?: return
        clip?.cancel()
        clip = work.launch {
            _state.value = _state.value.copy(playing = true)
            audio.play(url)
            _state.value = _state.value.copy(playing = false)
        }
    }

    private fun clipUrl(): String? {
        val word = _state.value.item?.learningWord ?: return null
        return SpokenVoice.clip(word.audioUrls, direction, accent, word.targetLanguage)
    }

    private suspend fun loadDetail() {
        val detail = try {
            atlas.item(slug, uiLang)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "物見 item failed: $slug", failure)
            val notFound = (failure as? ApiError.Http)?.status == 404
            _state.value = _state.value.copy(loading = false, failed = true, notFound = notFound)
            return
        }
        _state.value = if (detail == null) {
            _state.value.copy(loading = false, failed = true, notFound = true)
        } else {
            _state.value.copy(item = detail, loading = false, failed = false)
        }
    }

    /**
     * A background read: if it fails the pill keeps its safe default and an
     * explicit tap surfaces any real error.
     */
    private suspend fun loadSaveState() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        val result = try {
            saver.saveState(slug)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "save state failed: $slug", failure)
            null
        }
        _state.value = _state.value.copy(
            busy = false,
            saved = result?.saved ?: _state.value.saved,
            saveCount = result?.saveCount ?: _state.value.saveCount,
        )
    }

    fun stop() {
        clip?.cancel()
        clip = null
        audio.stop()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        const val TAG = "TujiPublicItem"
    }
}
