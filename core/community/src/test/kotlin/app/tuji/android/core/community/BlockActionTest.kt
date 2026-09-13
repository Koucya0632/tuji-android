package app.tuji.android.core.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockActionTest {

    @Test fun `a blocked author is offered the way back`() {
        assertEquals(BlockAction.Unblock, BlockAction.of(isBlocked = true))
        assertEquals(BlockAction.Block, BlockAction.of(isBlocked = false))
    }

    @Test fun `the reader's own work is theirs to share, not to block`() {
        assertEquals(ViewerRelationship.Mine, ViewerRelationship.of("TJ1", viewerHandle = "tj1", isGuest = false))
        assertEquals(ViewerRelationship.Theirs, ViewerRelationship.of("TJ2", viewerHandle = "TJ1", isGuest = false))
    }

    @Test fun `a guest has no account to moderate with`() {
        assertEquals(ViewerRelationship.Guest, ViewerRelationship.of("TJ2", viewerHandle = null, isGuest = true))
    }

    /** 物見's 我的主頁 row opens the page before the account's own UID may have loaded. */
    @Test fun `a page opened as the reader's own is theirs whatever the UID says`() {
        assertEquals(ViewerRelationship.Mine, ViewerRelationship.of("TJ1", viewerHandle = null, isGuest = false, isSelf = true))
    }

    @Test fun `work with no author relates to no one`() {
        assertNull(ViewerRelationship.of(null, viewerHandle = "TJ1", isGuest = false))
    }

    @Test fun `adding and removing a block ignore how the UID was spelled`() {
        val list = BlockList.none.adding("TJ11111111")
        assertTrue(list.hides("tj11111111"))
        assertFalse(list.removing("tj11111111").hides("TJ11111111"))
        assertEquals("a blank handle adds nothing", 0, BlockList.none.adding(" ").size)
    }
}
