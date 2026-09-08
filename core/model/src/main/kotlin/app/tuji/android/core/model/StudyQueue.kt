package app.tuji.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** Which side of the study flow the user is entering. */
enum class StudyMode(val wire: String) {
    New("new"),
    Review("review"),
}

/**
 * Which question 複習 is asking about the card in front of the user.
 *
 * The case names deliberately do **not** match the wire values. `Mcq` names the
 * *form* of the question (four options) while `listening` names the *sensory
 * channel*; one enum whose cases are named on two different axes leaves the
 * next person adding a third with no way to know which axis to pick. Both cases
 * here are named for what the user does, and [activity] keeps the wire
 * vocabulary.
 */
enum class ReviewQuestionKind(val activity: String) {
    /** A picture, and four word labels to choose between. */
    PickWord("mcq"),

    /** The word's example sentence blurred, its audio, and two pictures. */
    HearSentence("listening"),
}

/**
 * A card id that arrives as either a JSON number or a JSON string.
 *
 * Public cards carry an integer id; 自製圖鑑 cards carry a uuid. Swift handles
 * this with a hand-written `init(from:)`; kotlinx needs the same tolerance
 * spelled as a serializer, or half the queue fails to decode and the screen
 * shows 資料解析失敗 for a difference that means nothing to the app.
 */
private object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content
            ?: decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
data class StudyCard(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val cardType: String? = null,
    val deckKey: String? = null,
)

@Serializable
data class StudyQueueWord(
    val id: String,
    override val word: String,
    val chinese: String,
    val imageUrl: String,
    val pronunciation: String,
    override val reading: String? = null,
    override val readingSegments: List<FuriganaSegment>? = null,
    override val targetLanguage: TargetLanguage? = null,
    val category: String,
    /**
     * The 釋義 — the explanatory sentence the detail page prints, in the
     * reader's own language. 複習's 求救提示 turns the picture over to this
     * rather than to [chinese], which for a zh reader is the answer translated
     * (水桶) rather than a hint.
     *
     * Optional twice over: the catalogue does not have one for every word, and
     * the server deliberately withholds it when it would only repeat the gloss.
     * So null is the ordinary state, not a decoding failure.
     */
    val definition: String? = null,
) : Headworded {
    override val headwordPronunciation: String? get() = pronunciation
}

@Serializable
data class StudyExample(
    /** The sentence in the language being learned. Rendered blurred. */
    val sentence: String,
    /** `A2` (the simpler of the authored pair) or `B1` (the harder). */
    val cefrLevel: String? = null,
    /**
     * Pre-generated clips keyed by locale. Null when the sentence has no
     * recording yet — such a card simply is not asked as 聽句, because the
     * on-device fallback would be reading kanji by a guess the app cannot
     * correct.
     */
    val audioUrls: Map<String, String>? = null,
)

@Serializable
data class StudyQueueItem(
    val card: StudyCard,
    val word: StudyQueueWord,
    val choices: List<String>? = null,
    val spellingChoices: List<String>? = null,
    val mastery: Int? = null,
    /**
     * The word's authored example pair. Absent for 自製圖鑑 and 物見 cards,
     * which have no example sentences at all — the reason 聽句 needs a fallback
     * question rather than a gate at the session's entrance.
     */
    val examples: List<StudyExample>? = null,
) {
    val id: String get() = word.id
}

@Serializable
data class StudyQueueResponse(
    val queue: List<StudyQueueItem>,
    val stats: StudyStats? = null,
)

@Serializable
data class StudyStats(
    val total: Int,
    val seen: Int,
    val due: Int,
    val new: Int,
    val todayNew: Int? = null,
)
