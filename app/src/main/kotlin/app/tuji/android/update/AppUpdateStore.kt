package app.tuji.android.update

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which build Google Play has waiting for this account, if any. */
fun interface AppUpdateLookup {
    /**
     * The waiting build's `versionCode`, or `null` for "nothing newer" — which
     * is also the answer when Play cannot be asked at all. An update prompt is
     * not worth an error screen.
     */
    suspend fun availableVersionCode(): Int?
}

/** What survives a relaunch: when Play was last asked, and what was put off. */
interface AppUpdateMemory {
    var lastCheckedAtMillis: Long?
    var snoozed: SnoozedAppUpdate?
}

class PrefsAppUpdateMemory(private val prefs: SharedPreferences) : AppUpdateMemory {

    override var lastCheckedAtMillis: Long?
        get() = prefs.getLong(KEY_CHECKED, 0L).takeIf { it > 0L }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_CHECKED) else putLong(KEY_CHECKED, value)
        }.apply()

    override var snoozed: SnoozedAppUpdate?
        get() {
            val code = prefs.getInt(KEY_SNOOZED_CODE, 0)
            val at = prefs.getLong(KEY_SNOOZED_AT, 0L)
            return if (code > 0 && at > 0L) SnoozedAppUpdate(code, at) else null
        }
        set(value) = prefs.edit().apply {
            if (value == null) {
                remove(KEY_SNOOZED_CODE)
                remove(KEY_SNOOZED_AT)
            } else {
                putInt(KEY_SNOOZED_CODE, value.versionCode)
                putLong(KEY_SNOOZED_AT, value.atMillis)
            }
        }.apply()

    private companion object {
        const val KEY_CHECKED = "lastCheckedAt"
        const val KEY_SNOOZED_CODE = "snoozedVersionCode"
        const val KEY_SNOOZED_AT = "snoozedAt"
    }
}

/**
 * The update prompt's state: at most one version waiting to be mentioned.
 *
 * Per install, not per account, and so not one of the stores sign-out clears —
 * which build is on the phone has nothing to do with who is signed in.
 */
class AppUpdateStore(
    private val installedVersionCode: Int,
    private val lookup: AppUpdateLookup,
    private val memory: AppUpdateMemory,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _pendingVersionCode = MutableStateFlow<Int?>(null)

    /** The version to mention, until it is answered. Whether *now* is the moment is the shell's call. */
    val pendingVersionCode: StateFlow<Int?> = _pendingVersionCode.asStateFlow()

    /** Called on every return to the foreground; the day-long throttle is in here, not at the call site. */
    suspend fun checkIfNeeded() {
        val at = now()
        if (!AppUpdatePolicy.shouldCheck(memory.lastCheckedAtMillis, at)) return
        // A lookup that failed is not a check: leaving the timestamp alone
        // means the next foreground asks again instead of waiting out a day.
        val available = try {
            lookup.availableVersionCode()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        memory.lastCheckedAtMillis = at
        if (AppUpdatePolicy.shouldPrompt(installedVersionCode, available, memory.snoozed, at)) {
            _pendingVersionCode.value = available
        }
    }

    /** Either button. Answering at all means this version goes quiet for a few days. */
    fun dismiss() {
        _pendingVersionCode.value?.let { memory.snoozed = SnoozedAppUpdate(it, now()) }
        _pendingVersionCode.value = null
    }
}
