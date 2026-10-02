package app.tuji.android.core.model

/**
 * 會員功能權限 — whether a member feature shows at all, and in which form.
 * iOS's `MemberAccess`.
 *
 * One rule, read off the one [Entitlement] the shell holds. On iOS each feature
 * used to keep its own copy of policy and tier, learned on first load and never
 * refreshed, so a person who bought 永久會員 from a lock came back to the same
 * lock. The feature stores keep their data; this decides access. The server
 * still enforces every write — this only decides what the app offers.
 */
enum class MemberFeature {
    /** 我 → 詞表, and the 詞表 screens. */
    WordListBrowse,

    /** 單字頁「加入詞表」. */
    WordListAdd,

    /** 我的筆記 on a word. */
    WordNote,

    /**
     * 詞條延伸內容. The server trims what a non-member sees; the app only needs
     * to know whether to ask.
     */
    WordInsights,
}

enum class MemberAccessLevel {
    /** Membership policy v1, or not known yet: nothing is drawn. */
    Hidden,

    /** A non-member meets a lock that opens 會員方案. */
    Locked,

    /** A non-member who already has something here (a refund): shown, not editable. */
    ReadOnly,

    Open,
}

object MemberAccess {
    /**
     * The whole rule. [hasOwnData] is whether the account already has something
     * in this feature — lists to browse, a note on this word. A null
     * [entitlement] is one not loaded yet, and hides everything.
     */
    fun level(feature: MemberFeature, entitlement: Entitlement?, hasOwnData: Boolean = false): MemberAccessLevel {
        if (entitlement?.isPolicyV2 != true) return MemberAccessLevel.Hidden
        if (entitlement.membershipTier.isMember) return MemberAccessLevel.Open
        return when (feature) {
            MemberFeature.WordListBrowse -> if (hasOwnData) MemberAccessLevel.ReadOnly else MemberAccessLevel.Locked
            MemberFeature.WordListAdd -> MemberAccessLevel.Locked
            MemberFeature.WordNote -> if (hasOwnData) MemberAccessLevel.ReadOnly else MemberAccessLevel.Locked
            MemberFeature.WordInsights -> MemberAccessLevel.Open
        }
    }
}
