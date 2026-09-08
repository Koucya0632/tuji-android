package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasPublicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListTest {

    private fun item(slug: String, handle: String?) = AtlasPublicItem(
        id = slug, slug = slug, lemma = slug,
        author = handle?.let { AtlasAuthor(handle = it) },
    )

    private val feed = listOf(
        item("a", "TJ11111111"),
        item("b", "TJ22222222"),
        item("c", "TJ11111111"),
        item("d", null),
    )

    @Test fun `an empty list hides nobody`() {
        assertFalse(BlockList.none.hides("TJ11111111"))
        assertEquals(feed, BlockList.none.filter(feed) { it.author })
    }

    @Test fun `a blocked author disappears from the feed`() {
        val out = BlockList.of(listOf("TJ11111111")).filter(feed) { it.author }
        assertEquals(listOf("b", "d"), out.map { it.slug })
    }

    @Test fun `the compare is case-insensitive because the UID is`() {
        val list = BlockList.of(listOf("tj11111111"))
        assertTrue(list.hides("TJ11111111"))
        assertTrue(list.hides("Tj11111111"))
    }

    @Test fun `an item with no author is never hidden`() {
        assertFalse(BlockList.of(listOf("TJ11111111")).hides(null as String?))
        val out = BlockList.of(listOf("TJ11111111", "TJ22222222")).filter(feed) { it.author }
        assertEquals(listOf("d"), out.map { it.slug })
    }

    @Test fun `blank and duplicate handles do not become a rule`() {
        val list = BlockList.of(listOf(" ", "", "TJ11111111", "tj11111111", " TJ11111111 "))
        assertEquals(1, list.size)
        assertTrue(list.hides("TJ11111111"))
    }

    @Test fun `a failed fetch hides nothing rather than everything`() {
        // The whole reason `none` is named: the caller that cannot read the
        // list falls back to it. Hiding everything — or refusing to draw the
        // feed — would take 物見 away from every user to protect a filter that
        // only matters to the few accounts using it.
        val out = BlockList.none.filter(feed) { it.author }
        assertEquals("fail open", 4, out.size)
    }

    @Test fun `blocking is about discovery, so the list never sees a saved item`() {
        // Nothing here takes an "already saved" flag, and that is the point:
        // saved words are in the blocker's own 圖鑑 with their own SRS history.
        // If this type could hide them, someone would eventually make it.
        val list = BlockList.of(listOf("TJ11111111"))
        assertEquals(2, list.filter(feed) { it.author }.size)
    }

    @Test fun `an author object filters the same as a bare handle`() {
        val list = BlockList.of(listOf("TJ11111111"))
        assertTrue(list.hides(AtlasAuthor(handle = "TJ11111111")))
        assertFalse(list.hides(null as AtlasAuthor?))
    }
}
