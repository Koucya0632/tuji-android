package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `MemberAccessTests`: one rule for every member feature. */
class MemberAccessTest {

    private fun entitlement(tier: String, policy: String) =
        Entitlement(membership = Membership(tier = tier, policy = policy))

    @Test fun `nothing shows before the entitlement is known`() {
        MemberFeature.entries.forEach {
            assertEquals(MemberAccessLevel.Hidden, MemberAccess.level(it, null))
        }
    }

    @Test fun `policy v1 hides every member feature, even for a member`() {
        MemberFeature.entries.forEach {
            assertEquals(MemberAccessLevel.Hidden, MemberAccess.level(it, entitlement("pro", "v1")))
        }
    }

    @Test fun `a member has every feature open`() {
        listOf("lifetime", "pro").forEach { tier ->
            MemberFeature.entries.forEach {
                assertEquals(MemberAccessLevel.Open, MemberAccess.level(it, entitlement(tier, "v2")))
            }
        }
    }

    @Test fun `a non-member meets locks, and reads what they already have`() {
        val free = entitlement("free", "v2")
        assertEquals(MemberAccessLevel.Locked, MemberAccess.level(MemberFeature.WordListBrowse, free))
        assertEquals(MemberAccessLevel.ReadOnly, MemberAccess.level(MemberFeature.WordListBrowse, free, hasOwnData = true))
        assertEquals(MemberAccessLevel.Locked, MemberAccess.level(MemberFeature.WordListAdd, free, hasOwnData = true))
        assertEquals(MemberAccessLevel.Locked, MemberAccess.level(MemberFeature.WordNote, free))
        assertEquals(MemberAccessLevel.ReadOnly, MemberAccess.level(MemberFeature.WordNote, free, hasOwnData = true))
        assertEquals(MemberAccessLevel.Locked, MemberAccess.level(MemberFeature.CommunityWrite, free))
    }

    @Test fun `insights are asked for by everyone - the server trims them`() {
        assertEquals(MemberAccessLevel.Open, MemberAccess.level(MemberFeature.WordInsights, entitlement("free", "v2")))
    }
}
