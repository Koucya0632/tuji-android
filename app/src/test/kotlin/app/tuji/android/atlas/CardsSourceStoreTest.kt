package app.tuji.android.atlas

import app.tuji.android.core.model.FavoritesResponse
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordsListResponse
import app.tuji.android.core.network.PersonalWordsAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CardsSourceStoreTest {

    private fun word(id: String) = Word(id = id, word = id)

    private open class Server(
        val marks: List<String> = emptyList(),
        val own: List<Word> = emptyList(),
        val saved: List<Word> = emptyList(),
        val failMarks: Boolean = false,
        val failOwn: Boolean = false,
        val failSaved: Boolean = false,
        val failWrite: Boolean = false,
    ) : PersonalWordsAccess {
        var writes = 0
        var lastWrite: Pair<String, Boolean>? = null
        var savedCalls = 0

        override suspend fun favorites(): FavoritesResponse {
            if (failMarks) throw IOException("offline")
            return FavoritesResponse(favorites = marks)
        }

        override suspend fun setFavorite(wordId: String, favorite: Boolean) {
            writes++
            lastWrite = wordId to favorite
            if (failWrite) throw IOException("offline")
        }

        override suspend fun savedWords(
            lang: String,
            learning: LearningDirection,
        ): WordsListResponse {
            savedCalls++
            if (failSaved) throw IOException("offline")
            return WordsListResponse(words = saved)
        }

        override suspend fun customWords(
            lang: String,
            learning: LearningDirection,
        ): WordsListResponse {
            if (failOwn) throw IOException("offline")
            return WordsListResponse(words = own)
        }
    }

    private fun store(server: Server, scope: TestScope) =
        CardsSourceStore(remote = server, scope = scope)

    private suspend fun CardsSourceStore.fill() =
        load(lang = "zh-Hant", learning = LearningDirection.ZH_JA)

    @Test fun `every shelf arrives together`() = runTest(StandardTestDispatcher()) {
        val s = store(
            Server(
                marks = listOf("a"),
                own = listOf(word("atlas:mine")),
                saved = listOf(word("saved:x")),
            ),
            this,
        )

        s.fill()

        assertEquals(setOf("a"), s.personal.value.bookmarked)
        assertEquals(listOf("atlas:mine"), s.personal.value.mine.map { it.id })
        assertEquals(listOf("saved:x"), s.personal.value.taken.map { it.id })
        assertTrue(s.personal.value.loaded)
    }

    /** Part of an answer is still an answer. */
    @Test fun `the shelves that arrived are kept when a later one fails`() =
        runTest(StandardTestDispatcher()) {
            val s = store(
                Server(marks = listOf("a"), own = listOf(word("atlas:mine")), failSaved = true),
                this,
            )

            s.fill()

            assertEquals(setOf("a"), s.personal.value.bookmarked)
            assertEquals(listOf("atlas:mine"), s.personal.value.mine.map { it.id })
            assertFalse(s.personal.value.loaded)
        }

    /** Loaded once. A tab swap must not re-fetch two lists that cannot have moved. */
    @Test fun `a second load without force does nothing`() = runTest(StandardTestDispatcher()) {
        val server = Server(marks = listOf("a"))
        val s = store(server, this)

        s.fill()
        s.fill()

        assertEquals(1, server.savedCalls)
    }

    @Test fun `force is what a save passes`() = runTest(StandardTestDispatcher()) {
        val server = Server()
        val s = store(server, this)

        s.fill()
        s.load(lang = "zh-Hant", learning = LearningDirection.ZH_JA, force = true)

        assertEquals(2, server.savedCalls)
    }

    /** Forty marks must not vanish because one request timed out. */
    @Test fun `a failed load keeps the last shelves`() = runTest(StandardTestDispatcher()) {
        val s = store(Server(marks = listOf("a"), saved = listOf(word("saved:x"))), this)
        s.fill()

        val offline = CardsSourceStore(remote = Server(failMarks = true), scope = this)
        offline.fill()

        assertEquals(emptySet<String>(), offline.personal.value.bookmarked)
        assertFalse("a failure must not look loaded", offline.personal.value.loaded)
        // The one that did load is untouched by the one that did not.
        assertEquals(setOf("a"), s.personal.value.bookmarked)
    }

    /** Half an answer is still an answer. */
    @Test fun `marks are kept when only the saved shelf fails`() = runTest(StandardTestDispatcher()) {
        val s = store(Server(marks = listOf("a", "b"), failSaved = true), this)

        s.fill()

        assertEquals(setOf("a", "b"), s.personal.value.bookmarked)
        assertFalse(s.personal.value.loaded)
    }

    @Test fun `a toggle shows before the write goes out`() = runTest(StandardTestDispatcher()) {
        val server = Server()
        val s = store(server, this)

        s.toggle("a")

        assertTrue("a" in s.personal.value.bookmarked)
        assertEquals(0, server.writes)
        advanceUntilIdle()
        assertEquals("a" to true, server.lastWrite)
    }

    @Test fun `toggling a marked word unmarks it`() = runTest(StandardTestDispatcher()) {
        val server = Server(marks = listOf("a"))
        val s = store(server, this)
        s.fill()

        s.toggle("a")
        advanceUntilIdle()

        assertFalse("a" in s.personal.value.bookmarked)
        assertEquals("a" to false, server.lastWrite)
    }

    /**
     * The rule that separates this from a settings change: settings re-send the
     * whole object, so a lost write is carried by the next one. A bookmark has
     * no next write, and a star left lit is a lie until the next launch.
     */
    @Test fun `a failed write puts the mark back`() = runTest(StandardTestDispatcher()) {
        val s = store(Server(failWrite = true), this)

        s.toggle("a")
        advanceUntilIdle()

        assertFalse("a" in s.personal.value.bookmarked)
    }

    @Test fun `a failed unmark puts the mark back too`() = runTest(StandardTestDispatcher()) {
        val s = store(Server(marks = listOf("a"), failWrite = true), this)
        s.fill()

        s.toggle("a")
        advanceUntilIdle()

        assertTrue("a" in s.personal.value.bookmarked)
    }

    /**
     * 已收進 belongs to one deck; a mark belongs to the account. Clearing the
     * marks on a direction change would make every one of them look lost.
     */
    @Test fun `retune drops the deck-scoped shelves and keeps the marks`() =
        runTest(StandardTestDispatcher()) {
            val s = store(
                Server(
                    marks = listOf("a"),
                    own = listOf(word("atlas:mine")),
                    saved = listOf(word("saved:x")),
                ),
                this,
            )
            s.fill()

            s.retune()

            assertEquals(setOf("a"), s.personal.value.bookmarked)
            assertEquals(emptyList<Word>(), s.personal.value.mine)
            assertEquals(emptyList<Word>(), s.personal.value.taken)
            assertFalse(s.personal.value.loaded)
        }

    @Test fun `sign-out drops both`() = runTest(StandardTestDispatcher()) {
        val s = store(Server(marks = listOf("a"), saved = listOf(word("saved:x"))), this)
        s.fill()

        s.reset()

        assertEquals(CardsSourceStore.Personal(), s.personal.value)
    }
}
