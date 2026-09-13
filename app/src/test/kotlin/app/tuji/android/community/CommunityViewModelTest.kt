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
import app.tuji.android.core.network.AtlasSaving
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

    private class Saver(val fail: Boolean = false) : AtlasSaving {
        var calls = 0
        override suspend fun save(slug: String) {
            calls += 1
            if (fail) throw IOException("nope")
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
        saver: AtlasSaving = Saver(),
        bookmarks: CollectionBookmarking = SavedShelf(),
        reporter: ReportSubmitting = Reporter(),
        blocked: List<String> = emptyList(),
        blocksFail: Boolean = false,
        direction: LearningDirection = LearningDirection.ZH_JA,
        onSaved: () -> Unit = {},
    ) = CommunityViewModel(
        atlas = reader,
        saver = saver,
        bookmarks = bookmarks,
        onSaved = onSaved,
        reporter = reporter,
        blocks = object : BlockListing {
            override suspend fun blockedHandles() =
                if (blocksFail) throw IOException("down") else blocked
        },
        direction = direction,
        uiLang = "zh-Hant",
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

    @Test fun `an author page is filtered by the same list`() = runTest(dispatcher) {
        val page = AtlasAuthorPage(
            author = AtlasAuthor(handle = "TJ2"),
            items = feedItems,
            collections = feedCols,
        )
        val vm = vm(reader = Reader(feedItems, feedCols, page = page), blocked = listOf("TJ1"))
        vm.load(); advanceUntilIdle()
        vm.openAuthor("TJ2"); advanceUntilIdle()
        assertEquals(listOf("b"), vm.author.value!!.items.map { it.slug })
    }

    @Test fun `saving marks it saved`() = runTest(dispatcher) {
        val detail = AtlasPublicDetail(id = "a", slug = "a", lemma = "a")
        val saver = Saver()
        val vm = vm(reader = Reader(detail = detail), saver = saver)
        vm.openItem("a"); advanceUntilIdle()
        vm.save(); advanceUntilIdle()
        assertEquals(1, saver.calls)
        assertTrue((vm.item.value as CommunityViewModel.ItemState.Loaded).saved)
    }

    /**
     * 收藏 lands on a shelf another tab draws. Without this call, the word is
     * saved and 圖鑑's 已收進 goes on showing the shelf as it was.
     */
    @Test fun `a save tells whoever draws the other shelf`() = runTest(dispatcher) {
        var told = 0
        val detail = AtlasPublicDetail(id = "a", slug = "a", lemma = "a")
        val vm = vm(reader = Reader(detail = detail), onSaved = { told++ })
        vm.openItem("a"); advanceUntilIdle()
        vm.save(); advanceUntilIdle()
        assertEquals(1, told)
    }

    /** Nothing changed, so nothing to reload. */
    @Test fun `a failed save tells nobody`() = runTest(dispatcher) {
        var told = 0
        val detail = AtlasPublicDetail(id = "a", slug = "a", lemma = "a")
        val vm = vm(
            reader = Reader(detail = detail),
            saver = Saver(fail = true),
            onSaved = { told++ },
        )
        vm.openItem("a"); advanceUntilIdle()
        vm.save(); advanceUntilIdle()
        assertEquals(0, told)
    }

    @Test fun `a failed save does not claim to have saved`() = runTest(dispatcher) {
        // A card the user believes is in their 圖鑑 and is not is worse than
        // asking them to tap again.
        val detail = AtlasPublicDetail(id = "a", slug = "a", lemma = "a")
        val vm = vm(reader = Reader(detail = detail), saver = Saver(fail = true))
        vm.openItem("a"); advanceUntilIdle()
        vm.save(); advanceUntilIdle()
        val s = vm.item.value as CommunityViewModel.ItemState.Loaded
        assertFalse(s.saved)
        assertFalse(s.saving)
    }

    @Test fun `a second tap while saving does not send twice`() = runTest(dispatcher) {
        val detail = AtlasPublicDetail(id = "a", slug = "a", lemma = "a")
        val saver = Saver()
        val vm = vm(reader = Reader(detail = detail), saver = saver)
        vm.openItem("a"); advanceUntilIdle()
        vm.save(); vm.save()
        advanceUntilIdle()
        assertEquals(1, saver.calls)
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

    @Test fun `a missing item is a failure, not an empty screen`() = runTest(dispatcher) {
        val vm = vm(reader = Reader(detail = null))
        vm.openItem("gone"); advanceUntilIdle()
        assertTrue(vm.item.value is CommunityViewModel.ItemState.Failed)
    }
}
