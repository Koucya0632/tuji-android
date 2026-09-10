package app.tuji.android.settings

import android.util.Log
import app.tuji.android.core.auth.AccountScopedStore
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.model.UserSettings
import app.tuji.android.core.network.SettingsAccess
import app.tuji.android.core.study.SettingsHandover
import app.tuji.android.onboarding.OnboardingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The account's settings, and the one place that writes them.
 *
 * **Optimistic, then debounced.** A change lands in [current] immediately and
 * the POST follows [DEBOUNCE_MS] later, so a stepper held down sends one
 * request rather than fourteen, and the number under the user's thumb never
 * lags behind it. The server's normalised answer is *not* written back over the
 * local value: it would arrive after the next tap and undo it.
 *
 * **A failed save keeps the local value.** The alternative — reverting — makes
 * a control silently spring back with no explanation, and the next successful
 * write will carry the change anyway.
 */
class SettingsStore(
    private val remote: SettingsAccess,
    /**
     * Where the learning direction lived before this store existed. It is still
     * written, because it is what launch routing reads *before* any network
     * call can have answered — the first frame after a cold start must not
     * study the wrong language while the settings request is in flight.
     */
    private val local: OnboardingStore,
    private val scope: CoroutineScope,
) : AccountScopedStore {
    // Not the bare defaults: the direction the user picked during onboarding is
    // already on disk, and opening on `zh-en` would study the wrong deck for as
    // long as the fetch takes.
    private fun seed(): UserSettings =
        local.learningDirection
            ?.let { UserSettings(learningDirection = it.wire) }
            ?: UserSettings()

    private val _current = MutableStateFlow(seed())

    val current: StateFlow<UserSettings> = _current.asStateFlow()

    /** True once the server has answered, so a screen can tell "empty" from "unknown". */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var save: Job? = null

    /**
     * @param deviceLanguage what the device is in, which the handover needs and
     *   this module cannot ask for itself — a `Configuration` belongs to the
     *   screen, not to a store.
     */
    suspend fun load(deviceLanguage: UiLanguage) {
        val fetched = try {
            remote.settings()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "settings load failed — keeping what we had", failure)
            return
        }
        Log.i(TAG, "loaded settings: ${fetched.learningDirection} / ${fetched.uiLang}")
        val handover = SettingsHandover.decide(
            localDirection = local.learningDirection?.wire,
            serverDirection = fetched.learningDirection,
            deviceLanguage = deviceLanguage.wire,
            serverLanguage = fetched.uiLang,
            alreadyHandedOver = local.settingsHandedOver,
        )
        val settled = when (handover) {
            SettingsHandover.TakeServer -> fetched
            // Each field falls back to the server's own value: a device can
            // have a language to hand over and no direction, and forcing the
            // absent one would be inventing a choice nobody made.
            SettingsHandover.PushLocal -> fetched.copy(
                learningDirection = local.learningDirection?.wire ?: fetched.learningDirection,
                uiLang = deviceLanguage.wire,
            )
        }
        _current.value = settled
        _loaded.value = true
        local.learningDirection = settled.direction
        local.settingsHandedOver = true
        if (handover == SettingsHandover.PushLocal) {
            Log.i(TAG, "handing this device up: ${settled.learningDirection} / ${settled.uiLang}")
            try {
                remote.saveSettings(settled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The local values still stand and the flag is already set,
                // so this device keeps the right deck and language; the next
                // change the user makes carries them up. Retrying here would
                // mean a login on a flaky connection blocks on a write nobody
                // is waiting for.
                Log.w(TAG, "handover save failed — this device's answers stand", failure)
            }
        }
    }

    /**
     * Change one thing.
     *
     * The whole object is sent because the endpoint takes the whole object —
     * a partial POST would normalise the absent fields back to their defaults
     * and quietly reset everything the user did not just touch.
     */
    fun update(change: (UserSettings) -> UserSettings) {
        val next = change(_current.value)
        if (next == _current.value) return
        _current.value = next
        local.learningDirection = next.direction
        save?.cancel()
        save = scope.launch {
            delay(DEBOUNCE_MS)
            try {
                remote.saveSettings(next)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "settings save failed — the local value stands", failure)
            }
        }
    }

    /**
     * Forget the signed-out account.
     *
     * A pending save is cancelled rather than allowed to land: it carries the
     * previous account's object, and the token it would go out with belongs to
     * whoever signs in next.
     *
     * [OnboardingStore.settingsHandedOver] is deliberately **not** cleared. It
     * records that this device has met a server, not which account it met —
     * clearing it would make the next account's first load push this device's
     * answers over settings it may well have chosen elsewhere.
     */
    override fun reset() {
        save?.cancel()
        save = null
        _current.value = seed()
        _loaded.value = false
    }

    private companion object {
        const val TAG = "TujiSettings"

        /** iOS's window. Long enough to swallow a held stepper, short enough
         *  that leaving the screen immediately after a tap still saves. */
        const val DEBOUNCE_MS = 400L
    }
}
