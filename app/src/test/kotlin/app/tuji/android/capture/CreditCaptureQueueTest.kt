package app.tuji.android.capture

import app.tuji.android.core.model.AtlasCard
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.AtlasRecognitionResponse
import app.tuji.android.core.model.AtlasUploadResponse
import app.tuji.android.core.model.CaptureFailure
import app.tuji.android.core.model.CaptureJobRecord
import app.tuji.android.core.model.CaptureProgress
import app.tuji.android.core.model.CreditConfirmRequest
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasAuthoring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 生成佇列's 罐頭點數 kind: confirm through the operation, then wait out the server's fill-in. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreditCaptureQueueTest {

    private val dispatcher = StandardTestDispatcher()

    private class Authoring : AtlasAuthoring {
        var freeConfirms = 0
        val carded = mutableListOf<String>()
        override suspend fun uploadImage(bytes: ByteArray, filename: String, mimeType: String, targetLanguage: TargetLanguage?): AtlasUploadResponse = error("not used")
        override suspend fun recognize(imageId: String, mode: RecognitionMode): AtlasRecognitionResponse = error("not used")
        override suspend fun confirm(imageId: String, payload: AtlasConfirmPayload): AtlasItem { freeConfirms++; return AtlasItem(id = "free", lemma = payload.lemma) }
        override suspend fun createCards(itemId: String, cardTypes: List<String>): List<AtlasCard> { carded += itemId; return emptyList() }
        override suspend fun publish(itemId: String): AtlasPublishResult = error("not used")
    }

    private class Credits(var fill: List<String>, val fail: Throwable? = null) : CreditCardConfirming {
        val confirms = mutableListOf<CreditConfirmRequest>()
        var polls = 0
        override suspend fun confirm(request: CreditConfirmRequest): CreditConfirmed {
            confirms += request
            fail?.let { throw it }
            return CreditConfirmed("item-${request.operationId}", "pending")
        }
        override suspend fun fulfillmentState(operationId: String): String = fill[minOf(polls++, fill.size - 1)]
    }

    private class Journal : CaptureJobJournal {
        val records = mutableMapOf<String, CaptureJobRecord>()
        override fun save(record: CaptureJobRecord) { records[record.id] = record }
        override fun remove(id: String) { records.remove(id) }
        override fun removeAll() { records.clear() }
        override fun restore(): List<CaptureJobRecord> = records.values.toList()
    }

    private val request = CreditConfirmRequest("op1", "c1", "kettle", "水壺")

    @Test fun `a credit job confirms through the operation and waits for the fill-in`() = runTest(dispatcher) {
        val authoring = Authoring()
        val credits = Credits(fill = listOf("running", "completed"))
        val q = AtlasCaptureQueue(authoring, Journal(), this, credits = credits, doneLingerMillis = 0, enrichmentPollMillis = 10)
        q.enqueue(request, imageId = "img1", thumbUrl = null)
        assertEquals(setOf("op1"), q.creditOperationIds)
        assertEquals("a credit job's slot is already counted by the server", 0, q.inFlightCount)
        advanceUntilIdle()

        assertEquals(listOf(request), credits.confirms)
        assertEquals(0, authoring.freeConfirms)
        assertEquals(listOf("item-op1"), authoring.carded)
        assertEquals(2, credits.polls)
        assertTrue(q.jobs.value.isEmpty())
    }

    @Test fun `an unfinished fill-in stops at the deadline`() = runTest(dispatcher) {
        val credits = Credits(fill = listOf("running"))
        val q = AtlasCaptureQueue(Authoring(), Journal(), this, credits = credits, doneLingerMillis = 0,
            enrichmentPollMillis = 10, enrichmentDeadlineMillis = 50)
        q.enqueue(request, imageId = "img1", thumbUrl = null)
        advanceUntilIdle()
        assertEquals(5, credits.polls)
        assertTrue(q.jobs.value.isEmpty())
    }

    @Test fun `capacity_full from the credit confirm is a dead end, not a retry`() = runTest(dispatcher) {
        val credits = Credits(fill = listOf("completed"), fail = ApiError.Http(409, """{"error":"capacity_full","message":"滿了"}"""))
        val q = AtlasCaptureQueue(Authoring(), Journal(), this, credits = credits)
        q.enqueue(request, imageId = "img1", thumbUrl = null)
        advanceUntilIdle()
        assertEquals(CaptureProgress.Failed(CaptureFailure.AtCapacity("滿了")), q.jobs.value.single().progress)
    }
}
