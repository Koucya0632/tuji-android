package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * 詞條延伸內容 — `GET /api/words/:id/insights` (tuji-web
 * `lib/word-insights-present.ts`).
 *
 * Already in the reader's interface language and learning direction, and
 * already cut to what this account may see: a non-member gets the 容易混淆
 * entries plus how many 常見誤用 / 用法補充 are locked, never their text.
 */
@Serializable
data class WordInsights(
    val confusables: List<WordInsightConfusable> = emptyList(),
    val mistakes: List<WordInsightMistake> = emptyList(),
    val usage: String? = null,
    val lockedMistakesCount: Int = 0,
    val usageLocked: Boolean = false,
) {
    val isEmpty: Boolean
        get() = confusables.isEmpty() && mistakes.isEmpty() && usage == null &&
            lockedMistakesCount == 0 && !usageLocked
}

@Serializable
data class WordInsightConfusable(
    val term: String,
    /** The catalogue word it links to, when it is one. */
    val catalogId: String? = null,
    val distinction: String,
)

@Serializable
data class WordInsightMistake(
    val wrong: String,
    val right: String,
    val why: String,
)

@Serializable
data class WordInsightsResponse(
    /** False under membership policy v1: the feature does not exist yet. */
    val available: Boolean = false,
    val insights: WordInsights? = null,
)
