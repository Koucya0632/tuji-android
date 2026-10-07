package app.tuji.android.account

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.CheckInDecision
import app.tuji.android.core.model.CreditCatalog
import app.tuji.android.core.model.CreditWallet
import app.tuji.android.core.model.MonthGrid
import app.tuji.android.core.model.StudyCalendarMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State behind 打卡: the reward (catalog + wallet) and one month of calendar —
 * iOS's `CheckInModel`.
 *
 * Application-lifetime so 今日's chip, the 打卡 sheet and the study finish
 * screens read one wallet: a claim in one place clears the dot everywhere. The
 * two halves fail separately: a calendar that will not load still leaves the
 * reward card working, and the other way round.
 */
class CheckInStore(
    private val loadCatalog: suspend () -> CreditCatalog,
    private val loadWallet: suspend () -> CreditWallet,
    private val checkIn: suspend () -> CreditWallet,
    /** YYYY-MM or null for the server's current month; scoped to the current learning direction by the caller. */
    private val loadCalendar: suspend (String?) -> StudyCalendarMonth = { error("no calendar") },
    private val now: () -> Long = System::currentTimeMillis,
) : AccountScopedStore {

    data class Snapshot(
        val catalog: CreditCatalog? = null,
        val wallet: CreditWallet? = null,
        val claiming: Boolean = false,
        /** The last claim failed; cleared by the next load or claim. */
        val claimFailed: Boolean = false,
        val calendar: StudyCalendarMonth? = null,
        /** Only when there is no month to keep showing. */
        val calendarFailed: Boolean = false,
    ) {
        fun reward(fallbackStudiedToday: Boolean): CheckInDecision.Reward =
            CheckInDecision.reward(catalog, wallet, fallbackStudiedToday)

        private val monthsBack: Int?
            get() = calendar?.let { MonthGrid.monthsBefore(it.month, it.today.take(7)) }

        /** ‹ stops a year back, and never opens on an account with no study at all. */
        val canShowEarlier: Boolean
            get() = calendar != null && calendar.streak.totalDays > 0 && (monthsBack ?: 0) < MONTHS_BACK

        /** › stops at the current month. */
        val canShowLater: Boolean get() = (monthsBack ?: 0) > 0
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var rewardFetchedAt: Long? = null

    /** For the chip: re-read only when the last read is older than [ttlMs]. */
    suspend fun loadRewardIfStale(ttlMs: Long = 60_000) {
        val at = rewardFetchedAt
        if (at != null && now() - at < ttlMs) return
        loadReward()
    }

    suspend fun loadReward() {
        try {
            val catalog = loadCatalog()
            // A legacy account has no wallet; asking would only collect a 403.
            val wallet = if (catalog.billingMode == "credits" && catalog.checkInEnabled) loadWallet() else null
            _snapshot.update {
                it.copy(catalog = catalog, wallet = if (wallet == null) null else newer(it.wallet, wallet), claimFailed = false)
            }
            rewardFetchedAt = now()
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
            rewardFetchedAt = now()
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

    /** [month] null keeps the month on screen (or the server's current one before the first read). */
    suspend fun loadCalendar(month: String? = _snapshot.value.calendar?.month) {
        try {
            val loaded = loadCalendar.invoke(month)
            _snapshot.update { it.copy(calendar = loaded, calendarFailed = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "calendar load failed", failure)
            _snapshot.update { it.copy(calendarFailed = it.calendar == null) }
        }
    }

    suspend fun showMonth(offset: Int) {
        val current = _snapshot.value
        val month = current.calendar?.month ?: return
        if (offset < 0 && !current.canShowEarlier) return
        if (offset > 0 && !current.canShowLater) return
        loadCalendar(MonthGrid.shift(month, offset) ?: return)
    }

    private fun newer(old: CreditWallet?, new: CreditWallet): CreditWallet =
        if (old == null || new.isNewerThan(old)) new else old

    override fun reset() {
        _snapshot.value = Snapshot()
        rewardFetchedAt = null
    }

    companion object {
        /** How far back ‹ goes. A year is more history than this sheet is for. */
        const val MONTHS_BACK = 12
        private const val TAG = "TujiCheckIn"
    }
}
