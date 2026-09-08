package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * What this account is allowed to do, as the server sees it.
 *
 * **The server is the authority.** The client mirrors this to grey a button or
 * show a quota; every write is re-checked server-side. Nothing here is a
 * permission — it is a description of one.
 */
@Serializable
data class Entitlement(
    /** `free` or `pro`. */
    val plan: String = "free",
    /**
     * Where Pro came from — `appstore`, `play`, or null.
     *
     * Needed by [app.tuji.android.core.billing.PurchaseGate]: the paywall has
     * to tell an App Store subscriber *where* to manage it, and "you are
     * already Pro" without that is a dead end. Null while the backend has not
     * shipped the field yet, which the gate treats as "unknown, do not block".
     */
    val source: String? = null,
    val subscriptionExpiresAt: String? = null,
    val atlasSlotsLimit: Int = 0,
    val primaryAiSoftLimitMonthly: Int = 0,
    val precisionAiLimitMonthly: Int = 0,
    val savedItemsLimit: Int = 0,
    val adsRequiredForCardGeneration: Boolean = false,
    val usage: EntitlementUsage = EntitlementUsage(),
) {
    val isPro: Boolean get() = plan == "pro"
}

@Serializable
data class EntitlementUsage(
    val atlasSlots: Int = 0,
    val primaryAiThisMonth: Int = 0,
    val precisionAiThisMonth: Int = 0,
    val savedItems: Int = 0,
)
