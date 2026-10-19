package app.tuji.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 語法 — iOS's `GrammarStudying.swift`, the wire half: the three templates,
 * what is asked, the offer, and the three cards Moira answers with.
 */
@Serializable
enum class GrammarMode {
    @SerialName("explain") Explain,
    @SerialName("compare") Compare,
    @SerialName("practice") Practice,
}

@Serializable
enum class GrammarDifficulty {
    @SerialName("basic") Basic,
    @SerialName("advanced") Advanced,
}

@Serializable
enum class GrammarPracticeType {
    @SerialName("mixed") Mixed,
    @SerialName("choice") Choice,
    @SerialName("context") Context,
}

@Serializable
data class GrammarInput(
    val requestId: String,
    val mode: GrammarMode,
    val query: String,
    val context: String,
    val compareWith: String,
    val focus: String,
    val difficulty: GrammarDifficulty,
    val practiceType: GrammarPracticeType,
    /** The card a next step was drafted from. */
    val sourceRecordId: String? = null,
    val uiLanguage: String,
    val learningDirection: String,
    val pricingVersion: String,
    val maxPoints: Int,
)

@Serializable
data class GrammarOffer(
    val enabled: Boolean,
    val reason: VocabularyOffer.Reason? = null,
    val available: Int = 0,
    val pricingVersion: String,
    val modes: List<Price> = emptyList(),
) {
    @Serializable
    data class Price(val mode: GrammarMode, val minPoints: Int, val maxPoints: Int)
}

@Serializable
data class GrammarLine(
    val text: String,
    val reading: String? = null,
    val translation: String? = null,
    val note: String? = null,
)

/** A clarification's choice: one grammar point in what was pasted. */
@Serializable
data class GrammarCandidate(val focus: String, val description: String = "")

@Serializable(with = GrammarContentSerializer::class)
sealed interface GrammarContent {
    /** What 語法紀錄 lists it under. */
    val title: String
    val gist: String
}

@Serializable
data class GrammarExplanation(
    val summary: Summary,
    val structure: Structure,
    val rules: List<Rule> = emptyList(),
    val examples: List<GrammarLine> = emptyList(),
    val scenes: List<Scene> = emptyList(),
) : GrammarContent {
    @Serializable
    data class Summary(val name: String, val meaning: String = "", val explanation: String = "", val highlight: String? = null)

    @Serializable
    data class Structure(
        val formula: String = "",
        val parts: List<Part> = emptyList(),
        val breakdown: String? = null,
        val forms: String? = null,
    ) {
        @Serializable
        data class Part(val part: String, val role: String = "")
    }

    @Serializable
    data class Rule(val title: String, val explanation: String = "")

    @Serializable
    data class Scene(val situation: String, val use: String = "", val avoid: String? = null)

    override val title: String get() = summary.name
    override val gist: String get() = summary.meaning
}

@Serializable
enum class GrammarVerdict {
    @SerialName("interchangeable") Interchangeable,
    @SerialName("conditional") Conditional,
    @SerialName("not") Not,
}

@Serializable
data class GrammarComparison(
    val a: Point,
    val b: Point,
    val chosenBecause: String? = null,
    val summary: String = "",
    val similarity: Similarity,
    val dimensions: List<Dimension> = emptyList(),
    val pitfalls: List<Pitfall> = emptyList(),
    val substitutions: List<Substitution> = emptyList(),
) : GrammarContent {
    @Serializable
    data class Point(val name: String, val meaning: String = "")

    @Serializable
    data class Similarity(
        val shared: String = "",
        val aFocus: String = "",
        val bFocus: String = "",
        val interchangeable: GrammarVerdict,
    )

    @Serializable
    data class Dimension(val aspect: String, val a: String = "", val b: String = "")

    @Serializable
    data class Pitfall(val wrong: String, val fixed: String, val kind: Kind, val reason: String = "") {
        @Serializable
        enum class Kind {
            @SerialName("grammar") Grammar,
            @SerialName("meaning") Meaning,
            @SerialName("register") Register,
            @SerialName("unnatural") Unnatural,
        }
    }

    @Serializable
    data class Substitution(
        val situation: String,
        val a: GrammarLine,
        val b: GrammarLine,
        val verdict: GrammarVerdict,
        val explanation: String = "",
    )

    override val title: String get() = "${a.name} ／ ${b.name}"
    override val gist: String get() = summary
}

@Serializable
data class GrammarPractice(
    val focus: String,
    val difficulty: GrammarDifficulty = GrammarDifficulty.Basic,
    val questions: List<Question> = emptyList(),
    val takeaways: List<String> = emptyList(),
) : GrammarContent {
    @Serializable
    data class Question(
        val id: String,
        val kind: Kind,
        val context: String? = null,
        val prompt: String,
        val options: List<Option>,
        val solution: String = "",
        val answer: String,
        val explanations: List<Explanation> = emptyList(),
        val point: String = "",
    ) {
        @Serializable
        enum class Kind {
            @SerialName("choice") Choice,
            @SerialName("context") Context,
        }

        @Serializable
        data class Option(val id: String, val text: String)

        @Serializable
        data class Explanation(val optionId: String, val reason: String)

        fun reason(option: String): String? = explanations.firstOrNull { it.optionId == option }?.reason
    }

    override val title: String get() = focus
    override val gist: String get() = takeaways.firstOrNull().orEmpty()
}

internal object GrammarContentSerializer : JsonContentPolymorphicSerializer<GrammarContent>(GrammarContent::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out GrammarContent> =
        when (element.jsonObject["mode"]?.jsonPrimitive?.content) {
            "compare" -> GrammarComparison.serializer()
            "practice" -> GrammarPractice.serializer()
            else -> GrammarExplanation.serializer()
        }
}

@Serializable
data class GrammarReply(
    val outcome: String = "",
    val message: String = "",
    val candidates: List<GrammarCandidate> = emptyList(),
    val content: GrammarContent? = null,
)

@Serializable
data class GrammarRecord(
    val id: String,
    val status: VocabularyRecord.Status,
    val input: GrammarInput? = null,
    val result: GrammarReply? = null,
    val charged: Int = 0,
    val favorite: Boolean = false,
    val createdAt: String = "",
    val templateVersion: String = "",
    val error: String? = null,
)

@Serializable
data class GrammarRecords(val records: List<GrammarRecord> = emptyList(), val nextCursor: String? = null)

/** One run through a practice set, or through the questions it got wrong. */
@Serializable
data class GrammarAttempt(
    val id: String,
    /** Only the first run counts as the score. Redoing, or answering after the answers were read, is practice. */
    val isFirst: Boolean,
    val questionIds: List<String>,
    /** Question id → the option first confirmed for it. Fixed once confirmed. */
    val answers: Map<String, String> = emptyMap(),
) {
    val finished: Boolean get() = questionIds.all { answers[it] != null }
}

/** How far one practice set got on this device. Never sent to the server. */
@Serializable
data class GrammarPracticeProgress(
    val attempts: List<GrammarAttempt> = emptyList(),
    /** 查看題目與解析 was opened before the first run was finished. */
    val revealed: Boolean = false,
)
