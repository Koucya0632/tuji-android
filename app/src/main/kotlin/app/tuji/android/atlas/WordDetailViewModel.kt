package app.tuji.android.atlas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.network.CatalogReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One catalogue entry, and its pronunciation. */
class WordDetailViewModel(
    private val catalog: CatalogReading,
    private val audio: ClipPlaying,
    private val direction: LearningDirection,
    private val uiLang: String,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Failed(val message: String) : State
        data class Loaded(val word: WordDetail, val playing: Boolean = false) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var clip: Job? = null

    fun load(id: String) {
        _state.value = State.Loading
        work.launch {
            runCatching { catalog.word(id, uiLang, direction) }
                .onSuccess { _state.value = State.Loaded(it) }
                .onFailure {
                    Log.e(TAG, "word detail load failed: $id", it)
                    _state.value = State.Failed(it.message ?: "load failed")
                }
        }
    }

    /**
     * Play the word.
     *
     * The locale is the one being learned, not the device's: this is how the
     * *word* sounds, and a Japanese entry read in English is not a pronunciation
     * at all. English falls back to en-US the way iOS does when 發音口音 has not
     * been set — that setting has no Android home yet.
     */
    fun play() {
        val loaded = _state.value as? State.Loaded ?: return
        val url = loaded.word.audioUrls?.get(voice(loaded.word.targetLanguage)) ?: return
        clip?.cancel()
        clip = work.launch {
            _state.value = loaded.copy(playing = true)
            audio.play(url)
            (_state.value as? State.Loaded)?.let { _state.value = it.copy(playing = false) }
        }
    }

    /** Whether there is anything to play, so the button can say so. */
    fun canPlay(word: WordDetail): Boolean =
        word.audioUrls?.get(voice(word.targetLanguage)) != null

    private fun voice(language: TargetLanguage?): String =
        if ((language ?: direction.targetLanguage) == TargetLanguage.JA) "ja-JP" else "en-US"

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
        const val TAG = "TujiWord"
    }
}
