package app.tuji.android.membership

import androidx.annotation.StringRes
import app.tuji.android.R
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.MembershipTier

/**
 * What 會員方案 shows — iOS's `PaywallOffer` and `MembershipBenefits`.
 *
 * Tuji Pro is no longer sold (2026-10-06): the page sells 永久會員 only, and a
 * legacy Pro account just sees that it still has Pro. The rows must match
 * docs/marketing/CLAIMS.md §1.
 */
data class MembershipOffer(
    /** The 永久會員 card exists only under policy v2: before that it bought nothing. */
    val showsLifetime: Boolean,
    val ownsLifetime: Boolean,
    val ownsPro: Boolean,
    val credits: Boolean = false,
) {
    val lifetimeRows: List<Int> get() = LIFETIME

    @get:StringRes
    val headline: Int get() = R.string.membership_lifetime_headline

    companion object {
        /**
         * 圖鑑 and 場面 first — what the app is for — then what the membership
         * opens on top, then which AI features draw on 罐頭點數 (paid per use,
         * no monthly quota). iOS's `MembershipBenefits.lifetime`.
         */
        private val LIFETIME = listOf(
            R.string.membership_benefit_all_series,
            R.string.membership_benefit_scenes,
            R.string.membership_benefit_slots_200,
            R.string.membership_benefit_lists_20,
            R.string.membership_benefit_notes,
            R.string.membership_benefit_community,
            R.string.membership_benefit_ai_credits,
        )

        fun from(entitlement: Entitlement?): MembershipOffer {
            val tier = entitlement?.membershipTier ?: MembershipTier.Free
            val v2 = entitlement?.isPolicyV2 == true
            return MembershipOffer(
                showsLifetime = v2,
                ownsLifetime = tier == MembershipTier.Lifetime || entitlement?.membership?.lifetime != null,
                ownsPro = tier == MembershipTier.Pro,
                credits = entitlement?.billingMode == "credits",
            )
        }
    }
}
