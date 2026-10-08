package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasCollectionMember
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.core.model.TargetLanguage

/** How a member sits against the collection's review — the label on its tile. */
enum class MemberBadge {
    /** Not public yet: it goes to review with the collection. */
    WithCollection,
    InReview,
    ;

    companion object {
        fun of(member: AtlasCollectionMember): MemberBadge? = when (member.publicationState) {
            "private" -> WithCollection
            "pending" -> InReview
            else -> null
        }
    }
}

/**
 * 我的合集's rules — iOS's `MyCollectionsVM`, `CollectionCreateModel`,
 * `CollectionEditVM` and `CollectionCandidatesModel`, without their calls.
 */
/**
 * What a tile in 加入卡片 says about itself — iOS's `collectionPublicationLabel`
 * and its 「加入後送審」. Only the exceptions are marked; a public card carries
 * nothing, because badging every normal photo would be noise.
 */
enum class PickerBadge {
    /** The collection is live or in review: this card goes through review on its own once added. */
    EntersReviewOnAdd,

    /** The collection can still carry it: it is reviewed with the collection. */
    WithCollection,

    /** Already in review. */
    InReview,
}

object CollectionAuthoringRules {

    /** The route's own limit; a longer title is refused, not trimmed. */
    const val TITLE_MAX = 60

    fun titleValid(title: String): Boolean = title.trim().let { it.isNotEmpty() && it.length <= TITLE_MAX }

    /** The shelf shows the language being learned, as 圖鑑管理's cards do. */
    fun visible(collections: List<AtlasMyCollection>, language: TargetLanguage): List<AtlasMyCollection> =
        collections.filter { (it.targetLanguage ?: TargetLanguage.EN) == language }

    /** What deleting costs besides the collection — never the cards in it. */
    fun deleteWarning(collection: AtlasMyCollection): DeleteWarning = DeleteWarning.of(ReviewStatus.of(collection.reviewStatus))

    /** Cards that go to review with the collection because they are not public yet. */
    fun unpublishedCount(members: List<AtlasCollectionMember>): Int = members.count { it.publicationState != "public" }

    /** An empty collection is refused by the server, so the button says so first. */
    fun canSubmit(review: ReviewStatus, members: List<AtlasCollectionMember>, submitting: Boolean): Boolean =
        !submitting && review.canSubmit && members.isNotEmpty()

    /** What 加入項目 offers: eligible, and not already in. */
    fun available(candidates: List<AtlasCollectionMember>, existing: Set<String>): List<AtlasCollectionMember> =
        candidates.filter { it.eligible != false && it.id !in existing }

    /**
     * True while the collection is public or in review: a card that is not
     * public yet then goes through review by itself instead of riding along
     * with the collection. Nothing is blocked by it — the server takes the
     * card and keeps it out of sight until it passes — it is what the picker
     * tells the author will happen. iOS's `submitsMembersOnTheirOwn`.
     */
    fun submitsMembersOnTheirOwn(review: ReviewStatus): Boolean = !review.acceptsUnpublishedMembers

    /** Whether adding this card starts a review of its own. A payload with no state reads as public. */
    fun entersReviewOnAdd(review: ReviewStatus, card: AtlasCollectionMember): Boolean =
        submitsMembersOnTheirOwn(review) && card.publicationState != null && card.publicationState != "public"

    fun pickerBadge(review: ReviewStatus, card: AtlasCollectionMember): PickerBadge? = when {
        entersReviewOnAdd(review, card) -> PickerBadge.EntersReviewOnAdd
        card.publicationState == "private" -> PickerBadge.WithCollection
        card.publicationState == "pending" -> PickerBadge.InReview
        else -> null
    }
}
