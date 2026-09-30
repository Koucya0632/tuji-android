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
 * The numbers must match docs/MEMBERSHIP_PUBLIC_COPY.md §1. 詞表 and 筆記 are
 * iOS's rows too, but this app has neither screen yet, so they are not listed
 * here — a page must not promise what the app it sits in cannot do.
 */
data class MembershipOffer(
    /** The 永久會員 card exists only under policy v2: before that it bought nothing. */
    val showsLifetime: Boolean,
    val ownsLifetime: Boolean,
    val ownsPro: Boolean,
    /** Pro's AI line follows the limits in force (500 under v1, 200 under v2). */
    val isV2: Boolean,
) {
    val lifetimeRows: List<Int> get() = LIFETIME

    val proRows: List<Int>
        get() = listOf(
            R.string.membership_benefit_slots_300,
            if (isV2) R.string.membership_benefit_ai_200 else R.string.membership_benefit_ai_500,
            R.string.membership_benefit_precision,
            R.string.membership_benefit_support,
        )

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
        )

        fun from(entitlement: Entitlement?): MembershipOffer {
            val tier = entitlement?.membershipTier ?: MembershipTier.Free
            val v2 = entitlement?.isPolicyV2 == true
            return MembershipOffer(
                showsLifetime = v2,
                ownsLifetime = tier == MembershipTier.Lifetime || entitlement?.membership?.lifetime != null,
                ownsPro = tier == MembershipTier.Pro,
                isV2 = v2,
            )
        }
    }
}
