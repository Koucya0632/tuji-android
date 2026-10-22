package app.tuji.android.community

import app.tuji.android.core.community.BlockList
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.AtlasSearchResponse
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.AtlasSearching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Pins 物見搜尋's model: typing collapses into one request in the learning
 * language, a stale answer never replaces a newer query, clearing goes back to
 * idle, a failure is shown and retried, long input is trimmed to the backend's
 * cap, and blocked authors are hidden without asking again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunitySearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun collection(id: String, handle: String = "TJ1") =
        AtlasPublicCollection(id = id, slug = id, title = id, author = AtlasAuthor(handle = handle))

    private fun item(id: String, handle: String = "TJ1") =
        AtlasPublicItem(id = id, slug = id, lemma = "猫", author = AtlasAuthor(handle = handle))

    private class Stub(var response: AtlasSearchResponse = AtlasSearchResponse()) : AtlasSearching {
        val calls = mutableListOf<Pair<String, String>>()
        var fail = false

        override suspend fun search(q: String, lang: String): AtlasSearchResponse {
            calls += q to lang
            if (fail) throw IOException("offline")
            return response
        }
    }

    private fun TestScope.vm(
        stub: Stub,
        direction: LearningDirection = LearningDirection.ZH_JA,
        blocks: () -> BlockList = { BlockList.none },
    ) = CommunitySearchViewModel(
        remote = stub,
        direction = direction,
        blocks = blocks,
        scope = this,
    )

    @Test
    fun typingSearchesOnceForTheLastQueryInTheLearningLanguage() = runTest(dispatcher) {
        val stub = Stub(AtlasSearchResponse(collections = listOf(collection("c1")), items = listOf(item("i1"))))
        val vm = vm(stub)

        vm.query("貓")
        vm.query("貓咪")
        advanceUntilIdle()

        assertEquals(listOf("貓咪" to "ja"), stub.calls)
        val r = vm.results.value
        assertEquals(CommunitySearchViewModel.Phase.Ready, r.phase)
        assertEquals("貓咪", r.query)
        assertEquals(listOf("c1"), r.collections.map { it.id })
        assertEquals(listOf("i1"), r.items.map { it.id })
    }

    @Test
    fun englishLearnersSearchTheEnglishDeck() = runTest(dispatcher) {
        val stub = Stub()
        val vm = vm(stub, direction = LearningDirection.ZH_EN)

        vm.queryNow("cat")
        advanceUntilIdle()

        assertEquals(listOf("cat" to "en"), stub.calls)
    }

    @Test
    fun clearingTheFieldGoesBackToIdle() = runTest(dispatcher) {
        val stub = Stub(AtlasSearchResponse(collections = listOf(collection("c1"))))
        val vm = vm(stub)

        vm.queryNow("cat")
        advanceUntilIdle()
        vm.query("   ")

        val r = vm.results.value
        assertEquals(CommunitySearchViewModel.Phase.Idle, r.phase)
        assertTrue(r.isEmpty)
    }

    @Test
    fun aFailureIsShownAndRetryRunsTheSameQuery() = runTest(dispatcher) {
        val stub = Stub().apply { fail = true }
        val vm = vm(stub)

        vm.queryNow("kettle")
        advanceUntilIdle()
        assertEquals(CommunitySearchViewModel.Phase.Failed, vm.results.value.phase)

        stub.fail = false
        vm.queryNow("kettle")
        advanceUntilIdle()

        assertEquals(listOf("kettle", "kettle"), stub.calls.map { it.first })
        assertEquals(CommunitySearchViewModel.Phase.Ready, vm.results.value.phase)
    }

    @Test
    fun longInputIsTrimmedToTheBackendCap() = runTest(dispatcher) {
        val stub = Stub()
        val vm = vm(stub)

        vm.queryNow("  " + "a".repeat(60) + "  ")
        advanceUntilIdle()

        assertEquals(CommunitySearchViewModel.MAX_QUERY, stub.calls.single().first.length)
    }

    @Test
    fun blockedAuthorsAreHiddenWithoutAskingAgain() = runTest(dispatcher) {
        val stub = Stub(
            AtlasSearchResponse(
                collections = listOf(collection("c1", "TJ2"), collection("c2")),
                items = listOf(item("i1", "TJ2"), item("i2")),
            ),
        )
        var blocked = BlockList.none
        val vm = vm(stub, blocks = { blocked })
        vm.queryNow("x")
        advanceUntilIdle()

        blocked = BlockList.of(listOf("TJ2"))
        vm.refilter()
        assertEquals(listOf("c2"), vm.results.value.collections.map { it.id })
        assertEquals(listOf("i2"), vm.results.value.items.map { it.id })

        blocked = BlockList.none
        vm.refilter()
        assertEquals(listOf("c1", "c2"), vm.results.value.collections.map { it.id })
        assertEquals(1, stub.calls.size)
    }
}
