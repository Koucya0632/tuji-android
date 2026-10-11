package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * Why a capture stopped.
 *
 * Two kinds, because only one of them is worth offering 重試 for. A capture that
 * died at capacity would die the same way every time, and a button that cannot
 * work is worse than no button.
 */
sealed interface CaptureFailure {
    /** 自製圖鑑 is full. [message] is the server's own wording when it sent one. */
    data class AtCapacity(val message: String?) : CaptureFailure

    /** A timeout, a 500, a dropped connection. Worth another go. */
    data object Transient : CaptureFailure

    val isRetryable: Boolean get() = this is Transient
}

/**
 * Where a capture is, in the vocabulary every screen that draws one reads.
 *
 * The fraction is **real work completed**, not a sweep: confirm lands at 0.5,
 * the cards at 0.7, the detail at 0.9. That is the one case in this app where a
 * determinate bar is honest, because the steps are counted and known.
 */
sealed interface CaptureProgress {
    /** confirm → createCards. */
    data class Generating(val done: Float) : CaptureProgress

    /** The card exists; its detail page is being filled in. */
    data class Enriching(val done: Float) : CaptureProgress

    data object Ready : CaptureProgress

    data class Failed(val failure: CaptureFailure) : CaptureProgress

    /** The determinate fraction, or null where there is nothing honest to show. */
    val fraction: Float?
        get() = when (this) {
            is Generating -> done
            is Enriching -> done
            Ready -> 1f
            is Failed -> null
        }

    val isFailed: Boolean get() = this is Failed

    /** Only a transient failure offers 重試. */
    val canRetry: Boolean get() = (this as? Failed)?.failure?.isRetryable == true

    companion object {
        /** Where a job's bar starts. */
        const val STARTING_FRACTION = 0.15f
    }
}

/**
 * One queued capture, as it survives an app kill.
 *
 * Every step of the job is safe to repeat — the confirm is bound to its 識別操作
 * server-side and answers a resend with the card it already made — so a
 * restored record is simply run again from the top. A record journalled by the
 * retired pre-credit flow has no [credit]; it does not decode, and the journal
 * drops it.
 */
@Serializable
data class CaptureJobRecord(
    val id: String,
    val imageId: String,
    val lemma: String,
    /**
     * The uploaded picture, for the tile to draw.
     *
     * A URL rather than the bytes: the photograph is already on the server
     * before a name is typed, and the naming screen has just drawn it, so it is
     * in the image cache. iOS keeps the local `UIImage` instead — it can, and
     * carrying a megabyte per job through a journal here would be paying for
     * something the cache already has.
     */
    val thumbUrl: String? = null,
    /** What to confirm. The operation it names is what makes a resend safe. */
    val credit: CreditConfirmRequest,
)
