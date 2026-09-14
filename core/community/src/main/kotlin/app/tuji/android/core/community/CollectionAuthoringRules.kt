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
}
