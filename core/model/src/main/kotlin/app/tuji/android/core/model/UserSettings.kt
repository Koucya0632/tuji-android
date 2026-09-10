package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * The account's settings, as `/api/users/settings` sends and takes them.
 *
 * Every field has a default because the endpoint's own normaliser fills in
 * anything missing — so a payload from an older client, or one the user has
 * never saved, decodes rather than failing. The defaults here are the server's
 * (`lib/settings.ts` `DEFAULT_SETTINGS`), which is the only place they can be
 * copied from without the two drifting.
 */
@Serializable
data class UserSettings(
    val dailyGoal: Int = 12,
    /** "us" or "uk". Only consulted while learning English. */
    val accent: String = "us",
    /** Whether a gloss is printed under the word. */
    val showZh: Boolean = true,
    /**
     * Theme ids the user chose to study. **Empty means "none picked"**, which
     * every reader treats as *all* rather than none — a user who has picked
     * nothing has not asked to be shown nothing.
     */
    val studyCategories: List<String> = emptyList(),
    val studyDecks: List<String> = emptyList(),
    val learningDirection: String = "zh-en",
    val uiLang: String = "zh-Hant",
    val fontSize: String = "md",
) {
    val direction: LearningDirection
        get() = LearningDirection.entries.firstOrNull { it.wire == learningDirection }
            ?: LearningDirection.ZH_EN

    val language: UiLanguage
        get() = UiLanguage.entries.firstOrNull { it.wire == uiLang } ?: UiLanguage.ZhHant
}

@Serializable
data class UserSettingsResponse(val settings: UserSettings = UserSettings())
