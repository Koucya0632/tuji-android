package app.tuji.android.atlas

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.MemberAccess
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.core.model.WordInsights
import app.tuji.android.core.network.WordInsightsReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 詞條延伸內容, fetched per word and kept for the session — iOS's
 * `WordInsightsStore`.
 *
 * The answer depends on the word, the interface language, the learning
 * direction and the account's tier, so all four are in the key: buying a
 * membership shows the unlocked text on the next open without a relaunch.
 *
 * Under membership policy v1 it never asks — [MemberAccess] says the feature is
 * hidden, so v1 costs no request at all.
 */
class WordInsightsStore(private val remote: WordInsightsReading) : AccountScopedStore {

    /**
     * Key → answer. A key that is present was asked and answered; a null value
     * means the word has none. A key that is absent has not been asked yet, or
     * failed and will be asked again next time.
     */
    private val _answers = MutableStateFlow<Map<String, WordInsights?>>(emptyMap())
    val answers: StateFlow<Map<String, WordInsights?>> = _answers.asStateFlow()

    suspend fun load(wordId: String, direction: LearningDirection, uiLang: String, entitlement: Entitlement?) {
        if (!isEligible(wordId)) return
        if (MemberAccess.level(MemberFeature.WordInsights, entitlement) == MemberAccessLevel.Hidden) return
        val key = key(wordId, direction, uiLang, entitlement)
        if (key in _answers.value) return
        try {
            val response = remote.insights(wordId, uiLang, direction)
            _answers.update { it + (key to response.insights) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed read shows nothing and is asked again next time.
            Log.w(TAG, "insights load failed: $wordId", e)
        }
    }

    /** The account changed: its tier, and so every answer, may differ. */
    override fun reset() {
        _answers.value = emptyMap()
    }

    companion object {
        private const val TAG = "TujiInsights"

        /** Official words only; 自製 and 物見 ids have no insights. */
        fun isEligible(wordId: String): Boolean =
            !CardsSourceRules.isCustom(wordId) && !CardsSourceRules.isSaved(wordId)

        fun key(wordId: String, direction: LearningDirection, uiLang: String, entitlement: Entitlement?): String =
            "${direction.wire}|$uiLang|${entitlement?.membershipTier?.wire}|$wordId"
    }
}
