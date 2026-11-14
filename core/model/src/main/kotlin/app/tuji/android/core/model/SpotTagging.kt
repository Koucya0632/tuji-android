package app.tuji.android.core.model

/**
 * A 自製圖鑑 card at a 景點 — the same rules as tuji-web
 * `lib/travel/spot-tagging.ts`, so the card page only offers what the server
 * will accept (parent repo CONTEXT.md「景點」, docs/adr/0002).
 *
 * - A card may sit only at a spot in its own language: Japan's spots take
 *   Japanese cards.
 * - Sharing it there needs it public on 物見, and a spot an admin took it off
 *   stays closed to it.
 *
 * On Android a spot is only ever picked by hand: the system photo picker hands
 * over no location.
 */
object SpotTagging {
    private val SHAREABLE_REVIEW = setOf("pending", "pending_auto", "pending_review", "approved")

    /** The open spots [item] may be tagged to: those of destinations in its language. */
    fun taggableSpots(item: AtlasItem, destinations: List<TravelDestination>): List<TravelSpot> {
        val language = when (item.targetLanguage) {
            TargetLanguage.JA -> "ja"
            TargetLanguage.EN -> "en"
            null -> return emptyList()
        }
        return destinations
            .filter { it.language == language }
            .flatMap { it.cities }
            .flatMap { it.openSpots }
    }

    /** Submitted to 物見 and not refused, withdrawn or taken down. */
    fun isPublic(item: AtlasItem): Boolean =
        item.visibility == "public" && item.reviewStatus in SHAREABLE_REVIEW

    /** Whether 「放到景點頁」 may be turned on for the spot the card carries. */
    fun canShare(item: AtlasItem): Boolean =
        item.spotId != null && isPublic(item) && !item.spotShareBlocked

    /** Spots where this account has tagged a card — marked 拍過 on the map. */
    fun photographedSpotIds(items: List<AtlasItem>): Set<String> =
        items.filter { it.deletedAt == null }.mapNotNull { it.spotId }.toSet()

    /**
     * 「大家拍的」 without this account's own cards and authors it blocked — the
     * server answers the same list to everyone.
     */
    fun othersShared(
        shared: List<AtlasPublicItem>,
        own: List<AtlasItem>,
        isBlocked: (String?) -> Boolean,
    ): List<AtlasPublicItem> {
        val ownSlugs = own.mapNotNull { it.publicSlug }.toSet()
        return shared.filter { it.slug !in ownSlugs && !isBlocked(it.author?.handle) }
    }
}
