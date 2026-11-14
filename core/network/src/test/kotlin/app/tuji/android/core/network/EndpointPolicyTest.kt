package app.tuji.android.core.network

import org.junit.Assert.assertEquals
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.LearningDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three access cases, pinned as a table.
 *
 * On iOS these were two independent booleans, and the bug that produced was not
 * theoretical: `isPublic` answered both 「attach no bearer」 and 「never retry a
 * 401」, which part company on exactly one endpoint — the public collection
 * route, whose signed-in caller *does* send a token. Its 401 was the one 401
 * never retried, and the user saw the signed-out view of a collection they had
 * saved.
 */
class EndpointPolicyTest {

    @Test
    fun `only authenticated requires a token up front`() {
        assertTrue(EndpointAccess.Authenticated.requiresToken)
        assertFalse(EndpointAccess.Anonymous.requiresToken)
        assertFalse(EndpointAccess.OptionalToken.requiresToken)
    }

    @Test
    fun `only optional attaches a token opportunistically`() {
        assertFalse(EndpointAccess.Authenticated.attachesTokenWhenAvailable)
        assertFalse(EndpointAccess.Anonymous.attachesTokenWhenAvailable)
        assertTrue(EndpointAccess.OptionalToken.attachesTokenWhenAvailable)
    }

    @Test
    fun `a 401 is retried wherever a token may have been attached`() {
        // Including OptionalToken — the case the old `!isPublic` guard excluded.
        assertTrue(EndpointAccess.Authenticated.mayRetryUnauthorized)
        assertTrue(EndpointAccess.OptionalToken.mayRetryUnauthorized)
        assertFalse(EndpointAccess.Anonymous.mayRetryUnauthorized)
    }

    @Test
    fun `only an anonymous response may sit in a cache shared across accounts`() {
        assertTrue(EndpointAccess.Anonymous.mayBeCachedAcrossCallers)
        assertFalse(EndpointAccess.Authenticated.mayBeCachedAcrossCallers)
        assertFalse(EndpointAccess.OptionalToken.mayBeCachedAcrossCallers)
    }

    /**
     * The collection route's `access` block describes the reader. Sent
     * anonymously from a shared cache it described nobody, and a collection
     * the user had saved opened locked.
     */
    @Test
    fun `the collection detail is asked as the reader, not from a shared cache`() {
        assertTrue(Endpoint.AtlasCollection("c").descriptor.policy.access == EndpointAccess.OptionalToken)
        assertFalse(Endpoint.AtlasCollection("c").descriptor.policy.access.mayBeCachedAcrossCallers)
    }

    /** 跟著 Moira 去旅遊: the map is everyone's; a spot's queue is the caller's own progress. */
    @Test
    fun `the travel map is public and scoped by both languages`() {
        val d = Endpoint.Travel(lang = "zh-Hant", learning = LearningDirection.ZH_JA).descriptor
        assertEquals("/api/travel", d.path)
        assertEquals(listOf("lang" to "zh-Hant", "learning" to "zh-ja"), d.query)
        assertTrue(d.policy.access.mayBeCachedAcrossCallers)
    }

    @Test
    fun `a spot queue names the spot, no theme and no list, and is never cached`() {
        val d = Endpoint.StudySpotQueue("akihabara", StudyMode.New, 30, "zh-Hant", LearningDirection.ZH_JA).descriptor
        assertEquals("/api/study/queue", d.path)
        assertEquals("akihabara", d.query.toMap()["spot"])
        assertFalse(d.query.any { it.first == "category" || it.first == "list" })
        assertTrue(d.policy.access.requiresToken)
        assertEquals(CachePolicy.ReloadIgnoringCache, d.policy.cachePolicy)
    }

    /** 自製圖鑑掛景點: a card's spot is the owner's; 「大家拍的」 is everyone's. */
    @Test
    fun `a card's spot is private and a spot's shared photos are public`() {
        val tag = Endpoint.AtlasItemSpot("i1").descriptor
        assertEquals("/api/atlas/items/i1/spot", tag.path)
        assertTrue(tag.policy.access.requiresToken)
        assertEquals(CachePolicy.ReloadIgnoringCache, tag.policy.cachePolicy)

        val photos = Endpoint.TravelSpotPhotos("asakusa").descriptor
        assertEquals("/api/travel/spots/asakusa/photos", photos.path)
        assertTrue(photos.policy.access.mayBeCachedAcrossCallers)
    }
}

