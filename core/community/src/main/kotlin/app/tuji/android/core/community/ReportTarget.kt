package app.tuji.android.core.community

/**
 * What a 檢舉 is about.
 *
 * One type for the three things a user can report, so a screen names its target
 * instead of picking an endpoint. The three server calls differ only in which
 * id they carry — without this, three near-identical repository methods exist
 * and every caller has to know which one to reach for.
 */
sealed interface ReportTarget {
    /** A published word, addressed by its slug. */
    data class Item(val slug: String) : ReportTarget

    data class Collection(val slug: String) : ReportTarget

    /** An author identity, addressed by the immutable TJ UID. */
    data class Author(val handle: String) : ReportTarget
}

/**
 * Why.
 *
 * The wire values are what the server's one moderation queue stores; the labels
 * live in the UI layer, because a reason with a hard-coded Chinese string in it
 * cannot be shown in a ja or en UI.
 */
enum class ReportReason(val wire: String) {
    Spam("spam"),
    Inappropriate("inappropriate"),
    Copyright("copyright"),
    Wrong("wrong"),
    Other("other"),
}
