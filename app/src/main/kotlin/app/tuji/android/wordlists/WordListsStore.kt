package app.tuji.android.wordlists

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.WordList
import app.tuji.android.core.model.WordListLimits
import app.tuji.android.core.network.WordListsAccess
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The account's 個人詞表 in the current learning language — iOS's
 * `WordListsStore`: the one shared read that 我, the 詞表 screen and the word
 * page's 加入詞表 all ask.
 *
 * Data only. Whether the feature shows, and in which form, is
 * [app.tuji.android.core.model.MemberAccess].
 */
class WordListsStore(
    private val remote: WordListsAccess,
    /** Read at each call: lists are per language, and 設定 can switch it. */
    private val direction: () -> LearningDirection,
) : AccountScopedStore {

    enum class Phase { Idle, Loading, Loaded, Failed }

    data class Snapshot(
        val phase: Phase = Phase.Idle,
        val canCreate: Boolean = false,
        val limits: WordListLimits? = null,
        val lists: List<WordList> = emptyList(),
        /** The direction [lists] belongs to. A switch makes them someone else's. */
        val direction: LearningDirection? = null,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    suspend fun loadIfNeeded() {
        val now = _state.value
        if (now.phase == Phase.Loaded && now.direction == direction()) return
        reload()
    }

    suspend fun reload() {
        val direction = direction()
        // A reload of what is on screen keeps it there; a new language does not.
        _state.update { if (it.direction == direction && it.phase == Phase.Loaded) it else it.copy(phase = Phase.Loading) }
        try {
            apply(remote.wordLists(direction), direction)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "word lists load failed", e)
            _state.update { if (it.phase == Phase.Loaded) it else it.copy(phase = Phase.Failed) }
        }
    }

    /**
     * The same listing with `containsWord` marked, for the 加入詞表 sheet. Also
     * refreshes the shared answer, since it is the same read. Throws.
     */
    suspend fun listsContaining(wordId: String): List<WordList> {
        val direction = direction()
        val response = remote.wordLists(direction, containing = wordId)
        apply(response, direction)
        return response.lists
    }

    private fun apply(response: app.tuji.android.core.model.WordListsResponse, direction: LearningDirection) {
        _state.value = Snapshot(
            phase = Phase.Loaded,
            canCreate = response.canCreate ?: false,
            limits = response.limits,
            lists = response.lists,
            direction = direction,
        )
    }

    // Writes

    suspend fun create(name: String): Pair<MemberWriteOutcome, WordList?> = write(null) {
        val list = remote.createWordList(direction(), name)
        _state.update { it.copy(lists = it.lists + list) }
        reload()
        list
    }

    suspend fun rename(listId: String, name: String): MemberWriteOutcome = write(null) {
        remote.renameWordList(listId, name)
        reload()
    }.first

    suspend fun delete(listId: String): MemberWriteOutcome {
        val before = _state.value.lists
        _state.update { s -> s.copy(lists = s.lists.filterNot { it.id == listId }) }
        val outcome = write(null) {
            remote.deleteWordList(listId)
            reload()
        }.first
        // Already gone elsewhere is what the person asked for.
        if (outcome == MemberWriteOutcome.Missing) return MemberWriteOutcome.Done
        if (outcome != MemberWriteOutcome.Done) _state.update { it.copy(lists = before) }
        return outcome
    }

    /**
     * Optimistic: the rows move first, the server is told after. Order also
     * decides which lists stay usable after a downgrade, so it is reloaded to
     * pick up the new `locked` flags.
     */
    suspend fun move(listId: String, by: Int): MemberWriteOutcome {
        val before = _state.value.lists
        val from = before.indexOfFirst { it.id == listId }
        val to = from + by
        if (from < 0 || to !in before.indices) return MemberWriteOutcome.Done
        val moved = before.toMutableList().apply { add(to, removeAt(from)) }
        _state.update { it.copy(lists = moved) }
        val outcome = write(null) {
            remote.reorderWordLists(direction(), moved.map { it.id })
            reload()
        }.first
        if (outcome != MemberWriteOutcome.Done) _state.update { it.copy(lists = before) }
        return outcome
    }

    suspend fun setWord(wordId: String, listId: String, present: Boolean): MemberWriteOutcome = write(null) {
        remote.setWordInList(listId, wordId, present)
        _state.update { s ->
            s.copy(
                lists = s.lists.map {
                    if (it.id != listId) {
                        it
                    } else {
                        it.copy(
                            wordCount = (it.wordCount + if (present) 1 else -1).coerceAtLeast(0),
                            containsWord = present,
                        )
                    }
                },
            )
        }
    }.first

    private suspend fun <T> write(fallback: T, block: suspend () -> T): Pair<MemberWriteOutcome, T> = try {
        MemberWriteOutcome.Done to block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "word list write failed", e)
        MemberWriteOutcome.from(e) to fallback
    }

    /** The account changed: none of this is the next account's. */
    override fun reset() {
        _state.value = Snapshot()
    }

    private companion object {
        const val TAG = "TujiWordLists"
    }
}
