package app.tuji.android.membership

import androidx.annotation.StringRes
import app.tuji.android.R
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.MembershipTier

/**
 * What 會員方案 shows — iOS's `PaywallOffer` and `MembershipBenefits`, minus
 * the purchase: this build cannot take money yet (ADR-0001, Play Billing not
 * set up), so the page says what each plan gives and where it can be bought.
 *
 * The numbers must match docs/MEMBERSHIP_PUBLIC_COPY.md §1.
 */
data class MembershipOffer(
    /** The 永久會員 card exists only under policy v2: before that it bought nothing. */
    val showsLifetime: Boolean,
    val ownsLifetime: Boolean,
    val ownsPro: Boolean,
    /** Pro's AI line follows the limits in force (500 under v1, 200 under v2). */
    val isV2: Boolean,
    val credits: Boolean = false,
) {
    val lifetimeRows: List<Int> get() = if (credits) listOf(
        R.string.membership_benefit_all_series, R.string.credit_slots, R.string.credit_ai_points,
    ) else LIFETIME

    val proRows: List<Int>
        get() = buildList {
            add(R.string.membership_benefit_slots_300)
            add(if (isV2) R.string.membership_benefit_ai_200 else R.string.membership_benefit_ai_500)
            add(R.string.membership_benefit_precision)
            // 詞表 does not exist under v1.
            if (isV2) add(R.string.membership_benefit_lists_100)
            add(R.string.membership_benefit_support)
        }

    /** Under v2 the headline sells 永久會員 — unless it is already owned. */
    @get:StringRes
    val headline: Int
        get() = if (showsLifetime && !ownsLifetime) {
            R.string.membership_lifetime_headline
        } else {
            R.string.membership_pro_headline
        }

    companion object {
        private val LIFETIME = listOf(
            R.string.membership_benefit_all_series,
            R.string.membership_benefit_slots_20,
            R.string.membership_benefit_ai_10,
            R.string.membership_benefit_community,
            R.string.membership_benefit_lists_20,
            R.string.membership_benefit_notes,
        )

        fun from(entitlement: Entitlement?): MembershipOffer {
            val tier = entitlement?.membershipTier ?: MembershipTier.Free
            val v2 = entitlement?.isPolicyV2 == true
            return MembershipOffer(
                showsLifetime = v2,
                ownsLifetime = tier == MembershipTier.Lifetime || entitlement?.membership?.lifetime != null,
                ownsPro = tier == MembershipTier.Pro,
                isV2 = v2,
                credits = entitlement?.billingMode == "credits",
            )
        }
    }
}
