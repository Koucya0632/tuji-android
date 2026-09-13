package app.tuji.android.core.community

/**
 * What 封鎖 offers for one author right now — iOS's `BlockAction`.
 *
 * The control's label, the prompt's title, its detail and its confirm button
 * all ask the same question, and iOS once answered it separately on two
 * screens: one offered 解除封鎖, the other only disabled its button, so a
 * reader could undo a block on 作者主頁 and nowhere else. One answer here;
 * the words for each case live beside the screens that print them.
 */
enum class BlockAction {
    Block,
    Unblock,
    ;

    companion object {
        fun of(isBlocked: Boolean): BlockAction = if (isBlocked) Unblock else Block
    }
}

/**
 * What the reader is to an author — iOS's `ViewerRelationship`.
 *
 * One answer for every control that depends on it: 你的分享 instead of
 * 加入學習, and 檢舉 and 封鎖 only on someone else's work. Asking "is this
 * mine" in two places with two different guards is how iOS came to offer
 * 封鎖 on the reader's own page.
 */
enum class ViewerRelationship {
    Mine,
    Theirs,
    /** Signed out: there is no account to report or block with. */
    Guest,
    ;

    companion object {
        /**
         * @param isSelf the caller opened this as the reader's own page. It
         *   wins outright, so a page opened from 物見's 我的主頁 row is never
         *   offered 封鎖 while the account's own UID is still loading.
         * @return null when the work has no author to relate to.
         */
        fun of(
            authorHandle: String?,
            viewerHandle: String?,
            isGuest: Boolean,
            isSelf: Boolean = false,
        ): ViewerRelationship? = when {
            isSelf -> Mine
            authorHandle.isNullOrBlank() -> null
            isGuest -> Guest
            // The UID is server-assigned and immutable, so the compare is safe;
            // the worst a not-yet-loaded viewer can do is show the menu.
            viewerHandle != null && viewerHandle.equals(authorHandle, ignoreCase = true) -> Mine
            else -> Theirs
        }
    }
}
