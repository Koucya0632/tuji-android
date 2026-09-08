package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasAuthor

/**
 * 封鎖 — the authors whose public work should never surface.
 *
 * Tuji has no comments, no messages and no follows, so blocking cannot mean
 * "stop them contacting me". It means **stop surfacing them to me**, and it is
 * deliberately one-way and invisible to the person blocked.
 *
 * **Discovery only.** Anything already saved stays. Those words are in the
 * blocker's own 圖鑑 with their own SRS history, and the author is just their
 * provenance — removing them would destroy the blocker's study progress to
 * punish someone else. So nothing here touches a saved item; it only filters
 * the feeds.
 *
 * The list lives on the server (so it follows the account across devices) but
 * is applied **here, on the client**. The public 物見 endpoints are anonymous
 * and share one CDN cache, and a per-user filter would force `no-store` on all
 * of them. A block list is small and changes rarely, so carrying it is cheap
 * and the caches survive.
 */
@JvmInline
value class BlockList(private val handles: Set<String>) {

    /**
     * Whether this author is hidden.
     *
     * Handles are the immutable TJ UID, so the compare is case-insensitive and
     * no caller has to think about how it was spelled.
     */
    fun hides(handle: String?): Boolean {
        if (handle.isNullOrBlank() || handles.isEmpty()) return false
        return handle.lowercase() in handles
    }

    fun hides(author: AtlasAuthor?): Boolean = hides(author?.handle)

    /** What is left of a feed. */
    fun <T> filter(items: List<T>, authorOf: (T) -> AtlasAuthor?): List<T> =
        if (handles.isEmpty()) items else items.filterNot { hides(authorOf(it)) }

    val size: Int get() = handles.size

    companion object {
        /**
         * Nobody hidden.
         *
         * **This is also what a failed fetch produces**, and that is the
         * deliberate choice: the list cannot be read, so nothing is hidden.
         * Failing the other way — hiding everything, or showing an error
         * instead of the feed — would take 物見 away from every user whenever
         * one small request fails, to protect a filter that only matters to the
         * few accounts that use it. Fail open.
         */
        val none = BlockList(emptySet())

        fun of(handles: Iterable<String>): BlockList =
            BlockList(handles.mapNotNull { it.trim().lowercase().ifEmpty { null } }.toSet())
    }
}
