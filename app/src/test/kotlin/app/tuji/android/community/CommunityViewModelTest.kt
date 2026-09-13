package app.tuji.android.community

import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.AtlasPublicFeed
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.BlockListing
import app.tuji.android.core.network.CollectionBookmarking
import app.tuji.android.core.model.AtlasSaveState
import app.tuji.android.core.network.ReportSubmitting
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(slug: String, handle: String) = AtlasPublicItem(
        id = slug, slug = slug, lemma = slug, author = AtlasAuthor(handle = handle),
    )

    private fun collection(slug: String, handle: String) = AtlasPublicCollection(
        id = slug, slug = slug, title = slug, author = AtlasAuthor(handle = handle),
    )

    private val feedItems = listOf(item("a", "TJ1"), item("b", "TJ2"), item("c", "TJ1"))
    private val feedCols = listOf(collection("c1", "TJ1"), collection("c2", "TJ2"))

    private open class Reader(
        val items: List<AtlasPublicItem> = emptyList(),
        val cols: List<AtlasPublicCollection> = emptyList(),
        val detail: AtlasPublicDetail? = null,
        val page: AtlasAuthorPage = AtlasAuthorPage(),
        val failFeed: Boolean = false,
        val failCollections: Boolean = false,
        val collectionDetail: AtlasCollectionDetail = AtlasCollectionDetail(),
    ) : AtlasReading {
        var langAsked: String? = null
        override suspend fun feed(limit: Int) =
            if (failFeed) throw IOException("down") else AtlasPublicFeed(items)
        override suspend fun item(slug: String, lang: String) = detail
        override suspend fun author(handle: String) = page
        override suspend fun collections(lang: String, limit: Int): List<AtlasPublicCollection> {
            langAsked = lang
            if (failCollections) throw IOException("down")
            return cols
        }
        override suspend fun collection(slug: String) = collectionDetail
    }

    private class Blocks(
        val handles: List<String> = emptyList(),
        val failList: Boolean = false,
        val failWrites: Boolean = false,
    ) : BlockListing {
        val written = mutableListOf<String>()
        override suspend fun blockedHandles() = if (failList) throw IOException("down") else handles
        override suspend fun block(handle: String) {
            if (failWrites) throw IOException("nope")
            written += "block:$handle"
        }
        override suspend fun unblock(handle: String) {
            if (failWrites) throw IOException("nope")
            written += "unblock:$handle"
        }
    }

    private class SavedShelf(val cols: List<AtlasPublicCollection> = emptyList()) : CollectionBookmarking {
        var langAsked: String? = null
        override suspend fun savedCollections(lang: String): List<AtlasPublicCollection> {
            langAsked = lang
            return cols
        }
        override suspend fun collectionSaveState(slug: String) = AtlasSaveState()
        override suspend fun saveCollection(slug: String) = AtlasSaveState(saved = true)
        override suspend fun unsaveCollection(slug: String) = AtlasSaveState()
    }

    private class Reporter : ReportSubmitting {
        val sent = mutableListOf<Triple<ReportTarget, ReportReason, String?>>()
        override suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?) {
            sent += Triple(target, reason, detail)
        }
    }

    private fun vm(
        reader: AtlasReading = Reader(feedItems, feedCols),
        bookmarks: CollectionBookmarking = SavedShelf(),
        reporter: ReportSubmitting = Reporter(),
        blocked: List<String> = emptyList(),
        blocksFail: Boolean = false,
        blocks: BlockListing = Blocks(blocked, failList = blocksFail),
        direction: LearningDirection = LearningDirection.ZH_JA,
    ) = CommunityViewModel(
        atlas = reader,
        bookmarks = bookmarks,
        reporter = reporter,
        blocks = blocks,
        direction = direction,
        scope = TestScope(dispatcher),
    )

    @Test fun `探索 lists the published collections`() = runTest(dispatcher) {
        val vm = vm(); vm.load(); advanceUntilIdle()
        assertEquals(listOf("c1", "c2"), vm.explore.value.collections.map { it.slug })
        assertFalse(vm.explore.value.loading)
    }

    @Test fun `a blocked author's collections are gone`() = runTest(dispatcher) {
        val vm = vm(blocked = listOf("TJ1")); vm.load(); advanceUntilIdle()
        assertEquals(listOf("c2"), vm.explore.value.collections.map { it.slug })
    }

    @Test fun `a failed block list shows everything rather than nothing`() = runTest(dispatcher) {
        // Fail open. One small request must not take 物見 away from everyone.
        val vm = vm(blocksFail = true); vm.load(); advanceUntilIdle()
        assertEquals(2, vm.explore.value.collections.size)
        assertFalse(vm.explore.value.failed)
    }

    @Test fun `a failed shelf says so`() = runTest(dispatcher) {
        val vm = vm(reader = Reader(failCollections = true)); vm.load(); advanceUntilIdle()
        assertTrue(vm.explore.value.failed)
        assertFalse(vm.explore.value.loading)
    }

    @Test fun `已收藏 is asked in the same language and filtered by the same block list`() = runTest(dispatcher) {
        val bookmarks = SavedShelf(feedCols)
        val vm = vm(bookmarks = bookmarks, blocked = listOf("TJ2"), direction = LearningDirection.ZH_EN)
        vm.load(); advanceUntilIdle()
        vm.loadSaved(); advanceUntilIdle()
        assertEquals("en", bookmarks.langAsked)
        assertEquals(listOf("c1"), vm.saved.value.collections.map { it.slug })
    }

    @Test fun `the collections feed is scoped to the direction`() = runTest(dispatcher) {
        val ja = Reader(feedItems, feedCols)
        vm(reader = ja, direction = LearningDirection.ZH_JA).also { it.load() }
        advanceUntilIdle()
        assertEquals("ja", ja.langAsked)

        val en = Reader(feedItems, feedCols)
        vm(reader = en, direction = LearningDirection.ZH_EN).also { it.load() }
        advanceUntilIdle()
        assertEquals("en", en.langAsked)
    }

    /** Optimistic: a block that visibly does nothing is worse than an over-eager hide. */
    @Test fun `blocking empties the shelves before the server answers`() = runTest(dispatcher) {
        val blocks = Blocks()
        val vm = vm(blocks = blocks, bookmarks = SavedShelf(feedCols))
        vm.load(); advanceUntilIdle()
        vm.loadSaved(); advanceUntilIdle()

        var left = false
        vm.block("tj1") { left = true }
        assertEquals(listOf("c2"), vm.explore.value.collections.map { it.slug })
        assertEquals(listOf("c2"), vm.saved.value.collections.map { it.slug })
        assertTrue(vm.blocked.value.hides("TJ1"))
        assertFalse("the screen leaves only once the server has it", left)

        advanceUntilIdle()
        assertEquals(listOf("block:tj1"), blocks.written)
        assertTrue(left)
    }

    @Test fun `a refused block puts everything back and stays put`() = runTest(dispatcher) {
        val vm = vm(blocks = Blocks(failWrites = true))
        vm.load(); advanceUntilIdle()

        var left = false
        vm.block("TJ1") { left = true }
        advanceUntilIdle()
        assertFalse(vm.blocked.value.hides("TJ1"))
        assertEquals(listOf("c1", "c2"), vm.explore.value.collections.map { it.slug })
        assertFalse(left)
    }

    /** No refetch: the shelf as the server sent it is kept, so their collections come straight back. */
    @Test fun `unblocking brings their collections back`() = runTest(dispatcher) {
        val blocks = Blocks(handles = listOf("TJ1"))
        val vm = vm(blocks = blocks)
        vm.load(); advanceUntilIdle()
        assertEquals(listOf("c2"), vm.explore.value.collections.map { it.slug })

        vm.unblock("TJ1"); advanceUntilIdle()
        assertEquals(listOf("c1", "c2"), vm.explore.value.collections.map { it.slug })
        assertEquals(listOf("unblock:TJ1"), blocks.written)
    }

    @Test fun `a refused unblock keeps the author hidden`() = runTest(dispatcher) {
        val vm = vm(blocks = Blocks(handles = listOf("TJ1"), failWrites = true))
        vm.load(); advanceUntilIdle()
        vm.unblock("TJ1"); advanceUntilIdle()
        assertTrue(vm.blocked.value.hides("TJ1"))
        assertEquals(listOf("c2"), vm.explore.value.collections.map { it.slug })
    }

    @Test fun `a report carries its target and reason`() = runTest(dispatcher) {
        val reporter = Reporter()
        val vm = vm(reporter = reporter)
        vm.report(ReportTarget.Author("TJ9"), ReportReason.Spam, "廣告")
        advanceUntilIdle()
        assertEquals(1, reporter.sent.size)
        assertEquals(ReportTarget.Author("TJ9"), reporter.sent[0].first)
        assertEquals("spam", reporter.sent[0].second.wire)
        assertEquals("廣告", reporter.sent[0].third)
        assertEquals(ReportTarget.Author("TJ9"), vm.reported.value)
    }
}
