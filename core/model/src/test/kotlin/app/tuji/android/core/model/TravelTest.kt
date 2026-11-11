package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跟著 Moira 去旅遊: what the server's answer decodes to and what each screen
 * decides from it.
 *
 * THE RED LINES:
 *  - A map shape this build does not know still decodes; it is just not drawn.
 *  - A non-member studies a free spot and meets 會員方案 at any other; an
 *    account that is not gated meets it nowhere.
 *  - A study mode with nothing in it is never offered.
 *  - The entry is drawn exactly when the server names somewhere to go.
 */
class TravelTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val payload = """
        {"destinations":[{"id":"japan","name":"日本","grid":{"columns":14,"rows":16},
          "regions":[
            {"id":"kanto","name":"關東","tiles":[[9,7],[10,7]],"nameAt":[11.8,7.5],"cityIds":["tokyo"]},
            {"id":"kansai","name":"關西","tiles":[[5,9]],"nameAt":[8.8,10.6],"cityIds":[]}],
          "cities":[{"id":"tokyo","name":"東京","regionId":"kanto","aspect":1.4444,
            "shapes":[
              {"kind":"water","points":[[0.6,1],[1,0.8],[1,1]],"name":"東京灣","nameAt":[0.88,0.92]},
              {"kind":"ring","center":[0.45,0.5],"radius":[0.2,0.33],"name":"山手線","nameAt":[0.45,0.5]},
              {"kind":"monorail","points":[[0,0]],"name":"?","nameAt":[0,0],"gauge":"narrow"}],
            "spots":[
              {"id":"akihabara","name":"秋葉原","native":"秋葉原","reading":"あきはばら","at":[0.636,0.389],
               "label":"right","wordIds":["claw-machine","pin-badge","trading-card"],"open":true,"free":true},
              {"id":"asakusa","name":"淺草","native":"浅草","reading":"あさくさ","at":[0.733,0.193],
               "label":"left","wordIds":["paper-lantern","fortune-slip"],"open":false,"free":false}]}]}]}
    """.trimIndent()

    private val destination: TravelDestination = json.decodeFromString<TravelResponse>(payload).destinations.single()
    private fun spot(id: String) = destination.cities.flatMap { it.spots }.single { it.id == id }
    private fun word(id: String, category: String = "collecting-hobbies") = Word(id = id, word = id, category = category)

    private fun state(
        spot: TravelSpot,
        words: List<Word> = spot.wordIds.map { word(it) },
        scores: Map<String, Int> = emptyMap(),
        nextReview: Map<String, Long> = emptyMap(),
        studyable: List<String>? = null,
    ) = TravelDecisions.state(spot, words, scores, nextReview, studyable, now = 1_000L)

    // Decoding

    @Test fun `points decode from pairs`() {
        assertEquals(TravelPoint(0.636f, 0.389f), spot("akihabara").at)
        assertEquals(listOf(TravelPoint(9f, 7f), TravelPoint(10f, 7f)), destination.regions[0].tiles)
        assertTrue(spot("asakusa").labelOnLeft)
        assertFalse(spot("akihabara").labelOnLeft)
    }

    @Test fun `a shape it does not know still decodes, and says it is not water or a ring`() {
        val shapes = destination.city("tokyo")!!.shapes
        assertEquals(listOf("water", "ring", "monorail"), shapes.map { it.kind })
        assertEquals(listOf(true, false, false), shapes.map { it.isWater })
    }

    @Test fun `an empty answer is no destinations, not a failure`() {
        assertEquals(emptyList<TravelDestination>(), json.decodeFromString<TravelResponse>("""{"destinations":[]}""").destinations)
        assertEquals(emptyList<TravelDestination>(), json.decodeFromString<TravelResponse>("{}").destinations)
    }

    @Test fun `a region lists only cities with something open`() {
        assertEquals(listOf("tokyo"), destination.openCities(destination.regions[0]).map { it.id })
        assertEquals(emptyList<TravelCity>(), destination.openCities(destination.regions[1]))
        assertEquals(1, TravelDecisions.openSpotCount(destination))
    }

    // Access

    @Test fun `a non-member studies the free spot only`() {
        val gated = listOf("fruits", "bedroom")
        assertEquals(TravelSpotAccess.Open, TravelDecisions.access(spot("akihabara"), gated))
        assertEquals(TravelSpotAccess.NeedsMembership, TravelDecisions.access(spot("asakusa"), gated))
    }

    @Test fun `an account that is not gated studies every spot`() {
        assertEquals(TravelSpotAccess.Open, TravelDecisions.access(spot("asakusa"), null))
    }

    // Spot state

    @Test fun `studied and due are counted among the words the catalogue has`() {
        val s = state(
            spot("akihabara"),
            // `trading-card` has not loaded; `banana` is not the spot's.
            words = listOf(word("pin-badge"), word("banana", "fruits"), word("claw-machine")),
            scores = mapOf("claw-machine" to 40, "pin-badge" to 10, "trading-card" to 90, "banana" to 5),
            nextReview = mapOf("claw-machine" to 900L, "pin-badge" to 1_100L),
        )
        // The spot's order, not the catalogue's.
        assertEquals(listOf("claw-machine", "pin-badge"), s.words.map { it.id })
        assertEquals(2, s.studied)
        assertEquals(1, s.due)
        assertEquals(1f, s.progress)
        // Nothing new is left, one is due.
        assertEquals(listOf(StudyMode.Review), s.studyModes)
    }

    @Test fun `only modes with something in them are offered`() {
        val spot = spot("akihabara")
        assertEquals(listOf(StudyMode.New), state(spot).studyModes)
        assertEquals(
            listOf(StudyMode.New, StudyMode.Review),
            state(spot, scores = mapOf("pin-badge" to 1), nextReview = mapOf("pin-badge" to 0L)).studyModes,
        )
        assertEquals(emptyList<StudyMode>(), state(spot, scores = spot.wordIds.associateWith { 50 }).studyModes)
    }

    @Test fun `a locked spot offers no mode however much is left`() {
        val s = state(spot("asakusa"), studyable = listOf("fruits"))
        assertEquals(TravelSpotAccess.NeedsMembership, s.access)
        assertEquals(emptyList<StudyMode>(), s.studyModes)
    }

    @Test fun `a spot with no words loaded is empty, not complete`() {
        val s = state(spot("akihabara"), words = emptyList())
        assertEquals(0, s.total)
        assertEquals(0f, s.progress)
        assertEquals(emptyList<StudyMode>(), s.studyModes)
    }

    @Test fun `the city list counts studied words without the catalogue`() {
        assertEquals(2, TravelDecisions.studiedCount(spot("akihabara"), mapOf("pin-badge" to 1, "trading-card" to 2, "x" to 3)))
    }

    // Entry and grid

    @Test fun `the entry shows only when there is somewhere to go`() {
        assertFalse(TravelDecisions.showsEntry(emptyList()))
        assertTrue(TravelDecisions.showsEntry(listOf(destination)))
    }

    @Test fun `cells fill the width with gaps between`() {
        val side = TravelDecisions.cellSide(width = 362f, columns = 14, gap = 2f)
        assertEquals(24f, side)
        assertEquals(338f, TravelDecisions.cellOffset(13f, side, 2f))
        assertEquals(0f, TravelDecisions.cellSide(100f, 0, 2f))
    }
}
