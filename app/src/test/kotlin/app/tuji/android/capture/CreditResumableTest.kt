package app.tuji.android.capture

import app.tuji.android.core.model.CreditOperation
import app.tuji.android.credits.CreditCaptureModel
import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.network.ApiError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which paid-for work 拍照新增 reopens on. */
class CreditResumableTest {
    private fun op(id: String, state: String, image: String, item: String? = null) = CreditOperation(
        id = id, state = state, feature = "atlas.recognize.primary", targetLanguage = "en", imageId = image,
        points = 1, confirmedItemId = item, fulfillmentState = "completed",
    )

    @Test fun `a run in flight wins`() {
        assertEquals("b", CreditCaptureModel.resumable(listOf(op("a", "committed", "1"), op("b", "running", "2")))?.id)
    }

    @Test fun `a photo whose card exists is finished for both runs`() {
        assertNull(CreditCaptureModel.resumable(listOf(op("a", "committed", "1"), op("b", "committed", "1", item = "x"))))
    }

    @Test fun `one the queue is confirming is not waiting`() {
        assertNull(CreditCaptureModel.resumable(listOf(op("a", "committed", "1")), queued = setOf("a")))
    }

    @Test fun `繼續確認 asks about one photo, not the newest`() {
        val operations = listOf(op("new", "committed", "2"), op("old", "committed", "1"))
        assertEquals("old", CreditCaptureModel.resumable("1", operations)?.id)
        assertNull(CreditCaptureModel.resumable("3", operations))
    }

    private val photos: suspend (String) -> AtlasImageSummary = { AtlasImageSummary(it) }

    private suspend fun reopened(
        operations: List<CreditOperation>,
        resume: String? = null,
        aside: Set<String> = emptySet(),
        image: suspend (String) -> AtlasImageSummary = photos,
    ) = CreditCaptureModel.reopen(operations, emptySet(), resume, aside, image)

    @Test fun `a result whose photo was deleted is passed over for the next one waiting`() = runTest {
        val found = reopened(listOf(op("orphan", "committed", "gone"), op("older", "committed", "1"))) {
            if (it == "gone") throw ApiError.Http(404, null) else AtlasImageSummary(it)
        }
        assertEquals("older", found?.operation?.id)
        assertEquals("1", found?.image?.id)
    }

    @Test fun `when the only result waiting has no photo there is nothing to reopen`() = runTest {
        assertNull(reopened(listOf(op("orphan", "committed", "gone"))) { throw ApiError.Http(404, null) })
    }

    @Test fun `a photo that could not be fetched is not mistaken for a deleted one`() = runTest {
        val found = reopened(listOf(op("a", "committed", "1"))) { throw ApiError.Http(503, null) }
        assertEquals("a", found?.operation?.id)
        assertNull(found?.image)
    }

    @Test fun `換一張 puts a result aside so the one behind it opens`() = runTest {
        val operations = listOf(op("new", "committed", "2"), op("old", "committed", "1"))
        assertEquals("old", reopened(operations, aside = setOf("2"))?.operation?.id)
        assertNull(reopened(operations, aside = setOf("1", "2")))
    }

    @Test fun `繼續確認 still opens a result that was set aside`() = runTest {
        assertEquals("a", reopened(listOf(op("a", "committed", "1")), resume = "1", aside = setOf("1"))?.operation?.id)
    }
}
