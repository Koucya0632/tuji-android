package app.tuji.android.capture

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.CaptureDraft
import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.network.AtlasAuthoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 自製圖鑑 — the performing half of 拍照 → 辨識 → 更正 → 卡片.
 *
 * [CaptureDraft] holds every decision; this owns the four network calls and the
 * three ways they can be in flight. Splitting them is what lets the whole
 * user-facing path be walked in a test with no camera and no AI spend.
 */
class CaptureViewModel(
    private val authoring: AtlasAuthoring,
    private val direction: LearningDirection,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    sealed interface Step {
        /** Waiting for a photo. */
        data object Framing : Step

        /** Uploading, and running the first recognition inside the same request. */
        data object Uploading : Step

        /** Candidates are on screen; the user picks or types. */
        data class Naming(
            val image: AtlasImageSummary,
            val draft: CaptureDraft,
            val busy: Boolean = false,
        ) : Step

        /**
         * Confirmed. The item exists and its cards were made.
         *
         * [publish] is null until the user asks: putting their own photograph on
         * a public feed is a decision, not a side effect of naming it.
         */
        data class Made(
            val item: AtlasItem,
            val cards: Int,
            val publishing: Boolean = false,
            val publish: PublishOutcome? = null,
        ) : Step

        data class Failed(val message: String) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Framing)
    val step: StateFlow<Step> = _step.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    /** A photo was taken. Uploads it and shows whatever came back. */
    fun submit(bytes: ByteArray, filename: String = "capture.jpg") {
        _step.value = Step.Uploading
        work.launch {
            val response = runCatching {
                authoring.uploadImage(
                    bytes = bytes,
                    filename = filename,
                    mimeType = "image/jpeg",
                    targetLanguage = direction.targetLanguage,
                )
            }.getOrElse {
                Log.e(TAG, "upload failed", it)
                _step.value = Step.Failed(it.message ?: "upload failed")
                return@launch
            }

            val candidates = response.candidates.orEmpty()
            // Recorded even when empty: recognition soft-fails, and "it found
            // nothing" is an answer that must not be re-bought.
            var draft = CaptureDraft(targetLanguage = response.targetLanguage)
                .withCandidates(RecognitionMode.Primary, candidates)
            CaptureDraft.preselect(candidates)?.let { draft = draft.apply(it) }
            _step.value = Step.Naming(image = response.image, draft = draft)
        }
    }

    /**
     * Switch recognition depth.
     *
     * Only fetches when this mode has never answered — see [CaptureDraft]. The
     * key behind it is shared with two other features, so a user tapping
     * between the modes must not spend a call per tap.
     */
    fun setMode(mode: RecognitionMode) {
        val now = _step.value as? Step.Naming ?: return
        if (!now.draft.needsFetch(mode)) {
            _step.value = now.copy(draft = now.draft.showing(mode))
            return
        }
        _step.value = now.copy(busy = true)
        work.launch {
            val candidates = runCatching {
                authoring.recognize(now.image.id, mode).candidates
            }.getOrElse {
                Log.w(TAG, "recognize failed", it)
                emptyList()
            }
            var draft = now.draft.withCandidates(mode, candidates)
            CaptureDraft.preselect(candidates)?.let { draft = draft.apply(it) }
            _step.value = Step.Naming(image = now.image, draft = draft)
        }
    }

    fun pick(candidateId: String) {
        val now = _step.value as? Step.Naming ?: return
        val candidate = now.draft.candidates.firstOrNull { it.id == candidateId } ?: return
        _step.value = now.copy(draft = now.draft.apply(candidate))
    }

    fun edit(lemma: String = _draft()?.lemma ?: "", zhHant: String = _draft()?.displayZhHant ?: "") {
        val now = _step.value as? Step.Naming ?: return
        _step.value = now.copy(draft = now.draft.copy(lemma = lemma, displayZhHant = zhHant))
    }

    private fun _draft(): CaptureDraft? = (_step.value as? Step.Naming)?.draft

    /**
     * Confirm, then make the cards.
     *
     * Two calls, and the second is what makes the word reviewable — an item
     * without cards is a picture in a list that 複習 will never show. Reported
     * as one step because a half-made word is not something the user can act on.
     */
    fun confirm() {
        val now = _step.value as? Step.Naming ?: return
        if (!now.draft.isComplete || now.busy) return
        _step.value = now.copy(busy = true)
        work.launch {
            val item = runCatching {
                authoring.confirm(now.image.id, now.draft.payload())
            }.getOrElse {
                Log.e(TAG, "confirm failed", it)
                _step.value = now.copy(busy = false)
                return@launch
            }
            val cards = runCatching {
                authoring.createCards(item.id, DEFAULT_CARDS)
            }.getOrElse {
                Log.e(TAG, "cards failed for ${item.id}", it)
                emptyList()
            }
            _step.value = Step.Made(item = item, cards = cards.size)
        }
    }

    /** What 物見 did with it. */
    enum class PublishOutcome {
        /** Live on the feed now. */
        Published,

        /**
         * Accepted, but a human will look first.
         *
         * A distinct outcome from [Published] on purpose: telling someone their
         * word is live when it is queued is a lie the feed will contradict
         * within a minute of them going to look for it.
         */
        Queued,
        Failed,
    }

    /** Offer the finished item to 物見. */
    fun publish() {
        val now = _step.value as? Step.Made ?: return
        if (now.publishing || now.publish != null) return
        _step.value = now.copy(publishing = true)
        work.launch {
            val outcome = runCatching { authoring.publish(now.item.id) }
                .map { if (it.published) PublishOutcome.Published else PublishOutcome.Queued }
                .getOrElse {
                    Log.e(TAG, "publish failed for ${now.item.id}", it)
                    PublishOutcome.Failed
                }
            (_step.value as? Step.Made)?.let {
                _step.value = it.copy(publishing = false, publish = outcome)
            }
        }
    }

    /** Start over without leaving the screen. */
    fun reset() {
        _step.value = Step.Framing
    }

    private companion object {
        const val TAG = "TujiCapture"

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
