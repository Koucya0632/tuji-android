package app.tuji.android.manage

import app.tuji.android.core.model.AtlasCollectionAvatarResponse
import app.tuji.android.core.model.AtlasCollectionEdit
import app.tuji.android.core.model.AtlasCollectionEditResponse
import app.tuji.android.core.model.AtlasCollectionMember
import app.tuji.android.core.model.AtlasModeration
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.CollectionAuthoring
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
class CollectionEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Fake(
        var review: String = "draft",
        val members: MutableList<AtlasCollectionMember> = mutableListOf(),
        val candidates: List<AtlasCollectionMember> = emptyList(),
        val failAdd: Boolean = false,
    ) : CollectionAuthoring {
        val calls = mutableListOf<String>()
        var created = mutableListOf<AtlasMyCollection>()
        override suspend fun myCollections() = created.toList()
        override suspend fun createCollection(title: String, description: String?, language: TargetLanguage): AtlasMyCollection {
            calls += "create:$title:$description"
            return AtlasMyCollection(id = "new", title = title, targetLanguage = language).also { created.add(it) }
        }
        override suspend fun deleteCollection(id: String) { calls += "delete:$id" }
        override suspend fun collectionForEdit(id: String) = AtlasCollectionEditResponse(
            collection = AtlasCollectionEdit(id = id, title = "廚房", reviewStatus = review, targetLanguage = TargetLanguage.JA),
            items = members.toList(),
        )
        override suspend fun updateCollection(id: String, title: String, description: String?, coverPublicItemId: String?) {
            calls += "update:$title:$description"
        }
        override suspend fun uploadCollectionAvatar(id: String, jpeg: ByteArray) =
            AtlasCollectionAvatarResponse(avatarColor = "#aabbcc", avatarPreviewUrl = "https://x/a.jpg")
        override suspend fun addCollectionItem(id: String, itemId: String) {
            if (failAdd) throw IOException("nope")
            members += candidates.first { it.id == itemId }
        }
        override suspend fun removeCollectionItem(id: String, itemId: String) { members.removeAll { it.id == itemId } }
        override suspend fun publishCollection(id: String): AtlasPublishResult {
            calls += "publish"
            review = "pending_auto"
            return AtlasPublishResult(AtlasModeration(reviewStatus = review, published = false))
        }
        override suspend fun withdrawCollection(id: String) { calls += "withdraw"; review = "withdrawn" }
        override suspend fun collectionCandidates(language: TargetLanguage) = candidates
    }

    private fun member(id: String, state: String = "private") = AtlasCollectionMember(id = id, lemma = id, publicationState = state)

    private fun vm(fake: Fake, onChanged: () -> Unit = {}) = CollectionEditViewModel("c1", fake, onChanged, TestScope(dispatcher))

    /** What goes to review is what is on screen: the words are written first. */
    @Test fun `publishing saves the words first and reads back the review state`() = runTest(dispatcher) {
        var told = 0
        val fake = Fake(members = mutableListOf(member("a")))
        val vm = vm(fake) { told++ }
        vm.load(); advanceUntilIdle()
        vm.setTitle(" 我的廚房 ")
        vm.submit(); advanceUntilIdle()

        assertEquals(listOf("update:我的廚房:null", "publish"), fake.calls)
        assertEquals(false, vm.state.value.published)
        assertFalse("pending now, so no second submit", vm.state.value.canSubmit)
        assertEquals(1, told)
    }

    @Test fun `an empty collection cannot be put up for review`() = runTest(dispatcher) {
        val fake = Fake()
        val vm = vm(fake)
        vm.load(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertTrue(fake.calls.isEmpty())
    }

    /** A tile left ✓ over a card that never went in is worse than a second tap. */
    @Test fun `a refused add is unmarked and says so`() = runTest(dispatcher) {
        val fake = Fake(candidates = listOf(member("x")), failAdd = true)
        val vm = vm(fake)
        vm.load(); advanceUntilIdle()
        vm.loadCandidates(); advanceUntilIdle()
        vm.addMember("x"); advanceUntilIdle()
        assertFalse("x" in vm.state.value.added)
        assertEquals(CollectionEditViewModel.Failure.Member, vm.state.value.failure)
    }

    @Test fun `an added card stays in the picker, ticked, and joins the members`() = runTest(dispatcher) {
        val fake = Fake(candidates = listOf(member("x"), member("y")))
        val vm = vm(fake)
        vm.load(); advanceUntilIdle()
        vm.loadCandidates(); advanceUntilIdle()
        vm.addMember("x"); advanceUntilIdle()
        assertEquals(listOf("x"), vm.state.value.members.map { it.id })
        assertEquals(listOf("x", "y"), vm.state.value.available.map { it.id })
        assertTrue("x" in vm.state.value.added)
    }

    @Test fun `withdrawing a public collection reads back its state`() = runTest(dispatcher) {
        val fake = Fake(review = "approved", members = mutableListOf(member("a", "public")))
        val vm = vm(fake)
        vm.load(); advanceUntilIdle()
        assertTrue(vm.state.value.canWithdraw)
        vm.withdraw(); advanceUntilIdle()
        assertFalse(vm.state.value.canWithdraw)
        assertTrue("withdrawn is submittable again", vm.state.value.canSubmit)
    }

    @Test fun `a new collection leads the shelf`() = runTest(dispatcher) {
        val fake = Fake()
        val list = MyCollectionsViewModel(fake, TargetLanguage.JA, scope = TestScope(dispatcher))
        var opened: String? = null
        list.load(); advanceUntilIdle()
        list.create("  廚房 ", " ") { opened = it.id }; advanceUntilIdle()
        assertEquals(listOf("create:廚房:null"), fake.calls)
        assertEquals(listOf("new"), list.state.value.visible.map { it.id })
        assertEquals("new", opened)
    }
}
