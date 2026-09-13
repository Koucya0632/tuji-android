package app.tuji.android.community

import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.AtlasPublicFeed
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasReading
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Reader(var page: () -> AtlasAuthorPage) : AtlasReading {
        override suspend fun feed(limit: Int) = AtlasPublicFeed()
        override suspend fun item(slug: String, lang: String): AtlasPublicDetail? = null
        override suspend fun author(handle: String) = page()
        override suspend fun collections(lang: String, limit: Int) = emptyList<AtlasPublicCollection>()
        override suspend fun collection(slug: String) = AtlasCollectionDetail()
    }

    private val author = AtlasAuthor(handle = "TJ5")
    private fun item(slug: String, lang: TargetLanguage) =
        AtlasPublicItem(id = slug, slug = slug, lemma = slug, targetLanguage = lang, author = author)

    private fun vm(reader: Reader) = AuthorViewModel(handle = "TJ5", atlas = reader, scope = TestScope(dispatcher))

    /** The old page waited forever for an author a failed request never delivered. */
    @Test fun `a failed load is a failure, not an endless spinner`() = runTest(dispatcher) {
        val vm = vm(Reader { throw IOException("down") })
        vm.load(); advanceUntilIdle()
        assertEquals(AuthorViewModel.Phase.Failed, vm.state.value.phase)

        val gone = vm(Reader { throw ApiError.Http(404, null) })
        gone.load(); advanceUntilIdle()
        assertEquals(AuthorViewModel.Phase.NotFound, gone.state.value.phase)
    }

    @Test fun `the items are grouped by language, and collections offer the switch`() = runTest(dispatcher) {
        val vm = vm(
            Reader {
                AtlasAuthorPage(
                    author = author,
                    items = listOf(item("a", TargetLanguage.EN), item("b", TargetLanguage.JA)),
                    collections = listOf(AtlasPublicCollection(id = "c", slug = "c", title = "c")),
                )
            },
        )
        vm.load(); advanceUntilIdle()
        val s = vm.state.value
        assertEquals(AuthorViewModel.Phase.Ready, s.phase)
        assertTrue(s.showsSegments)
        assertEquals(listOf(TargetLanguage.EN, TargetLanguage.JA), s.groups.map { it.language })
    }

    /** An account with no public identity behind it: nothing to show, and nothing to retry. */
    @Test fun `a page with no author is not found`() = runTest(dispatcher) {
        val vm = vm(Reader { AtlasAuthorPage(author = null, items = listOf(item("a", TargetLanguage.EN))) })
        vm.load(); advanceUntilIdle()
        assertEquals(AuthorViewModel.Phase.NotFound, vm.state.value.phase)
        assertTrue(vm.state.value.groups.isEmpty())
    }
}
