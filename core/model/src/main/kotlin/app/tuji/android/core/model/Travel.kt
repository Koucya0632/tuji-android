package app.tuji.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 跟著 Moira 去旅遊 — what `GET /api/travel` answers (tuji-web
 * `lib/travel/destinations.ts`): destinations, their cities, and the 景點 in each.
 *
 * A 景點 owns no content. Its `wordIds` name ordinary 官方圖鑑 words, so a spot
 * is a second way into words the catalogue already holds.
 *
 * The maps arrive as shapes, not pictures: a grid of cells for the country and
 * a few lines for a city. Names come already in the interface language.
 */
@Serializable
data class TravelResponse(val destinations: List<TravelDestination> = emptyList())

@Serializable
data class TravelDestination(
    val id: String,
    val name: String,
    /**
     * The place's language (`ja`). A 自製圖鑑 card may be tagged to a spot here
     * only when it is a card for this language. Absent from an older server.
     */
    val language: String? = null,
    val grid: TravelGrid,
    val regions: List<TravelRegion> = emptyList(),
    val cities: List<TravelCity> = emptyList(),
) {
    fun city(id: String): TravelCity? = cities.firstOrNull { it.id == id }

    /** The cities of one region that have something to open. */
    fun openCities(region: TravelRegion): List<TravelCity> =
        region.cityIds.mapNotNull(::city).filter { it.openSpots.isNotEmpty() }
}

@Serializable
data class TravelGrid(val columns: Int, val rows: Int)

@Serializable
data class TravelRegion(
    val id: String,
    val name: String,
    /** Grid cells, `[column, row]`. */
    val tiles: List<TravelPoint> = emptyList(),
    /** Where the name goes, in cells; may sit between cells. */
    val nameAt: TravelPoint,
    val cityIds: List<String> = emptyList(),
)

@Serializable
data class TravelCity(
    val id: String,
    val name: String,
    val regionId: String,
    /** Width over height of the box the shapes and spots are placed in. */
    val aspect: Float,
    val shapes: List<TravelMapShape> = emptyList(),
    val spots: List<TravelSpot> = emptyList(),
) {
    val openSpots: List<TravelSpot> get() = spots.filter { it.open }
}

/**
 * One thing a city map is drawn from.
 *
 * Flat rather than a sealed hierarchy, so a `kind` this build does not know
 * still decodes — and is simply not drawn. The server adds shapes without
 * waiting for an app release.
 */
@Serializable
data class TravelMapShape(
    /** `ring` (a closed loop, 山手線), `river` (an open line) or `water` (a filled area). */
    val kind: String,
    val name: String,
    val nameAt: TravelPoint,
    /** `ring` only. */
    val center: TravelPoint? = null,
    val radius: TravelPoint? = null,
    /** `river` and `water`. */
    val points: List<TravelPoint> = emptyList(),
) {
    val isWater: Boolean get() = kind == KIND_RIVER || kind == KIND_WATER

    companion object {
        const val KIND_RING = "ring"
        const val KIND_RIVER = "river"
        const val KIND_WATER = "water"
    }
}

@Serializable
data class TravelSpot(
    val id: String,
    val name: String,
    /** How the place is written and read in Japanese, whatever the interface language. */
    val native: String = "",
    val reading: String = "",
    /** 0–1 inside the city map. */
    val at: TravelPoint,
    /** `left` or `right`: which side of the marker the name sits on. */
    val label: String = "right",
    val wordIds: List<String> = emptyList(),
    /** Every word is published, so there is something to open. */
    val open: Boolean = false,
    /** A non-member may study this spot. */
    val free: Boolean = false,
) {
    val labelOnLeft: Boolean get() = label == "left"
}

/** A position the server sends as `[x, y]`. */
@Serializable(with = TravelPointSerializer::class)
data class TravelPoint(val x: Float, val y: Float)

internal object TravelPointSerializer : KSerializer<TravelPoint> {
    private val pair = ListSerializer(Float.serializer())
    override val descriptor: SerialDescriptor = pair.descriptor

    override fun deserialize(decoder: Decoder): TravelPoint {
        val values = decoder.decodeSerializableValue(pair)
        return TravelPoint(values.getOrElse(0) { 0f }, values.getOrElse(1) { 0f })
    }

    override fun serialize(encoder: Encoder, value: TravelPoint) =
        encoder.encodeSerializableValue(pair, listOf(value.x, value.y))
}
