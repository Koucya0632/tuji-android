package app.tuji.android.core.model

/**
 * What the camera button opens.
 *
 * There is one capture flow — 罐頭點數 — and this decides whether the account
 * gets it. The pre-credit flow used to be the fallback for everything else; it
 * is retired (tuji-ios docs/adr/0016), and falling back to it would now open a
 * screen whose every request the server refuses.
 */
enum class CaptureEntry {
    /** A 罐頭點數 account: the capture flow. */
    Capture,

    /** Not a member. 自製圖鑑 is a membership feature, so the useful page is that one. */
    Membership,

    /**
     * The entitlement has not arrived. Guessing would show a member the
     * membership page, or a non-member a flow the server refuses.
     */
    Resolving;

    companion object {
        fun of(entitlement: Entitlement?): CaptureEntry = when {
            entitlement == null -> Resolving
            entitlement.billingMode == "credits" -> Capture
            else -> Membership
        }
    }
}
