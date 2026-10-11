package app.tuji.android.capture

import app.tuji.android.core.model.AtlasCard
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.CaptureFailure
import app.tuji.android.core.model.CaptureJobRecord
import app.tuji.android.core.model.CaptureProgress
import app.tuji.android.core.model.CreditConfirmRequest
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasAuthoring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 生成佇列: confirm through the 識別操作, make the cards, wait out the server's
 * fill-in — and, because every step is safe to repeat, simply run again after a
 * failure or an app kill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AtlasCaptureQueueTest {

    private val dispatcher = StandardTestDispatcher()

    private class Authoring : AtlasAuthoring {
        val carded = mutableListOf<String>()
        override suspend fun createCards(itemId: String, cardTypes: List<String>): List<AtlasCard> { carded += itemId; return emptyList() }
        override suspend fun publish(itemId: String): AtlasPublishResult = error("not used")
    }

    private class Credits(var fill: List<String> = listOf("completed"), var fail: Throwable? = null) : CreditCardConfirming {
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
        advanceUntilIdle()

        assertEquals(listOf(request), credits.confirms)
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

    @Test fun `a job restored after an app kill runs again from the top`() = runTest(dispatcher) {
        // No checkpoint to consult: the server answers the resent confirm with the card it already bound.
        val journal = Journal().apply { save(CaptureJobRecord("j1", "img1", "kettle", null, request)) }
        val authoring = Authoring()
        val credits = Credits()
        val q = AtlasCaptureQueue(authoring, journal, this, credits = credits, doneLingerMillis = 0)
        advanceUntilIdle()

        assertEquals(listOf(request), credits.confirms)
        assertEquals(listOf("item-op1"), authoring.carded)
        assertTrue(q.jobs.value.isEmpty())
        assertTrue(journal.records.isEmpty())
    }

    @Test fun `an ordinary failure keeps its record and can be retried`() = runTest(dispatcher) {
        val journal = Journal()
        val credits = Credits(fail = IllegalStateException("offline"))
        val q = AtlasCaptureQueue(Authoring(), journal, this, credits = credits, doneLingerMillis = 0)
        q.enqueue(request, imageId = "img1", thumbUrl = null)
        advanceUntilIdle()

        val failed = q.jobs.value.single()
        assertTrue(failed.progress.canRetry)
        // The record stays, so an app kill here can still resume it.
        assertEquals(request, journal.records.values.single().credit)

        credits.fail = null
        q.retry(failed.id)
        advanceUntilIdle()
        assertTrue(q.jobs.value.isEmpty())
        assertTrue(journal.records.isEmpty())
    }

    @Test fun `sign-out drops every job and its record`() = runTest(dispatcher) {
        val journal = Journal()
        val q = AtlasCaptureQueue(Authoring(), journal, this, credits = Credits(fail = IllegalStateException("offline")))
        q.enqueue(request, imageId = "img1", thumbUrl = null)
        advanceUntilIdle()
        q.reset()

        // A journalled job survives app kills; left behind it would resume under the next account.
        assertTrue(q.jobs.value.isEmpty())
        assertTrue(journal.records.isEmpty())
    }

    @Test fun `a record from the pre-credit flow does not decode`() {
        // Exactly what a shipped build journalled for the retired flow. The
        // file journal deletes what it cannot read, so this is cleared away
        // rather than resumed into a confirm the server refuses.
        val old = """{"id":"j1","imageId":"img1","lemma":"cat","itemId":"item-1",
            "payload":{"primaryLabel":"cat","lemma":"cat","displayZhHant":"貓"}}"""
        val json = Json { ignoreUnknownKeys = true }
        assertFalse(runCatching { json.decodeFromString<CaptureJobRecord>(old) }.isSuccess)
        val current = CaptureJobRecord("j2", "img2", "kettle", null, request)
        assertEquals(current, json.decodeFromString<CaptureJobRecord>(json.encodeToString(CaptureJobRecord.serializer(), current)))
    }
}
