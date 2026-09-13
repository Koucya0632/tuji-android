package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * 物見 — other people's published words.
 *
 * **The UI says 物見; the wire says `community`/`public`.** The tab was renamed
 * and the endpoints were not, so every path here is `/api/atlas/public/…` while
 * every string the user reads is 物見. Renaming one to match the other would
 * mean a migration on the server for a word that only appears on screen.
 */
@Serializable
data class AtlasAuthor(
    /** The immutable TJ UID. It is the id everywhere: reports, blocks, links. */
    val handle: String,
    /** 暱稱 when set, else the UID again — the server has already decided. */
    val displayName: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val joinedAt: String? = null,
    val publishedCount: Int? = null,
    val saveCount: Int? = null,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: handle
}

/** One published word, as the feed lists it. */
@Serializable
data class AtlasPublicItem(
    val id: String,
    /** The public address. Reports and saves are keyed by this, not by [id]. */
    val slug: String,
    val lemma: String,
    val displayZhHant: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val author: AtlasAuthor? = null,
    val publishedAt: String? = null,
)

@Serializable
data class AtlasPublicFeed(val items: List<AtlasPublicItem> = emptyList())

/**
 * One published word in full.
 *
 * [learningWord] is the **same [WordDetail]** a dictionary entry or a 自製圖鑑
 * card sends — the route builds it with the same function — so this page draws
 * 字詞資料 with the word page's own sections rather than a second copy of them.
 */
@Serializable
data class AtlasPublicDetail(
    val id: String,
    val slug: String,
    val lemma: String,
    val displayZhHant: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val imageUrl: String? = null,
    val author: AtlasAuthor? = null,
    val publishedAt: String? = null,
    val learningWord: WordDetail? = null,
)

@Serializable
data class AtlasPublicDetailResponse(val item: AtlasPublicDetail? = null)

/** A named, published collection of 物見 items. */
@Serializable
data class AtlasPublicCollection(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val targetLanguage: TargetLanguage? = null,
    val author: AtlasAuthor? = null,
    val itemCount: Int = 0,
    val saveCount: Int = 0,
    val coverImageUrl: String? = null,
    val avatarImageUrl: String? = null,
    /** The collection's identity colour, `#rrggbb`; see `CollectionIdentity`. */
    val avatarColor: String? = null,
    val publishedAt: String? = null,
)

/**
 * One collection, opened.
 *
 * [access] is the server's view of what this reader may do with it — whether
 * they own it, have saved it, and how many of its words are already in their
 * own study queue. M3 reads it; the entitlement it also gates is M4's.
 */
@Serializable
data class AtlasCollectionDetail(
    val collection: AtlasPublicCollection? = null,
    val items: List<AtlasPublicItem> = emptyList(),
    val access: AtlasCollectionAccess? = null,
)

@Serializable
data class AtlasCollectionAccess(
    val unlocked: Boolean = false,
    val isOwner: Boolean = false,
    val isSaved: Boolean = false,
    val totalCount: Int = 0,
    val learningCount: Int = 0,
)

@Serializable
data class AtlasCollectionsResponse(val collections: List<AtlasPublicCollection> = emptyList())

/** `/collections/{slug}/save` in all three verbs: where the bookmark stands now, and the new total. */
@Serializable
data class AtlasSaveState(val ok: Boolean = true, val saved: Boolean = false, val saveCount: Int = 0)

/** `/collections/{slug}/learn`. */
@Serializable
data class AtlasCollectionLearnResult(
    val ok: Boolean = true,
    val addedCount: Int = 0,
    val learningCount: Int = 0,
    val totalCount: Int = 0,
)

/** One author's public shelf. */
@Serializable
data class AtlasAuthorPage(
    val author: AtlasAuthor? = null,
    val items: List<AtlasPublicItem> = emptyList(),
    val collections: List<AtlasPublicCollection> = emptyList(),
)

/** `/api/users/blocks`. */
@Serializable
data class BlockedHandles(val handles: List<String> = emptyList(), val total: Int = 0)
