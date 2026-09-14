package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.TargetLanguage

/** A photo's place in the capture pipeline — iOS's `AtlasImageStatus`. */
enum class ImageStatus {
    Uploaded, Processing, NeedsReview, Confirmed, CardsReady, Failed, Deleted;

    /** A card exists for these, so a photo showing one without its card is a sync gap, not an unfinished capture. */
    val impliesItem: Boolean get() = this == Confirmed || this == CardsReady

    companion object {
        fun of(wire: String?): ImageStatus? = when (wire) {
            "uploaded" -> Uploaded
            "processing" -> Processing
            "needs_review" -> NeedsReview
            "confirmed" -> Confirmed
            "cards_ready" -> CardsReady
            "failed" -> Failed
            "deleted" -> Deleted
            else -> null
        }
    }
}

/**
 * Where a card sits in public review — iOS's `AtlasReviewStatus`. The three
 * pending states the server has are one state to the author.
 */
enum class ReviewStatus {
    Draft, Pending, Approved, Rejected, Takedown, Withdrawn;

    /** Only a live public card can be pulled back; a takedown is not the author's to reverse. */
    val canWithdraw: Boolean get() = this == Approved

    /**
     * What may be put up for review. `Withdrawn` may, because taking it down was
     * the author's own decision; `Takedown` may not.
     */
    val canSubmit: Boolean get() = this == Draft || this == Rejected || this == Withdrawn

    companion object {
        /** Absent on items older than the field: they were never submitted. */
        fun of(wire: String?): ReviewStatus = when (wire) {
            "pending", "pending_auto", "pending_review" -> Pending
            "approved" -> Approved
            "rejected" -> Rejected
            "takedown" -> Takedown
            "withdrawn" -> Withdrawn
            else -> Draft
        }
    }
}

/**
 * What deleting costs beyond the card itself, strongest last. The prompt's
 * first line is what deletion always takes; this is the sentence it adds.
 */
enum class DeleteWarning {
    PrivateOnly,
    /** It is queued for review, and deleting withdraws the submission. */
    CancelsReview,
    /** It is on 物見, and everyone who took it in loses their progress. */
    TakesDownFromPublic;

    companion object {
        fun of(review: ReviewStatus?): DeleteWarning = when (review) {
            ReviewStatus.Pending -> CancelsReview
            ReviewStatus.Approved -> TakesDownFromPublic
            else -> PrivateOnly
        }
    }
}

/** One photo, with the card made from it if there is one. */
data class ShelfRow(val image: AtlasImageSummary, val item: AtlasItem?) {
    val id: String get() = image.id
    val imageStatus: ImageStatus? get() = ImageStatus.of(image.status)
    val review: ReviewStatus? get() = item?.let { ReviewStatus.of(it.reviewStatus) }
    val warning: DeleteWarning get() = DeleteWarning.of(review)
}

/** Which of the shelf's five answers to draw. */
sealed interface ShelfState {
    data object Loading : ShelfState
    data object Loaded : ShelfState
    /** A failed sync, told apart from 「還沒有卡片」 — telling someone who owns cards that they own none. */
    data object Failed : ShelfState
    /** Nothing in this language, but cards in the other: switched 英↔日, not deleted. */
    data class HiddenElsewhere(val count: Int) : ShelfState
    data object Empty : ShelfState
}

/**
 * 圖鑑管理's rules — iOS's `AtlasShelfModel`, without the store.
 *
 * The shelf is scoped to the language being learned: a card of the other
 * language is hidden, never dropped, and the count of what is hidden is said.
 * A photo that has no card yet belongs to no language, so it always shows.
 */
object AtlasShelf {

    fun rows(images: List<AtlasImageSummary>, items: List<AtlasItem>, language: TargetLanguage): List<ShelfRow> {
        val byImage = items.filter { it.deletedAt == null && it.imageId != null }.associateBy { it.imageId }
        return images
            .filter { it.deletedAt == null }
            .sortedByDescending { it.createdAt.orEmpty() }
            .mapNotNull { image ->
                val item = byImage[image.id]
                if (item != null && (item.targetLanguage ?: TargetLanguage.EN) != language) null else ShelfRow(image, item)
            }
    }

    fun hiddenCount(images: List<AtlasImageSummary>, rows: List<ShelfRow>): Int =
        images.count { it.deletedAt == null } - rows.size

    fun state(rows: List<ShelfRow>, hidden: Int, loading: Boolean, failed: Boolean): ShelfState = when {
        rows.isNotEmpty() -> ShelfState.Loaded
        loading -> ShelfState.Loading
        failed -> ShelfState.Failed
        hidden > 0 -> ShelfState.HiddenElsewhere(hidden)
        else -> ShelfState.Empty
    }

    /** A batch warns as strongly as its strongest member. */
    fun warning(rows: List<ShelfRow>, selected: Set<String>): DeleteWarning =
        rows.filter { it.id in selected }.maxOfOrNull { it.warning } ?: DeleteWarning.PrivateOnly
}
