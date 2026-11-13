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
    val createdAt: String? = null,
    val deletedAt: String? = null,
)

/** The catalogue entry a confirmed capture becomes. */
@Serializable
data class AtlasItem(
    val id: String,
    val imageId: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val lemma: String = "",
    val displayZhHant: String? = null,
    /** The short name in Japanese / English. Null until 補充 fills them. */
    val displayJa: String? = null,
    val displayEn: String? = null,
    val primaryLabel: String? = null,
    val fineLabel: String? = null,
    val partOfSpeech: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val status: String? = null,
    /** Where it sits in public review: `draft`, `pending_auto`, `approved`, `withdrawn`… */
    val reviewStatus: String? = null,
    /** `public` once submitted to 物見; `private` otherwise. */
    val visibility: String? = null,
    /** The 物見 address once published — how 「大家拍的」 knows a card is this account's own. */
    val publicSlug: String? = null,
    val deletedAt: String? = null,
    /**
     * The 景點 this card is tagged to (跟著 Moira 去旅遊). Private to the owner;
     * absent from a server that predates it.
     */
    val spotId: String? = null,
    /** The owner shares it there: others see it in that spot's 「大家拍的」 while it is public. */
    val spotShared: Boolean = false,
    /** An admin took it off this spot after a 「不是在這裡拍的」 report; it may not go back. */
    val spotShareBlocked: Boolean = false,
)

/** A card's 景點 as the server now holds it, after PUT / DELETE `/api/atlas/items/{id}/spot`. */
@Serializable
data class AtlasItemSpotState(
    val spotId: String? = null,
    val spotShared: Boolean = false,
    val spotShareBlocked: Boolean = false,
)

@Serializable
data class AtlasItemSpotResponse(val item: AtlasItemSpotState = AtlasItemSpotState())

/**
 * `/api/atlas/sync` — every photo this account took and every card made from
 * one. 圖鑑管理 reads it whole: a photo with no card yet is still something the
 * user may want to delete.
 */
@Serializable
data class AtlasSyncResponse(
    val serverTime: String? = null,
    val images: List<AtlasImageSummary> = emptyList(),
    val items: List<AtlasItem> = emptyList(),
    /**
     * 自製 cards locked for being over the slot cap after Pro ended. The whole
     * list every time; absent from older servers, which means nothing locked.
     */
    val lockedItemIds: List<String> = emptyList(),
)

/** One row of `/api/users/top-words` — 我's 需要加強. */
@Serializable
data class TopWord(
    val id: String,
    val word: String,
    val chinese: String? = null,
    val imageUrl: String? = null,
    /** Postgres `float8`; drawn rounded. */
    @Serializable(with = LenientDoubleSerializer::class) val mastery: Double = 0.0,
    val reviewCount: Int = 0,
)

@Serializable
data class TopWordsResponse(val words: List<TopWord> = emptyList())

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
