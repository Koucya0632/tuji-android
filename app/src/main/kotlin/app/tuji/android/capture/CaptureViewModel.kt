package app.tuji.android.capture

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.CaptureDraft
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.AtlasImageSummary
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
    /** Where a confirmed capture goes. See [confirm]. */
    private val enqueue: (imageId: String, payload: AtlasConfirmPayload, thumbUrl: String?) -> Unit =
        { _, _, _ -> },
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
            /** Null when the form is the user's to use. */
            val busy: Work? = null,
        ) : Step

        /**
         * The two waits this screen can be in, kept apart because they are two
         * different sentences. Saying 「AI 識別中」 while the app is making
         * cards is a description of the wrong thing — the picture was
         * recognised a moment ago, and the user is watching the app do
         * something it can name.
         */
        enum class Work { Recognizing, Creating }

        /**
         * Handed to 生成佇列. The screen's work is over.
         *
         * There is no 已完成 step any more, because at this point the card does
         * not exist yet — `confirm` and `createCards` run in the queue, after
         * this screen is gone. iOS has never had one either: 確認並生成卡片
         * enqueues and dismisses in the same breath.
         */
        data object Queued : Step

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
        _step.value = now.copy(busy = Step.Work.Recognizing)
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
     * Hand the capture to 生成佇列 and get out of the way.
     *
     * Everything the queue needs is already decided: the photograph is
     * uploaded, the names are typed. What is left — `confirm`, then the cards
     * that make the word reviewable — is two API calls nobody has to watch, so
     * the screen closes and the 圖鑑 grid draws them as a 生成中 tile.
     *
     * [enqueue] rather than a queue instance, so this can still be walked in a
     * test with no journal and no file system.
     */
    fun confirm() {
        val now = _step.value as? Step.Naming ?: return
        if (!now.draft.isComplete || now.busy != null) return
        enqueue(now.image.id, now.draft.payload(), now.image.thumbUrl ?: now.image.imageUrl)
        _step.value = Step.Queued
    }

    fun reset() {
        _step.value = Step.Framing
    }

    private companion object {
        const val TAG = "TujiCapture"
    }
}
