package app.tuji.android.core.study

/**
 * What to do with a settings change, given whose settings are on screen.
 *
 * The endpoint takes the whole object, so every save sends *every* field — the
 * one the user touched and all the ones they did not. Before the account's
 * settings have arrived, "all the ones they did not" are this device's seed
 * values: no themes chosen, the default daily goal, the default accent. A
 * change made in that window overwrote the account with them. The launch read
 * only has to fail once — a timeout while online is enough — and the next tap
 * in 設定 empties 學習主題 on every device the account is signed in on.
 *
 * Applying the change locally and saving it later does not fix that: the
 * screen the change was made on showed the seed values too. 學習主題's grid
 * computes the whole new selection from the one on screen, so "add 廚房" made
 * against an empty grid *is* "replace twelve themes with 廚房", however late it
 * is sent. The change itself is wrong, not just its timing.
 *
 * A guest has no server row to overwrite and no token to save with; their
 * settings live on this device, as they always have.
 */
enum class SettingsWrite {
    /** The account's settings are on screen: apply, then save. */
    ApplyAndSave,

    /** A guest: apply on this device. There is nothing to save to. */
    ApplyLocally,

    /** Signed in, and the account's settings have not arrived. Drop it. */
    Refuse,
    ;

    companion object {
        fun decide(signedIn: Boolean, loaded: Boolean): SettingsWrite = when {
            !signedIn -> ApplyLocally
            loaded -> ApplyAndSave
            else -> Refuse
        }
    }
}
