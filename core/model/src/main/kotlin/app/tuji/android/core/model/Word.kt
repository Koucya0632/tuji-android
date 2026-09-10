package app.tuji.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One catalogue word, as `/api/words` and `/api/words/{id}` send it.
 *
 * Field names match the wire exactly. The backend already emits camelCase
 * (Next.js helpers convert from snake_case DB columns before serializing), so
 * there is no naming strategy to configure and no place for one to drift.
 */
@Serializable
data class Word(
    val id: String,
    /** The headword in the target language. 手おけ, "bath ladle". */
    val word: String,
    /** The zh gloss. Named for the column, not the language shown. */
    val chinese: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    /** IPA for English; the kana reading for Japanese. */
    val pronunciation: String? = null,
    val targetLanguage: TargetLanguage? = null,
    /** Kana for the whole headword. A JA-only field. */
    val reading: String? = null,
    /**
     * Which kana belong to which characters. Server-side dictionary fact
     * (ADR-0006); **null is normal** and means "no trustworthy split", not an
     * error — the client falls back to the reading line.
     */
    val readingSegments: List<FuriganaSegment>? = null,
    val audioUrls: Map<String, String>? = null,
)

@Serializable
data class WordsListResponse(
    val words: List<Word>,
    val total: Int? = null,
)

/**
 * What `/api/search` sends back.
 *
 * The rows are the same [Word] the catalogue is made of — the route resolves
 * ids and then hands them to the very same builder `/api/words` uses — so there
 * is no second word type to keep in step, and a search result opens the detail
 * screen with nothing to convert.
 *
 * `results` defaults to empty because a query that matched nothing is a normal
 * answer, not a missing field.
 */
/**
 * 書籤, as ids.
 *
 * Ids and not words: a bookmark is a mark *on* a dictionary entry, and the
 * entry itself is already on this device. Sending the rows would be a second
 * copy of words the client is holding, free to drift from them.
 *
 * The list spans both decks — a bookmark is not re-made when you switch to
 * 中文→英文 — so a reader has to expect ids its current catalogue does not
 * contain. See `CardsSourceRules`.
 */
@Serializable
data class FavoritesResponse(val favorites: List<String> = emptyList())

@Serializable
data class SearchResponse(
    val results: List<Word> = emptyList(),
    val query: String = "",
)

/**
 * A run of the headword and the kana that read it.
 *
 * A **range**, not a character: 熟字訓 have no per-character reading — 時計 is
 * とけい as a unit — and JmdictFurigana itself emits such blocks, so a
 * per-character format could not represent its own source (ADR-0006).
 */
@Serializable
data class FuriganaSegment(
    val text: String,
    val ruby: String? = null,
)

/**
 * The language a word teaches — the "target" half of a learning direction.
 * Wire strings are the enum names, which is why they are lowercase.
 */
@Serializable
enum class TargetLanguage {
    @SerialName("en")
    EN,

    @SerialName("ja")
    JA,
}

/**
 * A learning direction, as `user_settings.learningDirection` and the `learning`
 * query parameter spell it.
 *
 * The wire value is `zh-ja`, not `ja` — passing the target language alone is a
 * silent 200 that returns the *English* catalogue, which is worse than an error
 * because it looks like data.
 */
@Serializable
enum class LearningDirection(val wire: String) {
    @SerialName("zh-en")
    ZH_EN("zh-en"),

    @SerialName("zh-ja")
    ZH_JA("zh-ja");

    val targetLanguage: TargetLanguage
        get() = if (this == ZH_JA) TargetLanguage.JA else TargetLanguage.EN
}

/**
 * What a word's picture looks like, which decides whether two of them can
 * stand side by side.
 *
 * The dictionary's own artwork is a cut-out on white; a captured or saved word
 * is a photograph of a room. Offer one of each and the odd one out is visible
 * without listening to anything — so 聽句's image pair draws only within a
 * kind. It also keeps 自製圖鑑 out of the draw for free.
 *
 * Derived from `category` rather than stored, because `Word`, `StudyQueueWord`
 * and the catalogue all carry that same string: one rule, so a fourth model
 * asking the question gets the answer without a fourth copy of the test.
 */
enum class WordImageKind {
    /** White-backdrop product shot. Blends into the paper. */
    Cutout,

    /** A real photograph. Rendered as-is. */
    Photograph;

    companion object {
        fun of(category: String?): WordImageKind = when (category) {
            "custom", "community" -> Photograph
            else -> Cutout
        }
    }
}
