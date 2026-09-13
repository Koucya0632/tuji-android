package app.tuji.android.core.study

/**
 * What a settings value may be, decided on the client as well as the server.
 *
 * The server normalises everything it is sent, so nothing here is a security
 * boundary. It is here because the *screen* has to agree with the server about
 * what it just saved: a stepper that lets the number reach 0 shows a 0 for the
 * time it takes the POST to come back and correct it, and the user watches
 * their own input get overruled.
 */
object SettingsRules {

    const val DAILY_GOAL_MIN = 1
    const val DAILY_GOAL_MAX = 100

    /** The picker's choices — iOS's `SettingsOptions.dailyGoals`. */
    val DAILY_GOALS: List<Int> = listOf(5, 10, 15, 20, 30, 50)

    fun clampDailyGoal(value: Int): Int = value.coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)

    /**
     * The picker's rows for an account whose goal is [current].
     *
     * The stepper this picker replaces could set any number, so an account can
     * hold a 12 that none of the six choices is. Offering the six alone would
     * open a list with nothing ticked, which reads as the setting being lost;
     * the goal the account actually has gets a row of its own until it is
     * changed.
     */
    fun dailyGoalOptions(current: Int): List<Int> =
        if (current in DAILY_GOALS) DAILY_GOALS else (DAILY_GOALS + clampDailyGoal(current)).sorted()

    /**
     * A whole selection, as it is stored: no repeats, sorted — iOS writes
     * `ids.sorted()`, and two clients storing one set in two orders show up as
     * a settings change nobody made.
     */
    fun selection(ids: Collection<String>): List<String> = ids.distinct().sorted()

    /**
     * Toggle one theme in the selection.
     *
     * **Removing the last one is allowed**, and gives the empty list — which
     * every reader treats as "all themes". Blocking it would mean the only way
     * back to the whole dictionary is to select all twelve by hand.
     */
    fun toggleCategory(selected: List<String>, id: String): List<String> =
        if (id in selected) selected - id else selected + id

    /**
     * The accent setting only means something while learning English: a
     * Japanese recording has one accent, and offering 英式／美式 beside it is a
     * control that changes nothing.
     */
    fun accentApplies(learningDirection: String): Boolean = learningDirection == "zh-en"
}
