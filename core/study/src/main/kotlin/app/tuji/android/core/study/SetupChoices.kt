package app.tuji.android.core.study

import app.tuji.android.core.model.TopicPreset

/**
 * The theme ids this app treats as special, rather than as rows in a catalogue.
 *
 * Ported from iOS's `StudyCategoryDefaults`, ids and all. They are wire values:
 * the server's `normalizeStudyCategories` filter is lowercase-kebab only, so a
 * display name like 「廚房」 written here is silently dropped and the account
 * comes back with one theme fewer than the user picked.
 */
object StudyCategoryDefaults {
    const val CUSTOM_ID = "custom"
    const val COMMUNITY_ID = "community"

    /**
     * The personal shelves — 自製圖鑑 and 物見.
     *
     * Ticked from the first launch even while still empty, so anything the user
     * makes or saves lands somewhere they are already studying. A card made on
     * day one that never comes up for review is a card the app lost.
     */
    val ATLAS_IDS = listOf(CUSTOM_ID, COMMUNITY_ID)

    /**
     * Hand-picked opening themes: concrete, indoor, and well populated. Only
     * the fallback now — a new account starts from the default 主題組合, and
     * these three are locked for a non-member under membership v2.
     */
    val BEGINNER_IDS = listOf("kitchen", "bathroom", "living-room")
}

/** What 先幫你排一份學習節奏 starts from, and what it finishes with. */
data class SetupChoices(val topicIds: Set<String>, val dailyGoal: Int) {

    companion object {
        /** iOS's `UserSettings.default.dailyGoal`. */
        const val DEFAULT_DAILY_GOAL = 10

        /**
         * Where the picker opens.
         *
         * **Setup is a per-device flag**, so an account that finished it on an
         * old phone runs it again on a new one — or after a reinstall. Starting
         * from the beginner trio every time and saving a whole settings object
         * built from literals is how one tap on 完成設定 replaces the account's
         * real themes and goal with a new user's. So: if the account already
         * has themes, those are the answer, and this screen is a confirmation
         * rather than a question.
         *
         * @param account the account's settings, once they have arrived for
         *   *this* account. Null while they have not — a half-loaded account
         *   looks exactly like a new one, and cannot be told apart in time.
         * @param catalogIds the themes that exist, so a retired id is never
         *   preselected into a fresh save.
         * @param firstThemesFallback the catalogue in its own order, for a
         *   server whose ids no longer include the hand-picked three.
         * @param presets 主題組合 from `/api/categories`. A new account starts
         *   from the one flagged `isDefault` (留學生, whose themes are all free);
         *   with none — an older server — it falls back to the beginner three.
         */
        fun seed(
            account: AccountThemes?,
            catalogIds: Set<String>,
            firstThemesFallback: List<String>,
            presets: List<TopicPreset> = emptyList(),
        ): SetupChoices {
            if (account != null) {
                val existing = account.studyCategories.toSet().intersect(catalogIds)
                if (existing.isNotEmpty()) return SetupChoices(existing, account.dailyGoal)
            }
            val atlas = StudyCategoryDefaults.ATLAS_IDS.toSet().intersect(catalogIds)
            val preset = presets.firstOrNull { it.isDefault }
            if (preset != null) {
                val themes = TopicPresetChoice.themes(preset, catalogIds)
                if (themes.isNotEmpty()) return SetupChoices(themes + atlas, DEFAULT_DAILY_GOAL)
            }
            val beginner = StudyCategoryDefaults.BEGINNER_IDS.filter { it in catalogIds }
            val opening = if (beginner.size == StudyCategoryDefaults.BEGINNER_IDS.size) {
                beginner.toSet()
            } else {
                firstThemesFallback.take(3).toSet()
            }
            return SetupChoices(opening + atlas, DEFAULT_DAILY_GOAL)
        }
    }

    /** What the account already says, as [seed] reads it. */
    data class AccountThemes(val studyCategories: List<String>, val dailyGoal: Int)
}
