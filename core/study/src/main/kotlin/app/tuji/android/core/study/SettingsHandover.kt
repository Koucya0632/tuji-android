package app.tuji.android.core.study

/**
 * What to do the first time this device's settings meet the server's.
 *
 * Two things on this device are older than the settings endpoint, and both have
 * the same problem:
 *
 *  - **The learning direction.** Android's onboarding wrote it to *local
 *    storage only* — a stopgap from when there was no settings module. The
 *    server has never heard the choice, so its row still holds `zh-en`.
 *  - **The interface language.** Nothing has ever asked. The app has read it
 *    off the device since M6, while the server's row holds its own default of
 *    `zh-Hant` for every account that never opened settings on iOS or the web.
 *
 * Letting the server simply win therefore does not "sync" anything. It silently
 * moves an existing user off the deck they picked and, on a Japanese or English
 * phone, turns the whole app Chinese — on the launch after they update, with no
 * action of theirs and nothing on screen to say why.
 *
 * The rule is a **one-time handover**, not a permanent local override:
 *
 * - Before the handover, this device's answers are the only record of a real
 *   choice, so they are adopted and pushed up.
 * - After it, the server is authoritative — which is what makes changing either
 *   one on another device work at all.
 *
 * iOS arrives at the same place from the other side: it keeps the local
 * `uiLang` and direction until the account's first-run setup is done, then
 * takes the server's. "This device has never synced" is the only version of
 * that signal Android has, having no first-run profile step yet.
 *
 * The cost, stated rather than discovered: someone who picked a UI language on
 * iOS that is *not* their phone's and then installs Android is handed their
 * phone's language, and it propagates back. That is one tap to undo in 設定 —
 * the screen this rule ships with — while the alternative silently breaks every
 * new user on a non-Chinese phone.
 *
 * A value rather than a branch inside the store, because "which of two sources
 * wins, and when" is exactly the kind of rule that gets rewritten by accident.
 */
enum class SettingsHandover {
    /** Take the server's settings as they are. */
    TakeServer,

    /** Adopt this device's answers and save them to the server. */
    PushLocal,
    ;

    companion object {
        /**
         * @param localDirection what onboarding stored, or null if it never ran.
         * @param deviceLanguage the language the device is in. No null case:
         *   a device is always *in* some language, even when nobody chose it
         *   inside this app.
         */
        fun decide(
            localDirection: String?,
            serverDirection: String,
            deviceLanguage: String,
            serverLanguage: String,
            alreadyHandedOver: Boolean,
        ): SettingsHandover = when {
            alreadyHandedOver -> TakeServer
            localDirection != null && localDirection != serverDirection -> PushLocal
            deviceLanguage != serverLanguage -> PushLocal
            else -> TakeServer
        }
    }
}
