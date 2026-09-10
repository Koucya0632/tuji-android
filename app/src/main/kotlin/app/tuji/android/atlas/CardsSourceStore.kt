package app.tuji.android.atlas

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.PersonalWordsAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The two shelves in 圖鑑 that are not the dictionary: 書籤 and 已收進.
 *
 * Held once for the process rather than per screen, because two screens read
 * the same answer: the grid draws the shelves, and 單字詳情 draws the star for
 * whichever word is open. Two copies would let a word be bookmarked on one
 * screen and not on the other, which reads as the mark not saving.
 *
 * **A failed load keeps what it had.** Emptying 書籤 because one request timed
 * out tells someone with forty marks that they are gone.
 */
class CardsSourceStore(
    private val remote: PersonalWordsAccess,
    private val scope: CoroutineScope,
) : AccountScopedStore {

    data class Personal(
        /** Ids, spanning both decks — see `CardsSourceRules.words`. */
        val bookmarked: Set<String> = emptySet(),
        val taken: List<Word> = emptyList(),
        /** True once a load has *succeeded*, so a failure does not look loaded. */
        val loaded: Boolean = false,
    )

    private val _personal = MutableStateFlow(Personal())
    val personal: StateFlow<Personal> = _personal.asStateFlow()

    private val gate = Mutex()

    /**
     * Fill both shelves.
     *
     * [force] is what a save or an unsave passes: 已收進 changed on the server
     * and nothing local can know what the new row looks like.
     */
    suspend fun load(lang: String, learning: LearningDirection, force: Boolean = false) {
        gate.withLock {
            if (_personal.value.loaded && !force) return
            val marks = try {
                remote.favorites().favorites.toSet()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "bookmarks load failed — keeping the last set", failure)
                return
            }
            val saved = try {
                remote.savedWords(lang, learning).words
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Half an answer is still an answer: the marks arrived, and
                // 書籤 can be drawn from them while 已收進 stays as it was.
                Log.w(TAG, "saved words load failed — keeping the last shelf", failure)
                _personal.value = _personal.value.copy(bookmarked = marks)
                return
            }
            Log.i(TAG, "loaded ${marks.size} bookmarks and ${saved.size} saved words")
            _personal.value = Personal(bookmarked = marks, taken = saved, loaded = true)
        }
    }

    /**
     * Mark or unmark, immediately on screen and then on the server.
     *
     * **Reverted if the write fails**, unlike a settings change. Settings send
     * the whole object on every change, so a lost write is carried by the next
     * one; a bookmark has no next write to carry it, and a star left lit over a
     * server that never heard is a lie the user finds out about on the launch
     * after this one.
     */
    fun toggle(wordId: String) {
        val was = wordId in _personal.value.bookmarked
        apply(wordId, marked = !was)
        scope.launch {
            try {
                remote.setFavorite(wordId, favorite = !was)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "bookmark write failed — putting it back", failure)
                apply(wordId, marked = was)
            }
        }
    }

    private fun apply(wordId: String, marked: Boolean) {
        val current = _personal.value.bookmarked
        _personal.value = _personal.value.copy(
            bookmarked = if (marked) current + wordId else current - wordId,
        )
    }

    /**
     * Drop what belonged to the old deck.
     *
     * 已收進 only: 書籤 spans both decks — the same account marks a word once,
     * whichever language it was learning at the time — so clearing it on a
     * direction change would make every mark look lost until the next load.
     */
    fun retune() {
        _personal.value = _personal.value.copy(taken = emptyList(), loaded = false)
    }

    /** Sign-out. Both shelves belong to the account that just left. */
    override fun reset() {
        _personal.value = Personal()
    }

    private companion object {
        const val TAG = "TujiCardsSource"
    }
}
