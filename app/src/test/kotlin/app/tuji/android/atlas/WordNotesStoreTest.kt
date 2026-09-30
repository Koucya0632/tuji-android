package app.tuji.android.atlas

import app.tuji.android.core.model.WordNote
import app.tuji.android.core.model.WordNotesResponse
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.WordNotesAccess
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WordNotesStoreTest {

    private class Server(
        var notes: List<WordNote> = emptyList(),
        var failRead: Boolean = false,
        var writeError: Exception? = null,
    ) : WordNotesAccess {
        var reads = 0
        var savedBody: String? = null

        override suspend fun notes(): WordNotesResponse {
            reads++
            if (failRead) throw IOException("offline")
            return WordNotesResponse(available = true, canWrite = true, maxLength = 10, notes = notes)
        }

        override suspend fun saveNote(wordId: String, body: String): WordNote {
            writeError?.let { throw it }
            savedBody = body
            return WordNote(wordId, body, "now")
        }

        override suspend fun deleteNote(wordId: String) {
            writeError?.let { throw it }
        }
    }

    @Test fun `reads once, and again after a failure`() = runTest {
        val server = Server(notes = listOf(WordNote("oven", "hot box")), failRead = true)
        val store = WordNotesStore(server)
        store.loadIfNeeded()
        assertFalse(store.notes.value.loaded)
        server.failRead = false
        store.loadIfNeeded()
        store.loadIfNeeded()
        assertEquals(2, server.reads)
        assertEquals("hot box", store.notes.value.byWordId["oven"]?.body)
        assertEquals(10, store.notes.value.maxLength)
    }

    @Test fun `a save is trimmed, and validity counts against the server's limit`() = runTest {
        val server = Server()
        val store = WordNotesStore(server)
        store.loadIfNeeded()
        assertFalse(store.isValid("   "))
        assertTrue(store.isValid("  ten chars "))
        assertFalse(store.isValid("eleven char"))
        assertEquals(MemberWriteOutcome.Done, store.save("oven", "  hot  "))
        assertEquals("hot", server.savedBody)
        assertEquals("hot", store.notes.value.byWordId["oven"]?.body)
    }

    @Test fun `a refused save says why`() = runTest {
        val store = WordNotesStore(Server(writeError = ApiError.Http(402, null)))
        assertEquals(MemberWriteOutcome.NeedsUpgrade, store.save("oven", "hot"))
        assertNull(store.notes.value.byWordId["oven"])
    }

    @Test fun `a refused delete puts the note back, a missing one stays gone`() = runTest {
        val server = Server(notes = listOf(WordNote("oven", "hot box")))
        val store = WordNotesStore(server)
        store.loadIfNeeded()

        server.writeError = ApiError.Http(402, null)
        assertEquals(MemberWriteOutcome.NeedsUpgrade, store.delete("oven"))
        assertEquals("hot box", store.notes.value.byWordId["oven"]?.body)

        server.writeError = ApiError.Http(404, null)
        assertEquals(MemberWriteOutcome.Missing, store.delete("oven"))
        assertNull(store.notes.value.byWordId["oven"])
    }

    @Test fun `reset forgets the account`() = runTest {
        val store = WordNotesStore(Server(notes = listOf(WordNote("oven", "hot box"))))
        store.loadIfNeeded()
        store.reset()
        assertTrue(store.notes.value.byWordId.isEmpty())
        assertFalse(store.notes.value.loaded)
    }
}
