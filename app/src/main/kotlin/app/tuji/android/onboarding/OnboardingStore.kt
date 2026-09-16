package app.tuji.android.onboarding

import android.content.Context
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.study.StudyHints

/**
 * The two facts launch routing needs, kept across launches.
 *
 * ⚠️ **Local only, for now.** On iOS the learning direction is a *server*
 * setting — `user_settings.learningDirection`, written through `SettingsStore`
 * with a 400ms debounce and synced across a user's devices. Android has no
 * settings module yet (it arrives with 設定 in M4), so a choice made here does
 * not reach the server: sign in on a second device and you will be asked
 * again.
 *
 * Written down rather than left to be discovered, because the failure is
 * quiet — everything works, it just does not follow the account. When the
 * settings module lands, this store keeps [introDone] and hands the direction
 * over.
 */
class OnboardingStore(context: Context) : StudyHints {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var learningDirection: LearningDirection?
        get() = prefs.getString(KEY_DIRECTION, null)
            ?.let { wire -> LearningDirection.entries.firstOrNull { it.wire == wire } }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_DIRECTION) else putString(KEY_DIRECTION, value.wire)
            }.apply()
        }

    var introDone: Boolean
        get() = prefs.getBoolean(KEY_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRO, value).apply()

    /**
     * Whether the five-step tour has run on this install.
     *
     * One flag, not one per account, because what it points at is the app's own
     * furniture — the bar, the hero, 拍照 — and somebody who has been shown
     * where those are does not need showing again because a second person
     * signed in.
     */
    var tourDone: Boolean
        get() = prefs.getBoolean(KEY_TOUR, false)
        set(value) = prefs.edit().putBoolean(KEY_TOUR, value).apply()

    /**
     * [StudyHints] — kept here rather than in a second preferences file because
     * it is the same kind of fact as [introDone]: something the app has already
     * shown this person once.
     */
    /**
     * Whether this account has been through 先幫你排一份學習節奏 **on this device**.
     *
     * Per account, not one flag: two people sharing a phone each get asked
     * once. Per device, not on the server, because it is a record of what this
     * install has shown somebody — which is also why a reinstall asks again,
     * and why `SetupChoices.seed` has to open on the account's real themes
     * rather than on a beginner's.
     */
    fun setupDone(userId: String): Boolean = prefs.getBoolean(setupKey(userId), false)

    fun markSetupDone(userId: String) {
        prefs.edit().putBoolean(setupKey(userId), true).apply()
    }

    private fun setupKey(userId: String) = "$KEY_SETUP_PREFIX$userId"

    /**
     * Whether this device's locally-stored direction has ever been reconciled
     * with the server's. See [app.tuji.android.core.study.SettingsHandover] for
     * why one bit decides which of the two sources wins.
     */
    var settingsHandedOver: Boolean
        get() = prefs.getBoolean(KEY_HANDOVER, false)
        set(value) = prefs.edit().putBoolean(KEY_HANDOVER, value).apply()

    override var reviewHintTaught: Boolean
        get() = prefs.getBoolean(KEY_REVIEW_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_REVIEW_HINT, value).apply()

    private companion object {
        const val KEY_SETUP_PREFIX = "setupDone."
        const val KEY_TOUR = "tourDone"
        // The same key names iOS uses, so a future migration or a support
        // question does not have to translate between two vocabularies.
        const val PREFS = "tuji_onboarding"
        const val KEY_DIRECTION = "tuji.learning.direction"
        const val KEY_INTRO = "tuji.onboarding.introDone"
        const val KEY_REVIEW_HINT = "tuji.study.reviewHintTaught"
        const val KEY_HANDOVER = "tuji.settings.handedOver"
    }
}
