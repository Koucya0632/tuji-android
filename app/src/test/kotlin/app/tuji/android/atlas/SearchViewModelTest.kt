package app.tuji.android.atlas

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.SearchResponse
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.WordSearching
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private fun word(id: String, term: String = id, gloss: String? = null) =
        Word(id = id, word = term, chinese = gloss)

    private val catalogue = listOf(
        word("hashi", term = "箸", gloss = "筷子"),
        word("hashioki", term = "箸置き", gloss = "筷架"),
        word("kasa", term = "傘", gloss = "雨傘"),
    )

    /**
     * Answers per query, because a stand-in that returns the same rows for
     * every question cannot tell "this answer is stale" from "this answer is
     * right" — which is exactly the distinction these tests are about.
     */
    private class Server(
        val byQuery: Map<String, List<Word>> = emptyMap(),
        val fail: Boolean = false,
        /** Held open until the test releases it, for the one query named here. */
        val held: String? = null,
    ) : WordSearching {
        var calls = 0
        var lastQuery: String? = null
        val release = CompletableDeferred<Unit>()

        override suspend fun search(
            query: String,
            lang: String,
            learning: LearningDirection,
        ): SearchResponse {
            calls++
            lastQuery = query
            if (fail) throw IOException("offline")
            // NonCancellable on purpose: cancelling the job is the *first*
            // thing that stops a stale answer, and a test that relies on it
            // never reaches the guard underneath. This is a request already
            // past the point of no return.
            if (query == held) withContext(NonCancellable) { release.await() }
            return SearchResponse(results = byQuery[query].orEmpty(), query = query)
        }
    }

    private fun model(
        server: Server = Server(),
        catalogue: () -> List<Word> = { this.catalogue },
        scope: TestScope,
    ) = SearchViewModel(
        remote = server,
        local = catalogue,
        lang = "zh-Hant",
        direction = LearningDirection.ZH_JA,
        scope = scope,
    )

    /** The point of the local half: results without waiting for anything. */
    @Test fun `local rows are on screen before the request goes out`() =
        runTest(StandardTestDispatcher()) {
            val server = Server()
            val vm = model(server = server, scope = this)

            vm.query("箸")

            assertEquals(listOf("hashi", "hashioki"), vm.results.value.words.map { it.id })
            assertEquals(0, server.calls)
            advanceUntilIdle()
        }

    @Test fun `the server's extra matches are appended`() =
        runTest(StandardTestDispatcher()) {
            val server = Server(byQuery = mapOf("箸" to listOf(word("chopsticks", term = "chopsticks"))))
            val vm = model(server = server, scope = this)

            vm.query("箸")
            advanceUntilIdle()

            assertEquals(
                listOf("hashi", "hashioki", "chopsticks"),
                vm.results.value.words.map { it.id },
            )
            assertFalse(vm.results.value.searching)
        }

    /**
     * The reason for the debounce: typing a four-character word must not be
     * four requests for answers three of which nobody will read.
     */
    @Test fun `typing a word sends one request`() =
        runTest(StandardTestDispatcher()) {
            val server = Server()
            val vm = model(server = server, scope = this)

            vm.query("は")
            advanceTimeBy(50)
            vm.query("はし")
            advanceTimeBy(50)
            vm.query("はしお")
            advanceUntilIdle()

            assertEquals(1, server.calls)
            assertEquals("はしお", server.lastQuery)
        }

    /** Typing on replaces the previous query's rows rather than adding to them. */
    @Test fun `a new query supersedes the last one`() =
        runTest(StandardTestDispatcher()) {
            val server = Server(
                byQuery = mapOf(
                    "箸" to listOf(word("chopsticks")),
                    "傘" to listOf(word("umbrella")),
                ),
            )
            val vm = model(server = server, scope = this)

            vm.query("箸")
            advanceUntilIdle()
            vm.query("傘")
            advanceUntilIdle()

            val shown = vm.results.value
            assertEquals("傘", shown.query)
            assertEquals(listOf("kasa", "umbrella"), shown.words.map { it.id })
        }

    /**
     * The guard under the cancellation.
     *
     * A request that is already past the point of no return answers a question
     * the user has moved on from. Cancelling the job is what usually stops it;
     * this pins what happens when that is not enough, which is the case iOS
     * shipped a bug in.
     */
    @Test fun `an answer to an abandoned query is dropped`() =
        runTest(StandardTestDispatcher()) {
            val server = Server(
                byQuery = mapOf("箸" to listOf(word("late", term = "late"))),
                held = "箸",
            )
            val vm = model(server = server, scope = this)

            vm.query("箸")
            advanceTimeBy(300)
            vm.query("傘")
            advanceUntilIdle()
            server.release.complete(Unit)
            advanceUntilIdle()

            val shown = vm.results.value
            assertEquals("傘", shown.query)
            assertFalse(
                "the abandoned query's row is on screen",
                shown.words.any { it.id == "late" },
            )
        }

    @Test fun `a failed request keeps the local rows and says nothing`() =
        runTest(StandardTestDispatcher()) {
            val vm = model(server = Server(fail = true), scope = this)

            vm.query("箸")
            advanceUntilIdle()

            val shown = vm.results.value
            assertEquals(listOf("hashi", "hashioki"), shown.words.map { it.id })
            assertFalse("a failure with results to show must stay silent", shown.failed)
            assertFalse(shown.searching)
        }

    /** With nothing to keep instead, the failure is the only honest answer. */
    @Test fun `a failed request with no local match is surfaced`() =
        runTest(StandardTestDispatcher()) {
            val vm = model(server = Server(fail = true), scope = this)

            vm.query("zzz")
            advanceUntilIdle()

            assertTrue(vm.results.value.failed)
        }

    @Test fun `an empty query asks nothing and shows nothing`() =
        runTest(StandardTestDispatcher()) {
            val server = Server()
            val vm = model(server = server, scope = this)

            vm.query("箸")
            vm.query("   ")
            advanceUntilIdle()

            assertEquals(SearchViewModel.Results(), vm.results.value)
            assertEquals(0, server.calls)
        }

    /**
     * The catalogue finishes loading during the debounce. Its rows belong above
     * the server's, so the merge re-reads it rather than trusting the list it
     * built before the wait.
     */
    @Test fun `rows the catalogue gained during the wait are ranked locally`() =
        runTest(StandardTestDispatcher()) {
            var loaded = emptyList<Word>()
            val server = Server(byQuery = mapOf("箸" to listOf(word("remote", term = "remote"))))
            val vm = model(server = server, catalogue = { loaded }, scope = this)

            vm.query("箸")
            assertEquals(emptyList<String>(), vm.results.value.words.map { it.id })
            loaded = catalogue
            advanceUntilIdle()

            assertEquals(
                listOf("hashi", "hashioki", "remote"),
                vm.results.value.words.map { it.id },
            )
        }
}
