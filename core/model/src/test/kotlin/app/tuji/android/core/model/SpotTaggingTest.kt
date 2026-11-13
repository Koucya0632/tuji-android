package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自製圖鑑掛景點 on Android (parent repo CONTEXT.md「景點」, docs/adr/0002).
 *
 * THE RED LINES:
 *  - A card is offered only the open spots of destinations in its language.
 *  - 放到景點頁 needs the card public on 物見 and not taken off that spot.
 *  - 「大家拍的」 never shows this account's own cards or a blocked author.
 *  - A server that predates spots still decodes, with no spot anywhere.
 */
class SpotTaggingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val travel = """
        {"destinations":[{"id":"japan","name":"日本","language":"ja","grid":{"columns":14,"rows":16},
          "regions":[{"id":"kanto","name":"關東","tiles":[[9,7]],"nameAt":[11,7],"cityIds":["tokyo"]}],
          "cities":[{"id":"tokyo","name":"東京","regionId":"kanto","aspect":1.4,"shapes":[],"spots":[
            {"id":"asakusa","name":"淺草","at":[0.7,0.2],"open":true,
             "geo":{"lat":35.7148,"lng":139.7967,"radiusM":600}},
            {"id":"ueno","name":"上野","at":[0.5,0.2],"open":true},
            {"id":"closed","name":"未開","at":[0.1,0.1],"open":false}]}]}]}
    """.trimIndent()

    private val destinations get() = json.decodeFromString<TravelResponse>(travel).destinations

    private fun item(
        lang: TargetLanguage = TargetLanguage.JA,
        visibility: String? = "public",
        review: String? = "approved",
        spot: String? = "asakusa",
        blocked: Boolean = false,
        slug: String? = null,
    ) = AtlasItem(
        id = "i1", targetLanguage = lang, visibility = visibility, reviewStatus = review,
        spotId = spot, spotShareBlocked = blocked, publicSlug = slug,
    )

    @Test fun `a card is offered the open spots in its own language only`() {
        assertEquals(listOf("asakusa", "ueno"), SpotTagging.taggableSpots(item(), destinations).map { it.id })
        assertTrue(SpotTagging.taggableSpots(item(lang = TargetLanguage.EN), destinations).isEmpty())
        // An older server names no language: nothing is offered rather than guessed.
        val old = json.decodeFromString<TravelResponse>(travel.replace("\"language\":\"ja\",", "")).destinations
        assertNull(old.single().language)
        assertTrue(SpotTagging.taggableSpots(item(), old).isEmpty())
    }

    @Test fun `sharing needs a public card at a spot it was not taken off`() {
        assertTrue(SpotTagging.canShare(item()))
        assertTrue(SpotTagging.canShare(item(review = "pending_auto")))
        assertFalse(SpotTagging.canShare(item(visibility = "private")))
        assertFalse(SpotTagging.canShare(item(review = "withdrawn")))
        assertFalse(SpotTagging.canShare(item(review = "takedown")))
        assertFalse(SpotTagging.canShare(item(spot = null)))
        assertFalse(SpotTagging.canShare(item(blocked = true)))
    }

    @Test fun `the map marks spots with a live card`() {
        val items = listOf(
            item(spot = "asakusa"),
            item(spot = null),
            AtlasItem(id = "gone", spotId = "ueno", deletedAt = "2026-11-14T00:00:00Z"),
        )
        assertEquals(setOf("asakusa"), SpotTagging.photographedSpotIds(items))
    }

    @Test fun `大家拍的 drops this account's own cards and blocked authors`() {
        val shared = listOf(
            AtlasPublicItem(id = "1", slug = "mine", lemma = "a", author = AtlasAuthor(handle = "TJ1")),
            AtlasPublicItem(id = "2", slug = "other", lemma = "b", author = AtlasAuthor(handle = "TJ2")),
            AtlasPublicItem(id = "3", slug = "blocked", lemma = "c", author = AtlasAuthor(handle = "TJ3")),
        )
        val kept = SpotTagging.othersShared(shared, listOf(item(slug = "mine"))) { it == "TJ3" }
        assertEquals(listOf("other"), kept.map { it.slug })
    }

    @Test fun `spot fields decode, and an older server's card has none`() {
        val now = json.decodeFromString<AtlasItem>(
            """{"id":"i1","spotId":"asakusa","spotShared":true,"spotShareBlocked":false,"visibility":"public","publicSlug":"s1"}""",
        )
        assertEquals("asakusa", now.spotId)
        assertTrue(now.spotShared)
        assertEquals("s1", now.publicSlug)
        val old = json.decodeFromString<AtlasItem>("""{"id":"i1","lemma":"cat"}""")
        assertNull(old.spotId)
        assertFalse(old.spotShared)
        assertFalse(old.spotShareBlocked)

        val shared = json.decodeFromString<AtlasPublicDetail>("""{"id":"p","slug":"s","lemma":"提灯","spotId":"asakusa"}""")
        assertEquals("asakusa", shared.spotId)
        val photos = json.decodeFromString<TravelSpotPhotos>(
            """{"spot":"asakusa","items":[{"id":"p","slug":"s","lemma":"提灯","spotId":"asakusa"}]}""",
        )
        assertEquals("asakusa", photos.items.single().spotId)
    }
}
