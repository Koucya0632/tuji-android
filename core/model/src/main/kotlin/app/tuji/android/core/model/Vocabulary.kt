package app.tuji.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 查詞 — iOS's `VocabularyStudying.swift`, the wire half: what is asked, what
 * the offer costs, and the three cards Moira can answer with.
 */
@Serializable
enum class VocabularyMode {
    @SerialName("lookup") Lookup,
    @SerialName("analysis") Analysis,
    @SerialName("mnemonic") Mnemonic,
}

@Serializable
data class VocabularyInput(
    val requestId: String,
    val mode: VocabularyMode,
    val query: String,
    val context: String,
    val selectedSense: String,
    val uiLanguage: String,
    val learningDirection: String,
    val pricingVersion: String,
    val maxPoints: Int,
)

@Serializable
data class VocabularyOffer(
    val enabled: Boolean,
    /** Why generation is closed. Too few points is not a reason here — the server says so on a send. */
    val reason: Reason? = null,
    val available: Int = 0,
    val pricingVersion: String,
    val modes: List<Price> = emptyList(),
) {
    @Serializable
    data class Price(val mode: VocabularyMode, val minPoints: Int, val maxPoints: Int)

    @Serializable
    enum class Reason {
        @SerialName("disabled") Disabled,
        @SerialName("membership") Membership,
    }
}

@Serializable
data class VocabularyHeadword(
    val text: String,
    val reading: String? = null,
    val partOfSpeech: String = "",
    val meaning: String = "",
)

@Serializable
data class VocabularyLine(
    val text: String,
    val reading: String? = null,
    val translation: String? = null,
    val note: String? = null,
)

@Serializable
data class VocabularyCandidate(val term: String, val meaning: String)

/** One of the three cards. The wire tells them apart by `mode`. */
@Serializable(with = VocabularyContentSerializer::class)
sealed interface VocabularyContent {
    val headword: VocabularyHeadword
}

@Serializable
data class VocabularyLookup(
    override val headword: VocabularyHeadword,
    val senses: List<Sense> = emptyList(),
    val expressions: List<VocabularyLine> = emptyList(),
    val related: List<Related> = emptyList(),
) : VocabularyContent {
    @Serializable
    data class Sense(val term: String, val meaning: String, val explanation: String = "", val relevant: Boolean = false)

    @Serializable
    data class Related(val term: String, val relation: String = "")
}

@Serializable
data class VocabularyAnalysis(
    override val headword: VocabularyHeadword,
    val comparisons: List<Comparison> = emptyList(),
    val usage: List<Usage> = emptyList(),
    val examples: List<VocabularyLine> = emptyList(),
    val collocations: List<Collocation> = emptyList(),
) : VocabularyContent {
    @Serializable
    data class Comparison(
        val term: String,
        val difference: String = "",
        val whenToUse: String = "",
        val examples: List<VocabularyLine> = emptyList(),
    )

    @Serializable
    data class Usage(val title: String, val explanation: String = "")

    @Serializable
    data class Collocation(val phrase: String, val meaning: String = "", val pattern: String = "")
}

@Serializable
data class VocabularyMnemonic(
    override val headword: VocabularyHeadword,
    val association: String = "",
    val technique: String = "",
    val retelling: String = "",
    val example: VocabularyLine,
    val quiz: Quiz,
) : VocabularyContent {
    @Serializable
    data class Quiz(val question: String, val answer: String, val hint: String = "")
}

internal object VocabularyContentSerializer :
    JsonContentPolymorphicSerializer<VocabularyContent>(VocabularyContent::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out VocabularyContent> =
        when (element.jsonObject["mode"]?.jsonPrimitive?.content) {
            "analysis" -> VocabularyAnalysis.serializer()
            "mnemonic" -> VocabularyMnemonic.serializer()
            else -> VocabularyLookup.serializer()
        }
}

@Serializable
data class VocabularyReply(
    val outcome: String = "",
    val message: String = "",
    val candidates: List<VocabularyCandidate> = emptyList(),
    val content: VocabularyContent? = null,
)

@Serializable
data class VocabularyRecord(
    val id: String,
    val status: Status,
    val input: VocabularyInput? = null,
    val result: VocabularyReply? = null,
    val charged: Int = 0,
    val favorite: Boolean = false,
    val createdAt: String = "",
    val templateVersion: String = "",
    val error: String? = null,
) {
    @Serializable
    enum class Status {
        @SerialName("pending") Pending,
        @SerialName("completed") Completed,
        @SerialName("clarification") Clarification,
        @SerialName("failed") Failed,
        @SerialName("deleted") Deleted,
    }
}

@Serializable
data class VocabularyRecords(val records: List<VocabularyRecord> = emptyList(), val nextCursor: String? = null)
