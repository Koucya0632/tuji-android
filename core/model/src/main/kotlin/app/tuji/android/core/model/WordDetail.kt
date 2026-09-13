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
    /** CEFR band for English entries; absent for Japanese. */
    val cefrLevel: String? = null,
    /** 詞形 — 單數/複數/過去式…, each a label and a value. */
    val forms: List<WordForm> = emptyList(),
    /** 來源. */
    val etymology: String? = null,
    /** 搭配, in the language being learned. */
    val collocations: List<String> = emptyList(),
    /** The zh-Hant gloss for each of [collocations], by position; often absent. */
    val collocationsZh: List<String>? = null,
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
    /** A 物見 publisher's own note. Only public items carry one. */
    val note: String? = null,
) : Headworded {
    override val headwordPronunciation: String? get() = pronunciation
}

/** One inflected form: a grammar label (in zh-Hant, from the model) and the spelling. */
@Serializable
data class WordForm(val label: String, val value: String)

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
