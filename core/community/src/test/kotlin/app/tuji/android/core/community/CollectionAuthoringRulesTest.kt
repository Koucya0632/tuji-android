package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasCollectionMember
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.core.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionAuthoringRulesTest {

    private fun member(id: String, state: String? = "public", eligible: Boolean? = null) =
        AtlasCollectionMember(id = id, lemma = id, publicationState = state, eligible = eligible)

    @Test fun `a title is required and capped at the route's limit`() {
        assertFalse(CollectionAuthoringRules.titleValid("   "))
        assertTrue(CollectionAuthoringRules.titleValid(" 生活日常 "))
        assertTrue(CollectionAuthoringRules.titleValid("字".repeat(60)))
        assertFalse(CollectionAuthoringRules.titleValid("字".repeat(61)))
    }

    @Test fun `only the language being learned is on the shelf`() {
        val cols = listOf(
            AtlasMyCollection(id = "a", targetLanguage = TargetLanguage.JA),
            AtlasMyCollection(id = "b", targetLanguage = TargetLanguage.EN),
        )
        assertEquals(listOf("a"), CollectionAuthoringRules.visible(cols, TargetLanguage.JA).map { it.id })
    }

    @Test fun `an empty collection cannot be published, nor one already public`() {
        assertFalse(CollectionAuthoringRules.canSubmit(ReviewStatus.Draft, emptyList(), submitting = false))
        assertTrue(CollectionAuthoringRules.canSubmit(ReviewStatus.Withdrawn, listOf(member("a")), submitting = false))
        assertFalse(CollectionAuthoringRules.canSubmit(ReviewStatus.Approved, listOf(member("a")), submitting = false))
        assertFalse(CollectionAuthoringRules.canSubmit(ReviewStatus.Takedown, listOf(member("a")), submitting = false))
        assertFalse(CollectionAuthoringRules.canSubmit(ReviewStatus.Draft, listOf(member("a")), submitting = true))
    }

    @Test fun `private and pending members are the ones reviewed with the collection`() {
        val members = listOf(member("a"), member("b", "private"), member("c", "pending"))
        assertEquals(2, CollectionAuthoringRules.unpublishedCount(members))
        assertEquals(MemberBadge.WithCollection, MemberBadge.of(members[1]))
        assertEquals(MemberBadge.InReview, MemberBadge.of(members[2]))
        assertNull(MemberBadge.of(members[0]))
    }

    @Test fun `the picker offers eligible cards not already in`() {
        val candidates = listOf(member("a"), member("b", eligible = false), member("c"))
        assertEquals(listOf("c"), CollectionAuthoringRules.available(candidates, setOf("a")).map { it.id })
    }

    @Test fun `deleting a public collection warns that it leaves 物見`() {
        assertEquals(DeleteWarning.TakesDownFromPublic, CollectionAuthoringRules.deleteWarning(AtlasMyCollection(id = "a", reviewStatus = "approved")))
        assertEquals(DeleteWarning.CancelsReview, CollectionAuthoringRules.deleteWarning(AtlasMyCollection(id = "a", reviewStatus = "pending_auto")))
        assertEquals(DeleteWarning.PrivateOnly, CollectionAuthoringRules.deleteWarning(AtlasMyCollection(id = "a")))
    }

    @Test fun `a live or in-review collection sends unpublished cards through review on their own`() {
        val private = member("p", "private")
        val pending = member("q", "pending")
        val public = member("a", "public")
        // A draft carries them with it.
        assertEquals(PickerBadge.WithCollection, CollectionAuthoringRules.pickerBadge(ReviewStatus.Draft, private))
        assertEquals(PickerBadge.InReview, CollectionAuthoringRules.pickerBadge(ReviewStatus.Draft, pending))
        assertNull(CollectionAuthoringRules.pickerBadge(ReviewStatus.Draft, public))
        // A live one cannot re-enter the gate, so the card goes alone.
        assertEquals(PickerBadge.EntersReviewOnAdd, CollectionAuthoringRules.pickerBadge(ReviewStatus.Approved, private))
        assertEquals(PickerBadge.EntersReviewOnAdd, CollectionAuthoringRules.pickerBadge(ReviewStatus.Pending, pending))
        assertNull(CollectionAuthoringRules.pickerBadge(ReviewStatus.Approved, public))
        assertTrue(CollectionAuthoringRules.submitsMembersOnTheirOwn(ReviewStatus.Approved))
        assertFalse(CollectionAuthoringRules.submitsMembersOnTheirOwn(ReviewStatus.Withdrawn))
    }
}
