package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasCandidate
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.model.TargetLanguage

/**
 * What the user has decided about a photo, before it is confirmed.
 *
 * A value, so the whole 拍照 → 辨識 → 更正 → 送出 path can be walked in a test
 * without a camera, a network or an AI call. The screen holds one of these and
 * the coordinator turns the final one into a request.
 *
 * **Two of the rules here are about money.** The recognition key is shared with
 * two other features, and a zero balance breaks all three at once while looking
 * like an app bug — so a mode that has already answered is never asked twice.
 */
data class CaptureDraft(
    /** Candidates already fetched, per mode. Never re-fetched — see [needsFetch]. */
    val byMode: Map<RecognitionMode, List<AtlasCandidate>> = emptyMap(),
    val mode: RecognitionMode = RecognitionMode.Primary,
    val selectedCandidateId: String? = null,
    /** The two names the user may edit. */
    val lemma: String = "",
    val displayZhHant: String = "",
    /** The own-language name, for ja/en UIs. Unused on a Chinese UI. */
    val displayGloss: String = "",
    /** Carried from the chosen candidate; never shown, never edited. */
    val primaryLabel: String = "",
    val fineLabel: String = "",
    val partOfSpeech: String = "",
    val category: String = "",
    val targetLanguage: TargetLanguage? = null,
) {
    val candidates: List<AtlasCandidate> get() = byMode[mode].orEmpty()

    val selected: AtlasCandidate?
        get() = candidates.firstOrNull { it.id == selectedCandidateId }

    /**
     * Whether this mode still has to be asked.
     *
     * **An already-answered mode is never asked again.** Re-running barely
     * changes the answer and spends another AI call from a budget shared with
     * 補資料 and the web app; a user tapping 普通識別 / 精準識別 back and forth
     * would otherwise burn one per tap. An empty-but-present list still counts
     * as answered — recognition soft-fails, and "it found nothing" is an answer.
     */
    fun needsFetch(mode: RecognitionMode): Boolean = mode !in byMode

    /** Record what a mode returned and switch to it. */
    fun withCandidates(mode: RecognitionMode, candidates: List<AtlasCandidate>): CaptureDraft =
        copy(byMode = byMode + (mode to candidates), mode = mode)

    /** Switch modes without fetching. */
    fun showing(mode: RecognitionMode): CaptureDraft = copy(mode = mode)

    /**
     * Take a candidate's answer into the form.
     *
     * The two editable names are **overwritten**, because picking a different
     * candidate is the user saying the previous one was wrong. The hidden
     * fields ride along so `confirm` gets them without the screen showing four
     * more inputs nobody wants to fill in.
     */
    fun apply(candidate: AtlasCandidate): CaptureDraft = copy(
        selectedCandidateId = candidate.id,
        lemma = candidate.label,
        displayZhHant = candidate.zhHant.orEmpty(),
        displayGloss = candidate.gloss.orEmpty(),
        primaryLabel = if (candidate.level == AtlasCandidate.LEVEL_FINE) primaryLabel else candidate.label,
        fineLabel = if (candidate.level == AtlasCandidate.LEVEL_FINE) candidate.label else "",
    )

    /** Enough to send? The name is the one thing nothing else can supply. */
    val isComplete: Boolean get() = lemma.isNotBlank()

    /**
     * The request.
     *
     * Split from the sending so the fallbacks stay testable: **[lemma] stands in
     * for a missing [primaryLabel]** (a hand-typed word has no candidate behind
     * it, and the server requires the field), and every blank optional drops to
     * null rather than travelling as `""` — an empty string in `fine_label`
     * reads as "there is a fine label and it is nothing".
     */
    fun payload(): AtlasConfirmPayload = AtlasConfirmPayload(
        selectedCandidateId = selectedCandidateId,
        targetLanguage = targetLanguage,
        primaryLabel = primaryLabel.ifBlank { lemma },
        fineLabel = fineLabel.ifBlank { null },
        lemma = lemma,
        displayZhHant = displayZhHant,
        displayGloss = displayGloss.ifBlank { null },
        partOfSpeech = partOfSpeech.ifBlank { null },
        category = category.ifBlank { null },
    )

    companion object {
        /**
         * The candidate to start on: the highest-ranked one.
         *
         * `rank` rather than `confidence` — the server has already ordered
         * them, and re-sorting on a number it also sent would be a second
         * opinion about the same question.
         */
        fun preselect(candidates: List<AtlasCandidate>): AtlasCandidate? =
            candidates.minByOrNull { it.rank }
    }
}
