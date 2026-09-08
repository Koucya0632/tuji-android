package app.tuji.android.onboarding

import android.content.Context
import app.tuji.android.core.model.LearningDirection

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
class OnboardingStore(context: Context) {

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

    private companion object {
        // The same key names iOS uses, so a future migration or a support
        // question does not have to translate between two vocabularies.
        const val PREFS = "tuji_onboarding"
        const val KEY_DIRECTION = "tuji.learning.direction"
        const val KEY_INTRO = "tuji.onboarding.introDone"
    }
}
