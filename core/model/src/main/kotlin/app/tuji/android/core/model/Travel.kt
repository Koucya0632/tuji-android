package app.tuji.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * 跟著 Moira 去旅遊 — what GET /api/travel answers (tuji-web
 * lib/travel/destinations.ts): destinations, their cities, and the 景點 in each.
 * iOS's `Core/Models/Travel.swift`.
 *
 * A 景點 owns no content. Its `wordIds` name ordinary 官方圖鑑 words, so a spot
 * is a second way into words the dictionary already holds.
 *
 * The maps arrive as shapes, not pictures: a grid of cells for the country and
 * a few lines for a city. Names come already in the interface language, so
 * nothing here is translated on the device.
 */

/** A position the server sends as `[x, y]`. */
@Serializable(with = TravelPointSerializer::class)
data class TravelPoint(val x: Double, val y: Double)

object TravelPointSerializer : KSerializer<TravelPoint> {
    private val pair = ListSerializer(Double.serializer())
    override val descriptor: SerialDescriptor = pair.descriptor

    override fun deserialize(decoder: Decoder): TravelPoint {
        val values = decoder.decodeSerializableValue(pair)
        require(values.size >= 2) { "a point needs two numbers" }
        return TravelPoint(values[0], values[1])
    }

    override fun serialize(encoder: Encoder, value: TravelPoint) {
        encoder.encodeSerializableValue(pair, listOf(value.x, value.y))
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
    val label: LabelSide = LabelSide.Right,
    val wordIds: List<String> = emptyList(),
    /** Every word is published, so there is something to open. */
    val open: Boolean = false,
    /** A non-member may study this spot. */
    val free: Boolean = false,
) {
    @Serializable
    enum class LabelSide {
        @SerialName("left") Left,
        @SerialName("right") Right,
    }
}

/**
 * One thing a city map is drawn from. A kind this build does not know is
 * dropped while decoding ([TolerantShapesSerializer]), so a newer server can add
 * one without breaking the map for everyone on an older app.
 */
sealed interface TravelMapShape {
    val name: String
    val nameAt: TravelPoint

    /** A closed loop, e.g. 山手線. */
    data class Ring(val center: TravelPoint, val radius: TravelPoint, override val name: String, override val nameAt: TravelPoint) : TravelMapShape

    /** An open line, e.g. 隅田川. */
    data class River(val points: List<TravelPoint>, override val name: String, override val nameAt: TravelPoint) : TravelMapShape

    /** A filled area, e.g. 東京灣. */
    data class Water(val points: List<TravelPoint>, override val name: String, override val nameAt: TravelPoint) : TravelMapShape

    val isWater: Boolean get() = this !is Ring

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val points = ListSerializer(TravelPointSerializer)

        /** Null for a kind this build does not know, or a shape missing what it needs. */
        fun decode(element: JsonElement): TravelMapShape? = runCatching {
            val o = element as JsonObject
            val name = o.getValue("name").jsonPrimitive.content
            val nameAt = json.decodeFromJsonElement(TravelPointSerializer, o.getValue("nameAt"))
            when (o["kind"]?.jsonPrimitive?.contentOrNull) {
                "ring" -> Ring(
                    center = json.decodeFromJsonElement(TravelPointSerializer, o.getValue("center")),
                    radius = json.decodeFromJsonElement(TravelPointSerializer, o.getValue("radius")),
                    name = name,
                    nameAt = nameAt,
                )
                "river" -> River(json.decodeFromJsonElement(points, o.getValue("points")), name, nameAt)
                "water" -> Water(json.decodeFromJsonElement(points, o.getValue("points")), name, nameAt)
                else -> null
            }
        }.getOrNull()
    }
}

/** Decodes the shapes array, leaving out any one it cannot read rather than failing the city. */
object TolerantShapesSerializer : KSerializer<List<TravelMapShape>> {
    private val raw = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = raw.descriptor

    override fun deserialize(decoder: Decoder): List<TravelMapShape> {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return emptyList()
        return (element as? JsonArray).orEmpty().mapNotNull(TravelMapShape::decode)
    }

    override fun serialize(encoder: Encoder, value: List<TravelMapShape>) =
        throw UnsupportedOperationException("travel shapes are only ever read")
}

@Serializable
data class TravelCity(
    val id: String,
    val name: String,
    val regionId: String,
    /** Width over height of the box the shapes and spots are placed in. */
    val aspect: Double = 1.0,
    @Serializable(with = TolerantShapesSerializer::class)
    val shapes: List<TravelMapShape> = emptyList(),
    val spots: List<TravelSpot> = emptyList(),
) {
    val openSpots: List<TravelSpot> get() = spots.filter { it.open }
}

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
data class TravelGrid(val columns: Int, val rows: Int)

@Serializable
data class TravelDestination(
    val id: String,
    val name: String,
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
data class TravelResponse(val destinations: List<TravelDestination> = emptyList())
