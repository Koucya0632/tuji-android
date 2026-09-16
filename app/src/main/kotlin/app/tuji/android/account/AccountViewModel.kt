package app.tuji.android.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.billing.PurchaseGate
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.UserMe
import app.tuji.android.core.network.AccountReading
import app.tuji.android.core.network.EntitlementReading
import app.tuji.android.core.network.WeakWordsReading
import app.tuji.android.core.model.TopWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 我的 — who is signed in, and what they are entitled to.
 *
 * Both come from the server on every appearance rather than being cached at
 * launch: a subscription bought on another device, or one that lapsed
 * overnight, changes what this screen may truthfully say.
 */
class AccountViewModel(
    private val accounts: AccountReading,
    private val entitlements: EntitlementReading,
    /** 需要加強. Null reads nothing — a guest has no review history to be weak at. */
    private val weakWords: WeakWordsReading? = null,
    /**
     * Whether this build can actually take money.
     *
     * False until the Play Console has a verified developer account, a
     * subscription product and licence testers — none of which is code. While
     * it is false the screen **shows no purchase button at all** rather than
     * one that opens a sheet saying "not yet": a control that cannot do its
     * job is worse than its absence, because the user spends a tap finding out.
     *
     * One flag in one place, so the day the store is ready it flips once.
     */
    private val billingAvailable: Boolean = false,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class State(
        val me: UserMe? = null,
        val entitlement: Entitlement? = null,
        val loading: Boolean = true,
        /** See the constructor parameter of the same name. */
        val billingAvailable: Boolean = false,
        /** The reviewed words with the lowest mastery — iOS's 需要加強, three of them. */
        val weak: List<TopWord> = emptyList(),
    ) {
        /**
         * What the paywall may do. Null until the entitlement lands — a verdict
         * computed from a default `free` would offer a purchase to someone who
         * already subscribed on the App Store, which is the one thing ADR-0001
         * exists to prevent.
         */
        val purchase: PurchaseGate.Verdict?
            get() = entitlement?.let { PurchaseGate.verdict(it) }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun refresh() {
        work.launch { reload() }
    }

    /**
     * The same read, awaited.
     *
     * A pull-to-refresh has to know when it is over — an indicator that
     * disappears the instant the finger lifts is a spinner lying about work it
     * never waited for. One body with two doors, rather than a copy that will
     * be the one somebody forgets to change.
     */
    suspend fun reload() {
        run {
            val me = runCatching { accounts.me() }
                .getOrElse {
                    Log.w(TAG, "me failed", it)
                    _state.value.me
                }
            // A failed entitlement read keeps the previous answer rather than
            // falling back to free: telling a paying subscriber they are on the
            // free plan because one request timed out is worse than saying
            // nothing new.
            val ent = runCatching { entitlements.entitlement() }
                .getOrElse {
                    Log.w(TAG, "entitlement failed", it)
                    _state.value.entitlement
                }
            _state.value = _state.value.copy(
                me = me,
                entitlement = ent,
                loading = false,
                billingAvailable = billingAvailable,
            )
        }
        loadWeakWordsNow()
    }

    /**
     * 需要加強. A failure keeps the last list: it is a nudge, and a section
     * that blinks out on a timeout says the words got better.
     */
    fun loadWeakWords() {
        work.launch { loadWeakWordsNow() }
    }

    private suspend fun loadWeakWordsNow() {
        val reader = weakWords ?: return
        runCatching { reader.weakWords(limit = 3) }
            .onSuccess { _state.value = _state.value.copy(weak = it) }
            .onFailure { Log.w(TAG, "weak words failed", it) }
    }

    private companion object {
        const val TAG = "TujiAccount"
    }
}
