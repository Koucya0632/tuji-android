package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * 合集, from the author's side: `/api/atlas/collections`. Not the public shape
 * ([AtlasPublicCollection]) — it carries the review state and the private
 * preview of an avatar nobody else can see yet.
 */
@Serializable
data class AtlasMyCollection(
    val id: String,
    val slug: String? = null,
    val title: String = "",
    val description: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val reviewStatus: String? = null,
    val itemCount: Int = 0,
    val avatarColor: String? = null,
    val avatarImageUrl: String? = null,
    val coverImageUrl: String? = null,
)

@Serializable
data class AtlasMyCollectionsResponse(val collections: List<AtlasMyCollection> = emptyList())

@Serializable
data class AtlasMyCollectionResponse(val collection: AtlasMyCollection)

/** One collection opened for editing. */
@Serializable
data class AtlasCollectionEdit(
    val id: String,
    val slug: String? = null,
    val title: String = "",
    val description: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val reviewStatus: String? = null,
    val avatarColor: String? = null,
    /** A signed URL for the author's own view: the public copy may not exist yet. */
    val avatarPreviewUrl: String? = null,
    val coverPublicItemId: String? = null,
    val coverImageUrl: String? = null,
)

/**
 * A card in a collection, or one that could be added. [id] is the author's own
 * card; [publicItemId] exists only once it has been published.
 */
@Serializable
data class AtlasCollectionMember(
    val id: String,
    val publicItemId: String? = null,
    val lemma: String = "",
    val displayZhHant: String? = null,
    val reviewStatus: String? = null,
    /** `public`, `pending`, or `private` — whether it goes to review with the collection. */
    val publicationState: String? = null,
    /** False for a card that cannot go in — not confirmed, or the wrong language. */
    val eligible: Boolean? = null,
    val imageUrl: String? = null,
)

@Serializable
data class AtlasCollectionEditResponse(
    val collection: AtlasCollectionEdit,
    val items: List<AtlasCollectionMember> = emptyList(),
)

@Serializable
data class AtlasCollectionCandidatesResponse(val items: List<AtlasCollectionMember> = emptyList())

/** `/collections/{id}/avatar`: the photo and the colour behind it change together. */
@Serializable
data class AtlasCollectionAvatarResponse(
    val avatarColor: String? = null,
    val avatarImageUrl: String? = null,
    val avatarPreviewUrl: String? = null,
)
