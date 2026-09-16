package app.tuji.android.capture

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.CaptureFailure
import app.tuji.android.core.model.CaptureJobRecord
import app.tuji.android.core.model.CaptureProgress
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.AtlasAuthoring
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 生成佇列 — the durable tail of the 自製圖鑑 capture flow.
 *
 * Once the user confirms a name, `confirm` → `createCards` → one reconciling
 * read runs **here** instead of blocking the screen. Before this the capture
 * page stood still waiting for two API calls with nothing to do; iOS has never
 * made anyone wait for them, and the 圖鑑 grid draws the work in progress as
 * 生成中 tiles at its head.
 *
 * Owned by [app.tuji.android.TujiApplication] rather than a `ViewModel`, so the
 * jobs keep running after the capture screen is gone — which is the whole point
 * of handing the work here.
 *
 * **Weak-network resilience.** Jobs are journalled, so an app kill mid-flight
 * does not lose committed work: on launch the queue restores and resumes them.
 * `confirm` is a plain INSERT server-side and is not idempotent, so once it
 * succeeds the itemId is checkpointed and a resumed run starts from
 * `createCards`, which is.
 *
 * Everything it reaches arrives through a constructor parameter, so the
 * checkpoint rule above — the only thing standing between a resumed run and a
 * duplicate card — is something a test can actually exercise.
 */
class AtlasCaptureQueue(
    private val authoring: AtlasAuthoring,
    private val journal: CaptureJobJournal,
    private val scope: CoroutineScope,
    /** What a finished capture refreshes. Not this queue's decision. */
    private val onCompleted: suspend () -> Unit = {},
    /**
     * How long a finished tile stays on the grid saying 已加入圖鑑 before the
     * real card replaces it. A parameter so a test is not a four-second wait.
     */
    private val doneLingerMillis: Long = 4_000L,
    private val cardTypes: List<String> = DEFAULT_CARDS,
) : AccountScopedStore {

    /** One capture on its way in. */
    data class Item(
        val id: String,
        val imageId: String,
        val lemma: String,
        val thumbUrl: String?,
        val progress: CaptureProgress,
        internal val payload: AtlasConfirmPayload,
        internal val itemId: String?,
    ) {
        internal val record: CaptureJobRecord
            get() = CaptureJobRecord(id, imageId, payload, lemma, thumbUrl, itemId)
    }

    private val _jobs = MutableStateFlow<List<Item>>(emptyList())
    val jobs: StateFlow<List<Item>> = _jobs.asStateFlow()

    /**
     * One event per capture that lands, so the window can buzz for it.
     *
     * A flow rather than a callback the queue calls: haptics belong to a view,
     * and a queue that owns a `TujiHaptics` is a queue that cannot be built in
     * a test without one.
     */
    private val _celebrations = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val celebrations: SharedFlow<String> = _celebrations.asSharedFlow()

    /**
     * Captures committed but not yet counted by the server's usage snapshot.
     *
     * A queued job has already claimed a 自製圖鑑 slot; a gate that ignores them
     * lets a second capture through at capacity − 1, and it dies as a tile that
     * can only be dismissed.
     */
    val inFlightCount: Int
        get() = _jobs.value.count { !it.progress.isFailed && it.progress != CaptureProgress.Ready }

    private val running = mutableMapOf<String, Job>()

    init {
        restore()
    }

    /**
     * Hand a confirmed capture to the queue. Returns at once — that is the
     * point — and the returned [Job] exists so a test can await what production
     * deliberately does not.
     */
    fun enqueue(imageId: String, payload: AtlasConfirmPayload, thumbUrl: String?): Job {
        val record = CaptureJobRecord(
            id = UUID.randomUUID().toString(),
            imageId = imageId,
            payload = payload,
            lemma = payload.lemma,
            thumbUrl = thumbUrl,
        )
        _jobs.value = _jobs.value + item(record)
        journal.save(record)
        return start(record.id)
    }

    /**
     * Only a transient failure can be retried — see [CaptureProgress.canRetry].
     * A capture that died at capacity would die the same way every time.
     */
    fun retry(id: String): Job? {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return null
        if (!job.progress.canRetry) return null
        update(id) { it.copy(progress = CaptureProgress.Generating(CaptureProgress.startingFraction(it.itemId != null))) }
        return start(id)
    }

    fun remove(id: String) {
        _jobs.value = _jobs.value.filterNot { it.id == id }
        journal.remove(id)
    }

    /**
     * Drop every job, in memory and on disk.
     *
     * Sign-out. A journalled job survives app kills and would otherwise resume
     * under the next account's session — and surface the previous account's
     * photograph in their 圖鑑. In-flight requests fail with 401 once the
     * session is gone, and their updates no-op because the job is already gone.
     */
    override fun reset() {
        running.values.forEach { it.cancel() }
        running.clear()
        _jobs.value = emptyList()
        journal.removeAll()
    }

    /** Await everything in flight. Nothing in production calls this. */
    suspend fun settle() {
        while (true) {
            val pending = running.values.toList().ifEmpty { return }
            pending.joinAll()
        }
    }

    private fun item(record: CaptureJobRecord) = Item(
        id = record.id,
        imageId = record.imageId,
        lemma = record.lemma,
        thumbUrl = record.thumbUrl,
        progress = CaptureProgress.Generating(
            CaptureProgress.startingFraction(resuming = record.itemId != null),
        ),
        payload = record.payload,
        itemId = record.itemId,
    )

    private fun start(id: String): Job {
        val job = scope.launch {
            try {
                run(id)
            } finally {
                running.remove(id)
            }
        }
        running[id] = job
        return job
    }

    private suspend fun run(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        try {
            val itemId = job.itemId ?: run {
                val created = authoring.confirm(job.imageId, job.payload)
                update(id) { it.copy(itemId = created.id) }
                // Checkpoint **before** the idempotent tail, never after: the
                // window this closes is exactly confirm-succeeded-then-killed.
                checkpoint(id)
                created.id
            }
            update(id) { it.copy(progress = CaptureProgress.Generating(0.5f)) }
            authoring.createCards(itemId, cardTypes)
            update(id) { it.copy(progress = CaptureProgress.Enriching(0.9f)) }
            onCompleted()
            update(id) { it.copy(progress = CaptureProgress.Ready) }
            _celebrations.tryEmit(job.lemma)
            journal.remove(id)
            delay(doneLingerMillis)
            remove(id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.e(TAG, "capture job failed", error)
            update(id) { it.copy(progress = CaptureProgress.Failed(captureFailure(error))) }
            // The journalled record stays, so the job survives an app kill and
            // can be retried — from the checkpoint if confirm already ran.
        }
    }

    private fun update(id: String, mutate: (Item) -> Item) {
        _jobs.value = _jobs.value.map { if (it.id == id) mutate(it) else it }
    }

    /** Re-journal a record that changed — in practice only the checkpoint. */
    private fun checkpoint(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        journal.save(job.record)
    }

    private fun restore() {
        journal.restore().forEach { record ->
            _jobs.value = _jobs.value + item(record)
            start(record.id)
        }
    }

    private companion object {
        const val TAG = "AtlasCaptureQueue"

        /**
         * **One card, not two.**
         *
         * A 自製圖鑑 word studies as a single card: the unified study flow
         * renders every custom card as an image MCQ and dedupes the queue to
         * one per item, so a second `card_type` is pure overhead — extra SRS
         * state, doubled due counts, wasted signed-URL work.
         *
         * The server collapses any request to one anyway (preferring
         * `image_recall`, which is why that is the one asked for). Sending two
         * and receiving one is how a "2 張卡" that is really 1 gets shown to a
         * user — this client asked for both until a device run printed
         * 「1 張卡」 and the discrepancy was chased down.
         */
        val DEFAULT_CARDS = listOf("image_recall")
    }
}

/**
 * Which kind of failure this is.
 *
 * The server answers a spent quota with **402**, and the capture flow already
 * routes that to the paywall during 識別. After the work is queued it would
 * otherwise flatten into one untyped failure, and a dead end would wear a
 * retry's costume.
 */
internal fun captureFailure(error: Throwable): CaptureFailure {
    val http = error as? ApiError.Http ?: return CaptureFailure.Transient
    return if (http.status == 402) CaptureFailure.AtCapacity(serverMessage(http.body)) else CaptureFailure.Transient
}

/**
 * The sentence the server wrote, or nothing.
 *
 * **Never the body.** A 402 body is
 * `{"error":"quota_exceeded","scope":"capacity","message":"自製圖鑑已達免費上限（3）…"}`,
 * and passing it through put that whole string on a tile in the 圖鑑 grid — the
 * one field in it meant for a person was the one not being read. Anything that
 * is not a JSON object with a non-blank `message` falls back to this app's own
 * wording, because a machine code on a tile is worse than a generic sentence.
 */
private fun serverMessage(body: String?): String? = runCatching {
    Json.parseToJsonElement(body ?: return null)
        .jsonObject["message"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}.getOrNull()
