package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {

    @Test fun `the goal cannot leave its range`() {
        assertEquals(1, SettingsRules.clampDailyGoal(0))
        assertEquals(1, SettingsRules.clampDailyGoal(-7))
        assertEquals(100, SettingsRules.clampDailyGoal(101))
        assertEquals(12, SettingsRules.clampDailyGoal(12))
    }

    @Test fun `both ends of the range are reachable`() {
        assertEquals(1, SettingsRules.clampDailyGoal(SettingsRules.DAILY_GOAL_MIN))
        assertEquals(100, SettingsRules.clampDailyGoal(SettingsRules.DAILY_GOAL_MAX))
    }

    @Test fun `toggling adds and removes`() {
        assertEquals(listOf("kitchen"), SettingsRules.toggleCategory(emptyList(), "kitchen"))
        assertEquals(emptyList<String>(), SettingsRules.toggleCategory(listOf("kitchen"), "kitchen"))
    }

    /**
     * Empty means "all". Blocking the last removal would leave selecting all
     * twelve by hand as the only way back to the whole dictionary.
     */
    @Test fun `the last theme can be removed`() {
        assertEquals(emptyList<String>(), SettingsRules.toggleCategory(listOf("a"), "a"))
    }

    @Test fun `toggling keeps the other selections and their order`() {
        val out = SettingsRules.toggleCategory(listOf("a", "b", "c"), "b")
        assertEquals(listOf("a", "c"), out)
    }

    /** A Japanese recording has one accent; the control would change nothing. */
    @Test fun `accent only applies while learning English`() {
        assertTrue(SettingsRules.accentApplies("zh-en"))
        assertFalse(SettingsRules.accentApplies("zh-ja"))
    }
}
