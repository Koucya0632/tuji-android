package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * One catalogue entry, in full.
 *
 * A separate type from [Word] rather than a superset of it: the list route
 * returns ten fields for 557 rows and this returns twenty for one, and a model
 * with fourteen optional fields would leave every reader asking which route
 * filled it in.
 */
@Serializable
data class WordDetail(
    val id: String,
    override val word: String,
    val chinese: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val pronunciation: String? = null,
    override val reading: String? = null,
    override val readingSegments: List<FuriganaSegment>? = null,
    override val targetLanguage: TargetLanguage? = null,
    /** Pre-generated pronunciation clips, keyed by locale. */
    val audioUrls: Map<String, String>? = null,
    /** 釋義 in the UI language. */
    val chineseDefinition: String? = null,
    /** 釋義 in the language being learned. */
    val targetDefinition: String? = null,
    val partOfSpeech: String? = null,
    val examples: List<WordExample> = emptyList(),
    /**
     * Catalogue ids this word points at. [relations] carries the same ids with
     * their kind; this is the flat list the server has always sent, kept
     * because it is what older payloads have.
     */
    val relatedWords: List<String> = emptyList(),
    val relations: List<WordRelation> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String? = null,
) : Headworded {
    override val headwordPronunciation: String? get() = pronunciation
}

/**
 * One authored example sentence.
 *
 * Not the same shape as the study queue's `StudyExample`: this route carries
 * the sentence in every language at once ([zh], [en], [target]) because the
 * detail screen shows the pair, while the queue sends only the one being asked
 * about. Naming them the same thing would invite a shared decoder that is
 * wrong for one of them.
 */
@Serializable
data class WordExample(
    /** The sentence in the language being learned. */
    val target: String? = null,
    val zh: String? = null,
    val en: String? = null,
    val cefrLevel: String? = null,
    val sortOrder: Int? = null,
)

@Serializable
data class WordRelation(
    val wordId: String,
    /** `see-also` and friends. Unmapped: nothing branches on it yet. */
    val type: String? = null,
)
