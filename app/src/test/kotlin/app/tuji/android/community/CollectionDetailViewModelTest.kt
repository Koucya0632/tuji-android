package app.tuji.android.community

import app.tuji.android.core.community.CollectionLearnAction
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionAccess
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasCollectionLearnResult
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.AtlasPublicFeed
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.AtlasSaveState
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.CollectionBookmarking
import app.tuji.android.core.network.CollectionLearning
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

/** The same decisions iOS's `CollectionDetailVM` makes, pinned where a test can reach them. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val collection = AtlasPublicCollection(
        id = "c1", slug = "kitchen-set", title = "廚房", itemCount = 12, saveCount = 3,
        author = AtlasAuthor(handle = "TJ9"),
    )
    private fun item(slug: String) = AtlasPublicItem(id = slug, slug = slug, lemma = slug)

    private class Reader(var detail: () -> AtlasCollectionDetail) : AtlasReading {
        var calls = 0
        override suspend fun feed(limit: Int) = AtlasPublicFeed()
        override suspend fun item(slug: String, lang: String): AtlasPublicDetail? = null
        override suspend fun author(handle: String) = AtlasAuthorPage()
        override suspend fun collections(lang: String, limit: Int) = emptyList<AtlasPublicCollection>()
        override suspend fun collection(slug: String): AtlasCollectionDetail {
            calls += 1
            return detail()
        }
    }

    private class Bookmarks(var saved: Boolean = false, val count: Int = 4) : CollectionBookmarking {
        var stateReads = 0
        override suspend fun savedCollections(lang: String) = emptyList<AtlasPublicCollection>()
        override suspend fun collectionSaveState(slug: String): AtlasSaveState {
            stateReads += 1
            return AtlasSaveState(saved = saved, saveCount = count)
        }
        override suspend fun saveCollection(slug: String) = AtlasSaveState(saved = true, saveCount = count).also { saved = true }
        override suspend fun unsaveCollection(slug: String) = AtlasSaveState(saved = false, saveCount = count - 1).also { saved = false }
    }

    private fun vm(
        reader: Reader,
        bookmarks: Bookmarks = Bookmarks(),
        learning: CollectionLearning = CollectionLearning { AtlasCollectionLearnResult(learningCount = 12, totalCount = 12) },
        viewer: String? = "TJ1",
        signedIn: Boolean = true,
    ) = CollectionDetailViewModel(
        slug = "kitchen-set",
        atlas = reader,
        bookmarks = bookmarks,
        learning = learning,
        viewerHandle = viewer,
        signedIn = signedIn,
        scope = TestScope(dispatcher),
    )

    private fun locked(items: List<AtlasPublicItem> = listOf(item("a"))) = AtlasCollectionDetail(
        collection = collection,
        items = items,
        access = AtlasCollectionAccess(unlocked = false, isSaved = false, totalCount = 12, learningCount = 0),
    )

    @Test fun `the server's access decides the lock, the bookmark and the counts`() = runTest(dispatcher) {
        val bookmarks = Bookmarks()
        val vm = vm(Reader { locked() }, bookmarks)
        vm.open(); advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.unlocked)
        assertFalse(s.saved)
        assertTrue(s.bookmarkKnown)
        assertEquals(12, s.totalCount)
        assertEquals("access answered it, so the bookmark route is not asked", 0, bookmarks.stateReads)
    }

    /** An older payload with no `access`: ask the bookmark route rather than guess. */
    @Test fun `without access a signed-in reader's bookmark is read from its own route`() = runTest(dispatcher) {
        val bookmarks = Bookmarks(saved = true)
        val vm = vm(Reader { AtlasCollectionDetail(collection = collection, items = listOf(item("a"))) }, bookmarks)
        vm.open(); advanceUntilIdle()

        assertEquals(1, bookmarks.stateReads)
        assertTrue(vm.state.value.saved)
        assertTrue("an old server always sent the whole list", vm.state.value.unlocked)
    }

    /**
     * 收藏 unlocks the members, and a locked payload is only a preview of them —
     * so saving re-reads the detail. Without it the screen kept saying 「收藏合集後
     * 查看全部」 over a collection the reader had just saved.
     */
    @Test fun `saving a locked collection re-reads it and opens the list`() = runTest(dispatcher) {
        var saved = false
        val reader = Reader {
            if (saved) {
                AtlasCollectionDetail(
                    // A fresh read already counts the save.
                    collection = collection.copy(saveCount = 4),
                    items = (1..12).map { item("i$it") },
                    access = AtlasCollectionAccess(unlocked = true, isSaved = true, totalCount = 12),
                )
            } else {
                locked()
            }
        }
        val vm = vm(reader)
        vm.open(); advanceUntilIdle()
        saved = true
        vm.save(); advanceUntilIdle()

        assertEquals(2, reader.calls)
        assertTrue(vm.state.value.unlocked)
        assertEquals(12, vm.state.value.items.size)
        assertEquals(4, vm.state.value.collection?.saveCount)
    }

    /** With no `access` the author's handle decides, case-insensitively, and an owner's 收藏 does nothing. */
    @Test fun `an author cannot bookmark their own collection`() = runTest(dispatcher) {
        val bookmarks = Bookmarks()
        val vm = vm(Reader { AtlasCollectionDetail(collection = collection) }, bookmarks, viewer = "tj9")
        vm.open(); advanceUntilIdle()
        assertTrue(vm.state.value.isOwner)

        vm.save(); advanceUntilIdle()
        assertFalse(bookmarks.saved)
        assertEquals("no save-state read for one's own collection either", 0, bookmarks.stateReads)
    }

    @Test fun `learning the rest updates the counts the button reads`() = runTest(dispatcher) {
        val detail = AtlasCollectionDetail(
            collection = collection,
            access = AtlasCollectionAccess(unlocked = true, isSaved = true, totalCount = 12, learningCount = 8),
        )
        var learned = false
        val vm = CollectionDetailViewModel(
            slug = "kitchen-set", atlas = Reader { detail }, bookmarks = Bookmarks(),
            learning = { AtlasCollectionLearnResult(addedCount = 4, learningCount = 12, totalCount = 12) },
            viewerHandle = "TJ1", signedIn = true, onLearned = { learned = true }, scope = TestScope(dispatcher),
        )
        vm.open(); advanceUntilIdle()
        assertEquals(CollectionLearnAction.AddRemaining(4), vm.state.value.learnAction)

        vm.learnRemaining(); advanceUntilIdle()
        assertEquals(CollectionLearnAction.AllLearning, vm.state.value.learnAction)
        assertTrue(learned)
    }

    /** The one learn failure with something to do about it gets its own sentence. */
    @Test fun `a full study list is told apart from any other failure`() = runTest(dispatcher) {
        val detail = AtlasCollectionDetail(
            collection = collection,
            access = AtlasCollectionAccess(unlocked = true, isSaved = true, totalCount = 12),
        )
        val vm = vm(
            Reader { detail },
            learning = { throw ApiError.Http(429, """{"error":"save_limit","limit":1000}""") },
        )
        vm.open(); advanceUntilIdle()
        vm.learnRemaining(); advanceUntilIdle()
        assertEquals(CollectionDetailViewModel.Error.LearnLimit, vm.state.value.error)
        assertFalse(vm.state.value.learningBusy)
    }
}
