package app.tuji.android.atlas

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.WordNote
import app.tuji.android.core.network.WordNotesAccess
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The account's 個人筆記, read once and kept — iOS's `WordNotesStore`.
 *
 * The word page and the review reveal both ask here, so a note shows during
 * review with no request of its own.
 *
 * Data only. Whether notes show, and whether they can be written, is
 * [app.tuji.android.core.model.MemberAccess]: the response's own `canWrite`
 * went stale after a purchase on iOS.
 */
class WordNotesStore(private val remote: WordNotesAccess) : AccountScopedStore {

    data class Notes(
        val byWordId: Map<String, WordNote> = emptyMap(),
        /** In characters, as the server counts. */
        val maxLength: Int = DEFAULT_MAX_LENGTH,
        val loaded: Boolean = false,
    )

    private val _notes = MutableStateFlow(Notes())
    val notes: StateFlow<Notes> = _notes.asStateFlow()

    private val gate = Mutex()

    /** Once per account; a failure is asked again next time. */
    suspend fun loadIfNeeded() = gate.withLock {
        if (_notes.value.loaded) return@withLock
        try {
            val response = remote.notes()
            _notes.value = Notes(
                byWordId = response.notes.associateBy { it.wordId },
                maxLength = response.maxLength ?: DEFAULT_MAX_LENGTH,
                loaded = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "word notes load failed", e)
        }
    }

    /** Trimmed, not empty, and within [Notes.maxLength]. */
    fun isValid(body: String): Boolean {
        val trimmed = body.trim()
        return trimmed.isNotEmpty() && trimmed.codePointCount(0, trimmed.length) <= _notes.value.maxLength
    }

    suspend fun save(wordId: String, body: String): MemberWriteOutcome = try {
        val saved = remote.saveNote(wordId, body.trim())
        _notes.update { it.copy(byWordId = it.byWordId + (wordId to saved)) }
        MemberWriteOutcome.Done
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "word note save failed: $wordId", e)
        MemberWriteOutcome.from(e)
    }

    /** Optimistic; put back if the server refuses. */
    suspend fun delete(wordId: String): MemberWriteOutcome {
        val before = _notes.value.byWordId[wordId]
        _notes.update { it.copy(byWordId = it.byWordId - wordId) }
        return try {
            remote.deleteNote(wordId)
            MemberWriteOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "word note delete failed: $wordId", e)
            val outcome = MemberWriteOutcome.from(e)
            // Already gone elsewhere is the end state asked for.
            if (outcome != MemberWriteOutcome.Missing && before != null) {
                _notes.update { it.copy(byWordId = it.byWordId + (wordId to before)) }
            }
            outcome
        }
    }

    /** The account changed: none of this is the next account's. */
    override fun reset() {
        _notes.value = Notes()
    }

    private companion object {
        const val TAG = "TujiNotes"
        const val DEFAULT_MAX_LENGTH = 500
    }
}
