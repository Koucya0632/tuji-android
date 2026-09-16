package app.tuji.android.capture

import app.tuji.android.core.model.AtlasCard
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.AtlasRecognitionResponse
import app.tuji.android.core.model.AtlasUploadResponse
import app.tuji.android.core.model.CaptureFailure
import app.tuji.android.core.model.CaptureJobRecord
import app.tuji.android.core.model.CaptureProgress
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasAuthoring
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 生成佇列, which is the part of 拍照做卡 nobody watches — so everything that can
 * go wrong in it goes wrong out of sight. That is the reason the whole thing
 * takes its collaborators through the constructor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AtlasCaptureQueueTest {

    private val dispatcher = StandardTestDispatcher()

    private class Authoring(
        val failConfirm: Throwable? = null,
        val failCards: Throwable? = null,
    ) : AtlasAuthoring {
        var confirms = 0
        var cardCalls = 0

        override suspend fun uploadImage(
            bytes: ByteArray, filename: String, mimeType: String, targetLanguage: TargetLanguage?,
        ): AtlasUploadResponse = error("not used")

        override suspend fun recognize(imageId: String, mode: RecognitionMode): AtlasRecognitionResponse =
            error("not used")

        override suspend fun confirm(imageId: String, payload: AtlasConfirmPayload): AtlasItem {
            confirms++
            failConfirm?.let { throw it }
            return AtlasItem(id = "item-$imageId", lemma = payload.lemma)
        }

        override suspend fun createCards(itemId: String, cardTypes: List<String>): List<AtlasCard> {
            cardCalls++
            failCards?.let { throw it }
            return cardTypes.map { AtlasCard(id = "$itemId-$it", cardType = it) }
        }

        override suspend fun publish(itemId: String): AtlasPublishResult = error("not used")
    }

    /** In memory, so a test is not a file system. */
    private class Journal(initial: List<CaptureJobRecord> = emptyList()) : CaptureJobJournal {
        val records = initial.associateBy { it.id }.toMutableMap()
        var removals = 0
        override fun save(record: CaptureJobRecord) { records[record.id] = record }
        override fun remove(id: String) { if (records.remove(id) != null) removals++ }
        override fun removeAll() { records.clear() }
        override fun restore(): List<CaptureJobRecord> = records.values.toList()
    }

    private fun payload(lemma: String = "やかん") = AtlasConfirmPayload(
        primaryLabel = lemma,
        lemma = lemma,
        displayZhHant = "水壺",
    )

    private fun queue(
        authoring: AtlasAuthoring = Authoring(),
        journal: CaptureJobJournal = Journal(),
        scope: TestScope,
        onCompleted: suspend () -> Unit = {},
    ) = AtlasCaptureQueue(
        authoring = authoring,
        journal = journal,
        scope = scope,
        onCompleted = onCompleted,
        doneLingerMillis = 4_000L,
    )

    @Test fun `a queued capture confirms, cards and lands`() = runTest(dispatcher) {
        val authoring = Authoring()
        var refreshed = 0
        val q = queue(authoring, scope = this, onCompleted = { refreshed++ })
        q.enqueue("img1", payload(), thumbUrl = "u")
        advanceUntilIdle()

        assertEquals(1, authoring.confirms)
        assertEquals(1, authoring.cardCalls)
        assertEquals("the shelf it landed on is reloaded once", 1, refreshed)
        // 已加入圖鑑 lingers, then the real card replaces the tile.
        assertEquals(emptyList<AtlasCaptureQueue.Item>(), q.jobs.value)
    }

    @Test fun `the tile says 已加入圖鑑 before it goes`() = runTest(dispatcher) {
        val q = AtlasCaptureQueue(
            authoring = Authoring(), journal = Journal(), scope = this,
            doneLingerMillis = 10_000L,
        )
        q.enqueue("img1", payload(), thumbUrl = null)
        // Far enough in for the two calls (which have no delays of their own)
        // and not far enough for the linger to expire.
        advanceTimeBy(5_000L)
        assertEquals(CaptureProgress.Ready, q.jobs.value.single().progress)
    }

    @Test fun `a resumed job never confirms twice`() = runTest(dispatcher) {
        // The window this closes: confirm succeeded, then Android reclaimed the
        // process. Restarting from the top would make the word a second time.
        val authoring = Authoring()
        val journal = Journal(
            listOf(
                CaptureJobRecord(
                    id = "j1", imageId = "img1", payload = payload(),
                    lemma = "やかん", itemId = "item-img1",
                ),
            ),
        )
        val q = queue(authoring, journal, scope = this)
        advanceUntilIdle()

        assertEquals("confirm is skipped entirely", 0, authoring.confirms)
        assertEquals("the idempotent half still runs", 1, authoring.cardCalls)
        assertEquals(emptyList<AtlasCaptureQueue.Item>(), q.jobs.value)
    }

    @Test fun `a resumed job starts its bar where the work actually is`() = runTest(dispatcher) {
        // Walking back to 15% for a card the server already has is a lie the
        // user can see.
        assertEquals(0.5f, CaptureProgress.startingFraction(resuming = true))
        assertEquals(0.15f, CaptureProgress.startingFraction(resuming = false))
    }

    @Test fun `a failed confirm keeps the job so it can be retried`() = runTest(dispatcher) {
        val authoring = Authoring(failConfirm = IOException("down"))
        val journal = Journal()
        val q = queue(authoring, journal, scope = this)
        q.enqueue("img1", payload(), thumbUrl = null)
        advanceUntilIdle()

        val job = q.jobs.value.single()
        assertTrue(job.progress.isFailed)
        assertTrue("a timeout is worth another go", job.progress.canRetry)
        assertEquals("the record survives an app kill", 1, journal.records.size)
    }

    @Test fun `retrying a transient failure runs it again`() = runTest(dispatcher) {
        val authoring = Authoring(failConfirm = IOException("down"))
        val q = queue(authoring, scope = this)
        q.enqueue("img1", payload(), thumbUrl = null)
        advanceUntilIdle()
        q.retry(q.jobs.value.single().id)
        advanceUntilIdle()
        assertEquals(2, authoring.confirms)
    }

    @Test fun `a capacity failure is a dead end, not a retry`() = runTest(dispatcher) {
        // 402 is the server saying the atlas is full. Offering 重試 for it is
        // offering the impossible — and it used to, because every failure was
        // flattened into one untyped kind.
        val authoring = Authoring(failConfirm = ApiError.Http(402, QUOTA_BODY))
        val q = queue(authoring, scope = this)
        q.enqueue("img1", payload(), thumbUrl = null)
        advanceUntilIdle()

        val job = q.jobs.value.single()
        assertFalse(job.progress.canRetry)
        // The sentence, not the JSON it arrived in.
        assertEquals(
            CaptureFailure.AtCapacity("自製圖鑑已達免費上限（3），升級 Pro 可擴充到 300 格。"),
            (job.progress as CaptureProgress.Failed).failure,
        )
        assertNull("retry refuses", q.retry(job.id))
        assertEquals(1, authoring.confirms)
    }

    @Test fun `a queued job counts against capacity before the server knows`() = runTest(dispatcher) {
        // It has already claimed a slot. A gate that ignores it lets a second
        // capture through at capacity − 1, and that one dies as a dead tile.
        val q = queue(Authoring(failCards = IOException("down")), scope = this)
        q.enqueue("img1", payload(), thumbUrl = null)
        assertEquals(1, q.inFlightCount)
        advanceUntilIdle()
        assertEquals("a failed job is not in flight", 0, q.inFlightCount)
    }

    @Test fun `signing out drops every job, on disk as well as in memory`() = runTest(dispatcher) {
        // A journalled job outlives the process and would otherwise resume
        // under the next account — and surface the last account's photograph.
        val journal = Journal()
        val q = queue(Authoring(failConfirm = IOException("down")), journal, scope = this)
        q.enqueue("img1", payload(), thumbUrl = null)
        advanceUntilIdle()
        assertEquals(1, journal.records.size)

        q.reset()
        assertEquals(emptyList<AtlasCaptureQueue.Item>(), q.jobs.value)
        assertEquals(0, journal.records.size)
    }

    @Test fun `a landed job is dropped from the journal before the tile goes`() = runTest(dispatcher) {
        // Otherwise a kill during the four-second linger resurrects finished
        // work and runs createCards over a card that already exists.
        val journal = Journal()
        val q = AtlasCaptureQueue(
            authoring = Authoring(), journal = journal, scope = this,
            doneLingerMillis = 10_000L,
        )
        q.enqueue("img1", payload(), thumbUrl = null)
        advanceTimeBy(5_000L)
        assertEquals(CaptureProgress.Ready, q.jobs.value.single().progress)
        assertEquals("nothing left to resume", 0, journal.records.size)
    }

    @Test fun `only an http 402 is a capacity failure`() {
        assertEquals(CaptureFailure.Transient, captureFailure(ApiError.Http(500, "boom")))
        assertEquals(CaptureFailure.Transient, captureFailure(IOException("offline")))
        assertTrue(captureFailure(ApiError.Http(402, QUOTA_BODY)) is CaptureFailure.AtCapacity)
    }

    @Test fun `a capacity tile shows the sentence, never the response body`() {
        // This shipped as the raw body once, so a tile in the 圖鑑 grid read
        // `{"error":"quota_exceeded","scope":"capacity","message":"…"}` — the
        // one field written for a person was the one not being read.
        assertEquals(
            CaptureFailure.AtCapacity("自製圖鑑已達免費上限（3），升級 Pro 可擴充到 300 格。"),
            captureFailure(ApiError.Http(402, QUOTA_BODY)),
        )
        // Anything that is not a message falls back to this app's own wording.
        assertEquals(CaptureFailure.AtCapacity(null), captureFailure(ApiError.Http(402, "Payment Required")))
        assertEquals(CaptureFailure.AtCapacity(null), captureFailure(ApiError.Http(402, """{"error":"quota_exceeded"}""")))
        assertEquals(CaptureFailure.AtCapacity(null), captureFailure(ApiError.Http(402, null)))
    }

    private companion object {
        /** Exactly what the server sent on 2026-09-17. */
        const val QUOTA_BODY =
            """{"error":"quota_exceeded","scope":"capacity","message":"自製圖鑑已達免費上限（3），升級 Pro 可擴充到 300 格。"}"""
    }
}
