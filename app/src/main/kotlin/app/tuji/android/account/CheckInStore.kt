package app.tuji.android.account

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.CheckInDecision
import app.tuji.android.core.model.CreditCatalog
import app.tuji.android.core.model.CreditWallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 打卡's reward half — the catalog and the wallet — ported from iOS's
 * `CheckInModel` without the calendar, which Android does not draw yet.
 *
 * Application-lifetime so the study finish screens and anything added later
 * read one wallet: a claim in one place is the claim everywhere.
 */
class CheckInStore(
    private val loadCatalog: suspend () -> CreditCatalog,
    private val loadWallet: suspend () -> CreditWallet,
    private val checkIn: suspend () -> CreditWallet,
) : AccountScopedStore {

    data class Snapshot(
        val catalog: CreditCatalog? = null,
        val wallet: CreditWallet? = null,
        val claiming: Boolean = false,
        /** The last claim failed; cleared by the next load or claim. */
        val claimFailed: Boolean = false,
    ) {
        fun reward(fallbackStudiedToday: Boolean): CheckInDecision.Reward =
            CheckInDecision.reward(catalog, wallet, fallbackStudiedToday)
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    suspend fun loadReward() {
        try {
            val catalog = loadCatalog()
            // A legacy account has no wallet; asking would only collect a 403.
            val wallet = if (catalog.billingMode == "credits" && catalog.checkInEnabled) loadWallet() else null
            _snapshot.update {
                it.copy(catalog = catalog, wallet = if (wallet == null) null else newer(it.wallet, wallet), claimFailed = false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Keep whatever was showing; the card just goes without its points line.
            Log.w(TAG, "check-in reward load failed", failure)
        }
    }

    /** True when the claim landed. */
    suspend fun claim(): Boolean {
        if (_snapshot.value.claiming) return false
        _snapshot.update { it.copy(claiming = true, claimFailed = false) }
        return try {
            val wallet = checkIn()
            _snapshot.update { it.copy(wallet = newer(it.wallet, wallet), claiming = false) }
            true
        } catch (cancelled: CancellationException) {
            _snapshot.update { it.copy(claiming = false) }
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "check-in claim failed", failure)
            _snapshot.update { it.copy(claiming = false) }
            // The refusal usually means the wallet on screen was stale
            // (studied or claimed on another device).
            loadReward()
            _snapshot.update { it.copy(claimFailed = true) }
            false
        }
    }

    private fun newer(old: CreditWallet?, new: CreditWallet): CreditWallet =
        if (old == null || new.isNewerThan(old)) new else old

    override fun reset() {
        _snapshot.value = Snapshot()
    }

    private companion object {
        const val TAG = "TujiCheckIn"
    }
}
