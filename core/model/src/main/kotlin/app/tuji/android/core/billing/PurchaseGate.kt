package app.tuji.android.core.billing

import app.tuji.android.core.model.Entitlement

/**
 * Whether this device may start a purchase — ADR-0001.
 *
 * A Tuji account is a Supabase account, not a store account, so the same person
 * can subscribe on an iPhone and then sign in on Android. Two stores each take
 * money; Tuji sees one `user_id` and **cannot refund either side**. So the
 * paywall asks before it opens Billing.
 */
object PurchaseGate {

    /** The store that sold the existing subscription, as `source` spells it. */
    const val PLAY = "play"
    const val APP_STORE = "appstore"

    sealed interface Verdict {
        /** Open the billing flow. */
        data object Allowed : Verdict

        /** Already Pro through this same store — nothing to sell. */
        data object AlreadySubscribed : Verdict

        /**
         * Pro came from the *other* store.
         *
         * Carries the source so the screen can say where to manage it. "You are
         * already Pro" with no next step is a dead end, and the whole reason
         * the entitlement has to expose `source`.
         */
        data class ManagedElsewhere(val source: String) : Verdict
    }

    /**
     * @param entitlement what the server says this account has.
     * @param thisStore the store this build buys through — [PLAY] on Android.
     */
    fun verdict(entitlement: Entitlement, thisStore: String = PLAY): Verdict {
        if (!entitlement.isPro) return Verdict.Allowed

        val source = entitlement.source
        // Unknown source: an older backend that does not send the field yet, or
        // a manual grant. **Do not block.** Blocking on an unknown would stop a
        // comped user from ever subscribing, and a grant is not a second
        // charge — the rule is about two stores billing, not about being Pro.
        if (source.isNullOrBlank()) return Verdict.Allowed
        if (source == thisStore) return Verdict.AlreadySubscribed
        return Verdict.ManagedElsewhere(source)
    }
}
