package app.tuji.android.travel

import android.util.Log
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.network.TravelAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The travel map, as last answered for one interface language and learning
 * direction. Loaded when 物見 appears, not at launch: it is one screen's entry.
 *
 * An empty answer is an answer — it is what the server says to everyone not
 * learning 日文, and what hides the entry. A failed load keeps whatever was
 * shown and is asked again the next time 物見 appears.
 */
class TravelStore(private val remote: TravelAccess) {

    enum class Phase { Idle, Loading, Loaded, Failed }

    data class State(
        val destinations: List<TravelDestination> = emptyList(),
        val phase: Phase = Phase.Idle,
    ) {
        fun destination(id: String): TravelDestination? = destinations.firstOrNull { it.id == id }

        fun city(id: String, destinationId: String): TravelCity? = destination(destinationId)?.city(id)

        fun spot(id: String): TravelSpot? =
            destinations.asSequence().flatMap { it.cities }.flatMap { it.spots }.firstOrNull { it.id == id }

        /** The city a spot belongs to, for the label above its name. */
        fun cityOf(spotId: String): TravelCity? =
            destinations.asSequence().flatMap { it.cities }.firstOrNull { city -> city.spots.any { it.id == spotId } }
    }

    private data class Key(val lang: String, val learning: LearningDirection)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** What `state` was loaded for; a different key means reload. */
    private var loadedKey: Key? = null

    /** The newest request. A slower, older one must not overwrite its answer. */
    private var wanted: Key? = null

    suspend fun loadIfNeeded(lang: String, learning: LearningDirection) {
        val key = Key(lang, learning)
        if (loadedKey == key && _state.value.phase == Phase.Loaded) return
        load(key)
    }

    suspend fun reload(lang: String, learning: LearningDirection) = load(Key(lang, learning))

    private suspend fun load(key: Key) {
        wanted = key
        val keep = loadedKey == key && _state.value.phase == Phase.Loaded
        // Another language's map is not this one's "old answer": drop it
        // rather than show Japanese names under an English interface.
        if (!keep) {
            loadedKey = null
            _state.value = State(phase = Phase.Loading)
        }
        try {
            val response = remote.destinations(key.lang, key.learning)
            if (wanted != key) return
            loadedKey = key
            _state.value = State(response.destinations, Phase.Loaded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (wanted != key) return
            Log.w(TAG, "travel load failed", e)
            if (!keep) _state.value = State(phase = Phase.Failed)
        }
    }

    private companion object {
        const val TAG = "TravelStore"
    }
}
