package app.tuji.android.capture

import app.tuji.android.core.model.CreditOperation
import app.tuji.android.credits.CreditCaptureModel
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
}
