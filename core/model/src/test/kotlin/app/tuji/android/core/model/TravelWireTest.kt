package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跟著 Moira 去旅遊: what the server's answer decodes to — iOS's `TravelTests`
 * decoding half. A map shape this build does not know is dropped, not fatal:
 * the server adds shapes without waiting for an app release.
 */
class TravelWireTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val destination: TravelDestination by lazy {
        json.decodeFromString(TravelResponse.serializer(), TravelFixture.JSON).destinations.single()
    }

    @Test
    fun `points decode from pairs`() {
        val akihabara = destination.cities.flatMap { it.spots }.first { it.id == "akihabara" }
        assertEquals(TravelPoint(0.636, 0.389), akihabara.at)
        assertEquals(TravelSpot.LabelSide.Right, akihabara.label)
        assertEquals(listOf(TravelPoint(9.0, 7.0), TravelPoint(10.0, 7.0)), destination.regions[0].tiles)
    }

    @Test
    fun `a shape it does not know is dropped and the rest kept`() {
        val shapes = destination.city("tokyo")!!.shapes
        assertEquals(listOf("東京灣", "山手線"), shapes.map { it.name })
        assertTrue(shapes[0] is TravelMapShape.Water)
        assertTrue(shapes[1] is TravelMapShape.Ring)
    }

    @Test
    fun `a region lists only cities with something open`() {
        assertEquals(listOf("tokyo"), destination.openCities(destination.regions[0]).map { it.id })
        assertTrue(destination.openCities(destination.regions[1]).isEmpty())
    }
}

object TravelFixture {
    const val JSON = """
    {"destinations":[{"id":"japan","name":"日本","grid":{"columns":14,"rows":16},
      "regions":[
        {"id":"kanto","name":"關東","tiles":[[9,7],[10,7]],"nameAt":[11.8,7.5],"cityIds":["tokyo"]},
        {"id":"kansai","name":"關西","tiles":[[5,9]],"nameAt":[8.8,10.6],"cityIds":[]}],
      "cities":[{"id":"tokyo","name":"東京","regionId":"kanto","aspect":1.4444,
        "shapes":[
          {"kind":"water","points":[[0.6,1],[1,0.8],[1,1]],"name":"東京灣","nameAt":[0.88,0.92]},
          {"kind":"ring","center":[0.45,0.5],"radius":[0.2,0.33],"name":"山手線","nameAt":[0.45,0.5]},
          {"kind":"monorail","points":[[0,0]],"name":"?","nameAt":[0,0]}],
        "spots":[
          {"id":"akihabara","name":"秋葉原","native":"秋葉原","reading":"あきはばら","at":[0.636,0.389],
           "label":"right","wordIds":["claw-machine","pin-badge","trading-card"],"open":true,"free":true},
          {"id":"asakusa","name":"淺草","native":"浅草","reading":"あさくさ","at":[0.733,0.193],
           "label":"right","wordIds":["paper-lantern","fortune-slip"],"open":false,"free":false}]}]}]}
    """
}
