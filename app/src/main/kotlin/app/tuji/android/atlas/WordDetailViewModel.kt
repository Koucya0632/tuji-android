package app.tuji.android.atlas

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.study.SpokenVoice
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.network.AtlasItemReading
import app.tuji.android.core.network.CatalogReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One entry, and its pronunciation.
 *
 * Two sources, one screen: the dictionary answers a bare id, and 自製圖鑑
 * answers an `atlas:`-prefixed one. The payloads are the **same shape** — the
 * server's own route says so — which is why a card the user photographed opens
 * the page they already know instead of a second one built to look like it.
 */
class WordDetailViewModel(
    private val catalog: CatalogReading,
    private val atlas: AtlasItemReading,
    private val audio: ClipPlaying,
    private val direction: LearningDirection,
    private val uiLang: String,
    /** The saved 發音口音, for [SpokenVoice]. */
    private val accent: String = "us",
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        /**
         * Carries **nothing**.
         *
         * It used to carry the exception's message, and the screen printed it:
         * a custom card whose load timed out put
         * 「Transport failure: Socket timeout has expired [url=https://…]」
         * on screen, in English, with the server's address in it. That is a
         * sentence for a log. This project already wrote the rule down for the
         * sign-in screen — `AuthFailure` is an enum so that showing a server's
         * words to a user is *structurally* impossible — and this is the same
         * rule, one screen over. The message still goes to the log, where it
         * was always meant to go.
         */
        data object Failed : State
        data class Loaded(val word: WordDetail, val playing: Boolean = false) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope
    private var clip: Job? = null

    fun load(id: String) {
        _state.value = State.Loading
        val custom = CardsSourceRules.customItemId(id)
        work.launch {
            // The custom route can take its time — it finishes a card that was
            // never finished, inline. That is why its endpoint is the slow one
            // and why nothing here races it.
            runCatching {
                if (custom != null) atlas.itemDetail(custom, uiLang)
                else catalog.word(id, uiLang, direction)
            }
                .onSuccess { _state.value = State.Loaded(it) }
                .onFailure {
                    Log.e(TAG, "word detail load failed: $id", it)
                    _state.value = State.Failed
                }
        }
    }

    /**
     * Play the word.
     *
     * The locale is the one being learned, not the device's: this is how the
     * *word* sounds, and a Japanese entry read in English is not a pronunciation
     * at all. Which of the two English recordings comes back is 設定 → 發音口音,
     * through [SpokenVoice].
     */
    fun play() {
        val loaded = _state.value as? State.Loaded ?: return
        val url = clipFor(loaded.word) ?: return
        clip?.cancel()
        clip = work.launch {
            _state.value = loaded.copy(playing = true)
            audio.play(url)
            (_state.value as? State.Loaded)?.let { _state.value = it.copy(playing = false) }
        }
    }

    /** Whether there is anything to play, so the button can say so. */
    fun canPlay(word: WordDetail): Boolean = clipFor(word) != null

    private fun clipFor(word: WordDetail): String? =
        SpokenVoice.clip(word.audioUrls, direction, accent, word.targetLanguage)

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
