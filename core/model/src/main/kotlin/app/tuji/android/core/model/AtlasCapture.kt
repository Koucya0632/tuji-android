package app.tuji.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * A number that may arrive as a JSON string.
 *
 * Postgres serializes `NUMERIC` as a string through the pg driver, and the
 * atlas routes hand back near-raw rows. The server coerces the fields it knows
 * about (`Number(row.confidence)`), but that is one call site per field and the
 * class of bug has already cost this project once — a `NUMERIC` reaching iOS as
 * a string surfaced to users as 資料解析失敗, which reads like a broken app
 * rather than a serialization detail.
 *
 * Accepting both shapes here costs nothing and removes the whole class.
 */
internal object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: 0.0

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

/** The stored image a capture produced. */
@Serializable
data class AtlasImageSummary(
    val id: String,
    val status: String? = null,
    val imageUrl: String? = null,
    val thumbUrl: String? = null,
)

/** How far the recognition job got. */
@Serializable
data class AtlasRecognitionJob(
    val id: String,
    val status: String? = null,
    val stage: String? = null,
)

/**
 * One thing the model thinks the photo shows.
 *
 * [level] stays a raw String so an unknown future tier never fails decoding —
 * compare through the constants rather than inventing an enum the server can
 * outgrow.
 */
@Serializable
data class AtlasCandidate(
    val id: String,
    val level: String = LEVEL_PRIMARY,
    val label: String,
    val normalizedLabel: String? = null,
    val zhHant: String? = null,
    /** Gloss in the UI language (ja/en interfaces only; null otherwise). */
    val gloss: String? = null,
    @Serializable(with = LenientDoubleSerializer::class) val confidence: Double = 0.0,
    val rank: Int = 0,
) {
    companion object {
        const val LEVEL_PRIMARY = "primary"
        const val LEVEL_FINE = "fine"
    }
}

/** Recognition depth. A second pass costs another AI call — see `CaptureDraft`. */
enum class RecognitionMode(val wire: String) {
    /** Runs inline with the upload. */
    Primary("primary"),

    /** 精準識別 — deliberately a second, explicit request. */
    Escalate("escalate"),
}

@Serializable
data class AtlasUploadResponse(
    val duplicate: Boolean? = null,
    val targetLanguage: TargetLanguage? = null,
    val image: AtlasImageSummary,
    val job: AtlasRecognitionJob? = null,
    /**
     * Candidates come back **with the upload** — recognition runs server-side
     * in the same request. Null or empty when it soft-failed, in which case the
     * image is still usable and the user retries or types the name.
     */
    val candidates: List<AtlasCandidate>? = null,
)

@Serializable
data class AtlasRecognitionResponse(
    val job: AtlasRecognitionJob? = null,
    val candidates: List<AtlasCandidate> = emptyList(),
)

/** What the user settled on. Assembled by `CaptureDraft`, not by a screen. */
@Serializable
data class AtlasConfirmPayload(
    val selectedCandidateId: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val primaryLabel: String,
    val fineLabel: String? = null,
    val lemma: String,
    val displayZhHant: String,
    /** The user's own-language name, for ja/en UIs. Null for Chinese. */
    val displayGloss: String? = null,
    val partOfSpeech: String? = null,
    val category: String? = null,
)

/** The catalogue entry a confirmed capture becomes. */
@Serializable
data class AtlasItem(
    val id: String,
    val imageId: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val lemma: String,
    val displayZhHant: String? = null,
    val primaryLabel: String? = null,
    val fineLabel: String? = null,
    val partOfSpeech: String? = null,
    val imageUrl: String? = null,
    val status: String? = null,
)

@Serializable
data class AtlasCard(val id: String, val cardType: String? = null)

/**
 * What publishing decided.
 *
 * **Not every publish publishes.** A machine gate lets clean submissions
 * straight through and queues risky ones for a human, and the difference
 * matters to the user: "it is live" and "someone will look at it" are different
 * sentences, and showing the first when the second is true is a lie the feed
 * will contradict.
 */
@Serializable
data class AtlasPublishResult(
    val moderation: AtlasModeration? = null,
) {
    val published: Boolean get() = moderation?.published == true
}

@Serializable
data class AtlasModeration(
    val reviewStatus: String? = null,
    val published: Boolean = false,
)

@Serializable
data class AtlasCardsResponse(val cards: List<AtlasCard> = emptyList())
