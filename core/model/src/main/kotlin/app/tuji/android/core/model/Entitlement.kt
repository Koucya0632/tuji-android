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
    /**
     * The three-tier view. Null from a server older than the membership
     * rollout — [membershipTier] then falls back to [plan].
     */
    val membership: Membership? = null,
) {
    /**
     * `plan` keeps its pre-membership meaning: "pro" only while Pro is live. A
     * lifetime member is `plan == "free"` — ask [membershipTier] instead.
     */
    val isPro: Boolean get() = plan == "pro"

    /** free / lifetime / pro. An unknown future tier falls back to [plan] too. */
    val membershipTier: MembershipTier
        get() = membership?.tier?.let(MembershipTier::fromWire)
            ?: if (isPro) MembershipTier.Pro else MembershipTier.Free

    /** v1 = pre-membership limits still in force; v2 = the three tiers. */
    val isPolicyV2: Boolean get() = membership?.policy == "v2"
}

/** 非會員 / 永久會員 / Pro. */
enum class MembershipTier(val wire: String) {
    Free("free"),
    Lifetime("lifetime"),
    Pro("pro"),
    ;

    /**
     * 我's status badge. Latin on purpose and never localized, as on iOS — a
     * product mark, not a sentence.
     */
    val badge: String
        get() = when (this) {
            Free -> "Free"
            Lifetime -> "Lifetime"
            Pro -> "Pro"
        }

    val isMember: Boolean get() = this != Free

    companion object {
        fun fromWire(wire: String): MembershipTier? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `GET /api/atlas/entitlement` → `membership`. Dates stay ISO strings, like
 * `subscriptionExpiresAt` beside them.
 */
@Serializable
data class Membership(
    /** Raw, so an unknown future tier decodes instead of failing the snapshot. */
    val tier: String = "free",
    val lifetime: LifetimeHolding? = null,
    val proExpiresAt: String? = null,
    /**
     * Set only for a lifetime member inside the grace after Pro ended. Read
     * the date off this, never a constant — the server owns how long it is.
     */
    val graceEndsAt: String? = null,
    val canPurchaseLifetime: Boolean = false,
    val canPurchasePro: Boolean = false,
    val policy: String = "v1",
    /**
     * Official themes this account may study; null = every theme. Only a v2
     * non-member gets a list (the server's study gate). Absent from older
     * servers, which decodes as null.
     */
    val studyableCategories: List<String>? = null,
)

@Serializable
data class LifetimeHolding(
    /** appstore / play / legacy_pro / grant */
    val source: String,
    val acquiredAt: String? = null,
)

@Serializable
data class EntitlementUsage(
    val atlasSlots: Int = 0,
    val primaryAiThisMonth: Int = 0,
    val precisionAiThisMonth: Int = 0,
    val savedItems: Int = 0,
)
