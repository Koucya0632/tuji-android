package app.tuji.android.manage

import app.tuji.android.core.community.ReviewStatus
import app.tuji.android.core.community.ShelfState
import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.AtlasItemSpotState
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.AtlasSyncResponse
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasShelfManaging
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
class AtlasManageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Shelf(
        var images: List<AtlasImageSummary>,
        var items: List<AtlasItem>,
        val failDelete: Set<String> = emptySet(),
        val failPublish: Set<String> = emptySet(),
        var failSync: Boolean = false,
    ) : AtlasShelfManaging {
        val deleted = mutableListOf<String>()
        val withdrawn = mutableListOf<String>()
        val published = mutableListOf<String>()
        override suspend fun sync(): AtlasSyncResponse {
            if (failSync) throw IOException("down")
            return AtlasSyncResponse(images = images, items = items)
        }
        override suspend fun deleteImage(imageId: String) {
            if (imageId in failDelete) throw IOException("nope")
            deleted += imageId
            images = images.filterNot { it.id == imageId }
        }
        override suspend fun withdrawItem(itemId: String) {
            withdrawn += itemId
            items = items.map { if (it.id == itemId) it.copy(reviewStatus = "withdrawn") else it }
        }
        override suspend fun publishItem(itemId: String): AtlasPublishResult {
            if (itemId in failPublish) throw IOException("nope")
            published += itemId
            items = items.map { if (it.id == itemId) it.copy(reviewStatus = "pending") else it }
            return AtlasPublishResult(moderation = null)
        }
        var failSpot = false
        val spotWrites = mutableListOf<Triple<String, String?, Boolean>>()
        override suspend fun setSpot(itemId: String, spotId: String, shared: Boolean): AtlasItemSpotState {
            if (failSpot) throw IOException("nope")
            spotWrites += Triple(itemId, spotId, shared)
            items = items.map { if (it.id == itemId) it.copy(spotId = spotId, spotShared = shared) else it }
            return AtlasItemSpotState(spotId, shared)
        }
        override suspend fun clearSpot(itemId: String): AtlasItemSpotState {
            if (failSpot) throw IOException("nope")
            spotWrites += Triple(itemId, null, false)
            items = items.map { if (it.id == itemId) it.copy(spotId = null, spotShared = false) else it }
            return AtlasItemSpotState()
        }
    }

    private fun image(id: String) = AtlasImageSummary(id = id, status = "cards_ready", createdAt = id)
    private fun item(id: String, lang: TargetLanguage = TargetLanguage.JA, review: String? = null) =
        AtlasItem(id = "i$id", imageId = id, targetLanguage = lang, lemma = id, reviewStatus = review)

    private fun vm(shelf: Shelf, onChanged: () -> Unit = {}) =
        AtlasManageViewModel(shelf, TargetLanguage.JA, onChanged, TestScope(dispatcher))

    @Test fun `a failed sync says so rather than claiming there are no cards`() = runTest(dispatcher) {
        val vm = vm(Shelf(emptyList(), emptyList(), failSync = true))
        vm.load(); advanceUntilIdle()
        assertEquals(ShelfState.Failed, vm.state.value.shelf)
    }

    @Test fun `a batch delete keeps what failed selected and tells the other tabs once`() = runTest(dispatcher) {
        var told = 0
        val shelf = Shelf(listOf(image("a"), image("b"), image("c")), listOf(item("a"), item("b"), item("c")), failDelete = setOf("b"))
        val vm = vm(shelf) { told++ }
        vm.load(); advanceUntilIdle()
        vm.setSelecting(true)
        listOf("a", "b", "c").forEach(vm::toggle)
        vm.delete(vm.state.value.selected); advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("b"), s.rows.map { it.id })
        assertEquals(setOf("b"), s.selected)
        assertTrue("still selecting, so the failure can be retried", s.selecting)
        assertTrue(s.actionFailed)
        assertEquals(1, told)
    }

    @Test fun `a clean delete ends selection`() = runTest(dispatcher) {
        var done = false
        val vm = vm(Shelf(listOf(image("a")), listOf(item("a"))))
        vm.load(); advanceUntilIdle()
        vm.setSelecting(true); vm.toggle("a")
        vm.delete(setOf("a")) { done = true }; advanceUntilIdle()
        assertFalse(vm.state.value.selecting)
        assertTrue(done)
        assertEquals(ShelfState.Empty, vm.state.value.shelf)
    }

    /** The server owns what a withdrawn card is; the shelf reads it back. */
    @Test fun `withdrawing re-reads the card's review state`() = runTest(dispatcher) {
        var told = 0
        val shelf = Shelf(listOf(image("a")), listOf(item("a", review = "approved")))
        val vm = vm(shelf) { told++ }
        vm.load(); advanceUntilIdle()
        vm.withdraw("ia"); advanceUntilIdle()
        assertEquals(listOf("ia"), shelf.withdrawn)
        assertEquals(ReviewStatus.Withdrawn, vm.state.value.row("a")?.review)
        assertEquals(1, told)
    }

    /** 景點 on the card page: written, then read back from sync like 取消公開. */
    @Test fun `a spot is set, shared and taken off, and the shelf reads each back`() = runTest(dispatcher) {
        val shelf = Shelf(listOf(image("a")), listOf(item("a", review = "approved")))
        val vm = vm(shelf)
        vm.load(); advanceUntilIdle()

        vm.setSpot("ia", "asakusa", shared = false); advanceUntilIdle()
        assertEquals("asakusa", vm.state.value.items.single().spotId)
        vm.setSpot("ia", "asakusa", shared = true); advanceUntilIdle()
        assertTrue(vm.state.value.items.single().spotShared)
        vm.setSpot("ia", null, shared = false); advanceUntilIdle()
        assertEquals(null, vm.state.value.items.single().spotId)

        assertEquals(
            listOf(Triple("ia", "asakusa", false), Triple("ia", "asakusa", true), Triple("ia", null, false)),
            shelf.spotWrites,
        )
        assertFalse(vm.state.value.savingSpot)
        assertFalse(vm.state.value.actionFailed)
    }

    @Test fun `a refused spot change says so and changes nothing`() = runTest(dispatcher) {
        val shelf = Shelf(listOf(image("a")), listOf(item("a"))).apply { failSpot = true }
        val vm = vm(shelf)
        vm.load(); advanceUntilIdle()
        vm.setSpot("ia", "asakusa", shared = false); advanceUntilIdle()
        assertTrue(vm.state.value.actionFailed)
        assertEquals(null, vm.state.value.items.single().spotId)
    }
}

