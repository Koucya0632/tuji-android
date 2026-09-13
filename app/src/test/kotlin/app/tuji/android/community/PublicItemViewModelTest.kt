package app.tuji.android.community

import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.AtlasPublicFeed
import app.tuji.android.core.model.AtlasSaveState
import app.tuji.android.core.model.ClipPlayback
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.AtlasSaving
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The decisions iOS's `AtlasPublicDetailVM` makes, pinned where a test can reach them. */
@OptIn(ExperimentalCoroutinesApi::class)
class PublicItemViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val detail = AtlasPublicDetail(id = "i1", slug = "bag", lemma = "バッグ")

    private class Reader(val detail: () -> AtlasPublicDetail?) : AtlasReading {
        override suspend fun feed(limit: Int) = AtlasPublicFeed()
        override suspend fun item(slug: String, lang: String) = detail()
        override suspend fun author(handle: String) = AtlasAuthorPage()
        override suspend fun collections(lang: String, limit: Int) = emptyList<AtlasPublicCollection>()
        override suspend fun collection(slug: String) = AtlasCollectionDetail()
    }

    private class Saver(
        var saved: Boolean = false,
        var count: Int = 2,
        val failState: Boolean = false,
        val failWrites: Throwable? = null,
    ) : AtlasSaving {
        var stateReads = 0
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun saveState(slug: String): AtlasSaveState {
            stateReads += 1
            if (failState) throw IOException("down")
            return AtlasSaveState(saved = saved, saveCount = count)
        }
        override suspend fun save(slug: String): AtlasSaveState {
            writes += 1
            gate?.await()
            failWrites?.let { throw it }
            saved = true; count += 1
            return AtlasSaveState(saved = true, saveCount = count)
        }
        override suspend fun unsave(slug: String): AtlasSaveState {
            writes += 1
            failWrites?.let { throw it }
            saved = false; count -= 1
            return AtlasSaveState(saved = false, saveCount = count)
        }
    }

    private object Silent : ClipPlaying {
        override fun canPlay(url: String?, online: Boolean) = false
        override suspend fun play(url: String?, rate: Float) = ClipPlayback.Finished
        override fun stop() = Unit
    }

    private fun vm(
        saver: Saver = Saver(),
        reader: Reader = Reader { detail },
        signedIn: Boolean = true,
        onSaveChanged: () -> Unit = {},
    ) = PublicItemViewModel(
        slug = "bag",
        atlas = reader,
        saver = saver,
        audio = Silent,
        direction = LearningDirection.ZH_JA,
        uiLang = "zh-Hant",
        signedIn = signedIn,
        onSaveChanged = onSaveChanged,
        scope = TestScope(dispatcher),
    )

    /** The bug this screen had: every opening drew 加入學習, even over a word already taken in. */
    @Test fun `opening reads where the reader's save stands`() = runTest(dispatcher) {
        val vm = vm(Saver(saved = true, count = 5))
        vm.open(); advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertEquals(5, vm.state.value.saveCount)
        assertEquals(detail, vm.state.value.item)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `a guest is never asked, so no count is claimed`() = runTest(dispatcher) {
        val saver = Saver(saved = true, count = 5)
        val vm = vm(saver, signedIn = false)
        vm.open(); advanceUntilIdle()
        assertEquals(0, saver.stateReads)
        assertNull(vm.state.value.saveCount)
        assertFalse(vm.state.value.saved)
    }

    @Test fun `the toggle goes both ways and tells the other tabs each time`() = runTest(dispatcher) {
        var told = 0
        val vm = vm(Saver(saved = false, count = 2), onSaveChanged = { told++ })
        vm.open(); advanceUntilIdle()

        vm.toggleSave(); advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertEquals(3, vm.state.value.saveCount)

        vm.toggleSave(); advanceUntilIdle()
        assertFalse(vm.state.value.saved)
        assertEquals(2, vm.state.value.saveCount)
        assertEquals(2, told)
    }

    /** A word the reader believes is out of their 圖鑑 and is not is worse than a second tap. */
    @Test fun `a failed unsave leaves it saved and tells nobody`() = runTest(dispatcher) {
        var told = 0
        val vm = vm(Saver(saved = true, failWrites = IOException("nope")), onSaveChanged = { told++ })
        vm.open(); advanceUntilIdle()
        vm.toggleSave(); advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        assertEquals(PublicItemViewModel.Error.Failed, vm.state.value.error)
        assertFalse(vm.state.value.busy)
        assertEquals(0, told)
    }

    @Test fun `a full 圖鑑 is told apart from any other failure`() = runTest(dispatcher) {
        val vm = vm(Saver(failWrites = ApiError.Http(429, """{"error":"save_limit","limit":1000}""")))
        vm.open(); advanceUntilIdle()
        vm.toggleSave(); advanceUntilIdle()
        assertEquals(PublicItemViewModel.Error.SaveLimit, vm.state.value.error)
    }

    @Test fun `a second tap while one is in flight sends nothing`() = runTest(dispatcher) {
        val saver = Saver().apply { gate = CompletableDeferred() }
        val vm = vm(saver)
        vm.open(); advanceUntilIdle()
        vm.toggleSave(); advanceUntilIdle()
        vm.toggleSave(); advanceUntilIdle()
        assertEquals(1, saver.writes)

        saver.gate!!.complete(Unit); advanceUntilIdle()
        assertTrue(vm.state.value.saved)
    }

    /** The state read is additive: if it fails, the pill keeps its safe default rather than an error. */
    @Test fun `a failed save-state read is quiet`() = runTest(dispatcher) {
        val vm = vm(Saver(failState = true))
        vm.open(); advanceUntilIdle()
        assertFalse(vm.state.value.saved)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `a word that is gone says so instead of spinning`() = runTest(dispatcher) {
        val vm = vm(reader = Reader { throw ApiError.Http(404, null) })
        vm.open(); advanceUntilIdle()
        assertTrue(vm.state.value.notFound)
        assertFalse(vm.state.value.loading)

        val failing = vm(reader = Reader { throw IOException("down") })
        failing.open(); advanceUntilIdle()
        assertTrue(failing.state.value.failed)
        assertFalse(failing.state.value.notFound)
    }
}
