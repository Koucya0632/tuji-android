package app.tuji.android.travel

import android.util.Log
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.network.TravelAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The travel map, as last answered for one interface language and learning
 * direction — iOS's `TravelStore`. Loaded when 物見 appears, not at launch: it
 * is one screen's entry, and the app opens without it.
 *
 * An empty answer is an answer — it is what the server says to everyone not
 * learning 日文, and what hides the entry. A failed load keeps whatever was
 * shown and is asked again the next time 物見 appears.
 *
 * Public and the same for every account, so it is not account-scoped.
 */
class TravelStore(val remote: TravelAccess) {

    enum class Phase { Idle, Loading, Loaded, Failed }

    data class State(
        val destinations: List<TravelDestination> = emptyList(),
        val phase: Phase = Phase.Idle,
    ) {
        fun destination(id: String): TravelDestination? = destinations.firstOrNull { it.id == id }

        fun city(id: String, destinationId: String): TravelCity? = destination(destinationId)?.city(id)

        fun spot(id: String): TravelSpot? =
            destinations.asSequence().flatMap { it.cities }.flatMap { it.spots }.firstOrNull { it.id == id }
    }

    private data class Key(val lang: String, val learning: LearningDirection)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** What `destinations` was loaded for; a different key means reload. */
    private var loadedKey: Key? = null

    /** The newest request. A slower, older one must not overwrite its answer. */
    @Volatile private var wanted: Key? = null

    suspend fun loadIfNeeded(lang: String, learning: LearningDirection) {
        val key = Key(lang, learning)
        if (loadedKey == key && _state.value.phase == Phase.Loaded) return
        load(key)
    }

    suspend fun reload(lang: String, learning: LearningDirection) = load(Key(lang, learning))

    private suspend fun load(key: Key) {
        wanted = key
        // Another language's map is not this one's "old answer": drop it
        // rather than show Japanese names under an English interface.
        if (loadedKey != key) {
            loadedKey = null
            _state.value = State(phase = Phase.Loading)
        }
        try {
            val response = remote.destinations(key.lang, key.learning)
            if (wanted != key) return
            loadedKey = key
            _state.value = State(response.destinations, Phase.Loaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (wanted != key) return
            Log.w(TAG, "travel load failed", failure)
            // What was on screen for this key stays; with nothing, say so.
            if (loadedKey != key) _state.value = _state.value.copy(phase = Phase.Failed)
        }
    }

    private companion object {
        const val TAG = "TravelStore"
    }
}

/**
 * A study queue drawn from one 景點 — iOS's `SpotStudyQueue`.
 *
 * Shaped as the same [StudyQueueReading] 今日's sessions read, so 學新字 and
 * 複習 run unchanged and only where the cards come from differs. The theme
 * filter is ignored: a spot is its own selection.
 */
class SpotQueue(private val remote: TravelAccess, private val spotId: String) : StudyQueueReading {
    override suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse {
        val response = remote.spotQueue(spotId, mode, limit.coerceAtLeast(1), lang, learning)
        // One item per word, the same rule 今日's queue keeps.
        return response.copy(queue = response.queue.distinctBy { it.word.id })
    }
}
