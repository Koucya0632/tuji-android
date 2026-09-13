package app.tuji.android.core.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionIdentityTest {

    @Test fun `a usable server colour is used, lower-cased`() {
        assertEquals("#557a95", CollectionIdentity.colorHex("any", "#557A95"))
    }

    /** It sits behind white text; a colour pulled from a dark or blown-out photo would swallow it. */
    @Test fun `a near-black or near-white server colour falls back to the palette`() {
        assertNull(CollectionIdentity.accepted("#050505"))
        assertNull(CollectionIdentity.accepted("#fafafa"))
        assertNull(CollectionIdentity.accepted("557a95"))
        assertNull(CollectionIdentity.accepted("#zzzzzz"))
    }

    @Test fun `a collection with no colour is always the same palette colour`() {
        val first = CollectionIdentity.colorHex("col-123", null)
        assertEquals(first, CollectionIdentity.colorHex("col-123", null))
        assertTrue(first in CollectionIdentity.palette)
    }

    /**
     * Pinned against the same FNV-1a iOS runs, so a collection is one colour on
     * both phones: the empty id hashes to the offset basis, 14695981039346656037,
     * and that mod 12 is 5.
     */
    @Test fun `the fallback hash matches iOS's`() {
        assertEquals(5, CollectionIdentity.stableIndex(""))
        // "a": (basis xor 0x61) * prime = 0xaf63dc4c8601ec8c = 12638187200555641996; mod 12 = 4.
        assertEquals(4, CollectionIdentity.stableIndex("a"))
    }

    @Test fun `the learn button follows how much is already in the queue`() {
        assertEquals(CollectionLearnAction.AddAll, CollectionLearnAction.of(learning = 0, total = 12))
        assertEquals(CollectionLearnAction.AddRemaining(4), CollectionLearnAction.of(learning = 8, total = 12))
        assertEquals(CollectionLearnAction.AllLearning, CollectionLearnAction.of(learning = 12, total = 12))
    }
}
