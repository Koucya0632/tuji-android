package app.tuji.android.core.study

import app.tuji.android.core.study.RefreshTarget.Account
import app.tuji.android.core.study.RefreshTarget.CommunityFeed
import app.tuji.android.core.study.RefreshTarget.CommunityMe
import app.tuji.android.core.study.RefreshTarget.CommunitySaved
import app.tuji.android.core.study.RefreshTarget.CreditBalance
import app.tuji.android.core.study.RefreshTarget.Entitlement
import app.tuji.android.core.study.RefreshTarget.Mastery
import app.tuji.android.core.study.RefreshTarget.MyCollections
import app.tuji.android.core.study.RefreshTarget.PersonalShelves
import app.tuji.android.core.study.RefreshTarget.Progress
import app.tuji.android.core.study.RefreshTarget.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table, row by row. A row that changes here is a behaviour change on a
 * screen somebody else built, so each one is pinned.
 */
class AppMutationTest {

    private val learning = setOf(PersonalShelves, Progress, Stats, Mastery)

    // ── iOS AtlasMutation, line for line ──

    @Test fun `publishing or withdrawing a card moves the wall, not the author's study`() {
        listOf(AppMutation.ItemPublished, AppMutation.ItemWithdrawn).forEach { m ->
            assertEquals("$m", setOf(CommunityFeed), m.targets)
            assertTrue("$m", (m.targets intersect learning).isEmpty())
        }
    }

    @Test fun `a finished capture refreshes the words, progress, stats and the quota`() {
        assertEquals(setOf(PersonalShelves, Progress, Stats, Entitlement), AppMutation.CaptureCompleted.targets)
    }

    @Test fun `deleting cards frees a slot as well as taking words away`() {
        val targets = AppMutation.ItemsDeleted.targets
        assertTrue(Entitlement in targets)
        assertTrue(targets.containsAll(setOf(PersonalShelves, Progress, Stats)))
        assertFalse("nothing public changed", CommunityFeed in targets)
    }

    @Test fun `a spot change reaches nothing beyond the shelf that made it`() {
        assertTrue(AppMutation.ItemSpotChanged.targets.isEmpty())
    }

    @Test fun `a collection reaches the wall only when it is on it`() {
        assertTrue(CommunityFeed in AppMutation.CollectionDeleted(wasPublic = true).targets)
        assertTrue(AppMutation.CollectionDeleted(wasPublic = false).targets.isEmpty())
        assertTrue(CommunityFeed in AppMutation.CollectionAvatarChanged(isPublic = true).targets)
        assertFalse(CommunityFeed in AppMutation.CollectionAvatarChanged(isPublic = false).targets)
        assertTrue(CommunityFeed in AppMutation.CollectionEdited(isPublic = true).targets)
        assertFalse(CommunityFeed in AppMutation.CollectionEdited(isPublic = false).targets)
    }

    @Test fun `publishing or withdrawing a collection moves the wall and the author's list`() {
        listOf(AppMutation.CollectionPublished, AppMutation.CollectionWithdrawn).forEach { m ->
            assertEquals("$m", setOf(MyCollections, CommunityFeed), m.targets)
        }
    }

    // ── the consumption and study sides ──

    @Test fun `taking cards in from 物見 moves 已收進 and what studying them moves`() {
        listOf(AppMutation.CommunityItemSaved, AppMutation.CollectionLearned).forEach { m ->
            assertEquals("$m", setOf(PersonalShelves, Mastery, Progress), m.targets)
        }
    }

    @Test fun `a bookmark is 已收藏 and nothing else`() {
        assertEquals(setOf(CommunitySaved), AppMutation.CollectionBookmarked.targets)
    }

    @Test fun `a saved profile redraws both rows that show it`() {
        assertEquals(setOf(Account, CommunityMe), AppMutation.ProfileSaved.targets)
    }

    @Test fun `closing a session re-reads every score and the streak`() {
        assertEquals(setOf(Mastery, Progress), AppMutation.StudySessionClosed.targets)
    }

    @Test fun `clearing progress re-reads every number it erased`() {
        assertEquals(setOf(Mastery, Progress, Stats), AppMutation.ProgressCleared.targets)
    }

    @Test fun `a purchase re-reads the plan and the balance`() {
        assertEquals(setOf(Entitlement, CreditBalance), AppMutation.PurchaseDelivered.targets)
    }

    // ── the queue ──

    @Test fun `a mutation posted before anyone listens still arrives`() = runTest {
        // 生成佇列 finishing between one activity and the next.
        val invalidator = Invalidator()
        invalidator.post(AppMutation.CaptureCompleted)
        assertEquals(AppMutation.CaptureCompleted.targets, invalidator.targets.first())
    }

    @Test fun `mutations arrive in order, and one that reaches nothing is not sent`() = runTest {
        val invalidator = Invalidator()
        invalidator.post(AppMutation.ItemSpotChanged)
        invalidator.post(AppMutation.ItemWithdrawn)
        invalidator.post(AppMutation.StudySessionClosed)
        assertEquals(
            listOf(setOf(CommunityFeed), setOf(Mastery, Progress)),
            invalidator.targets.take(2).toList(),
        )
    }
}
