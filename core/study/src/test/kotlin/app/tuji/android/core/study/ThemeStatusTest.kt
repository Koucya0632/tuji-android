package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStatusTest {

    private val theme = listOf("a", "b", "c")
    private fun scores(vararg pairs: Pair<String, Int>): (String) -> Int? {
        val map = pairs.toMap()
        return { map[it] }
    }

    @Test fun `an empty theme claims nothing`() {
        assertEquals(ThemeStatus.None, ThemeStatus.of(emptyList(), { 100 }, 3 to 3))
    }

    @Test fun `every word at 精通 is 全精通`() {
        val s = scores("a" to 80, "b" to 91, "c" to 100)
        assertEquals(ThemeStatus.Mastered, ThemeStatus.of(theme, s, null))
    }

    /** One word short of the top tier drops the whole theme out of 全精通. */
    @Test fun `one word below 精通 is not 全精通`() {
        val s = scores("a" to 80, "b" to 79, "c" to 100)
        assertEquals(ThemeStatus.None, ThemeStatus.of(theme, s, null))
    }

    @Test fun `seen equals total is 完成`() {
        val s = scores("a" to 40, "b" to 40, "c" to 40)
        assertEquals(ThemeStatus.Completed, ThemeStatus.of(theme, s, 3 to 3))
    }

    /**
     * 全精通 already implies every word was seen, so it must win — otherwise a
     * theme would drop from the stronger claim to the weaker one purely because
     * the server row happened to arrive.
     */
    @Test fun `全精通 wins over 完成`() {
        val s = scores("a" to 100, "b" to 100, "c" to 100)
        assertEquals(ThemeStatus.Mastered, ThemeStatus.of(theme, s, 3 to 3))
    }

    @Test fun `a partly studied theme claims nothing`() {
        val s = scores("a" to 40)
        assertEquals(ThemeStatus.None, ThemeStatus.of(theme, s, 1 to 3))
    }

    /**
     * A zero denominator is a theme the server has no published cards for.
     * `seen >= total` would be trivially true and would badge an empty theme
     * as finished.
     */
    @Test fun `a zero total is not 完成`() {
        assertEquals(ThemeStatus.None, ThemeStatus.of(theme, { null }, 0 to 0))
    }

    /** No progress row yet — the state Android is actually in today. */
    @Test fun `without the progress row only 全精通 is reachable`() {
        assertEquals(ThemeStatus.None, ThemeStatus.of(theme, scores("a" to 90), null))
        assertEquals(
            ThemeStatus.Mastered,
            ThemeStatus.of(theme, scores("a" to 90, "b" to 90, "c" to 90), null),
        )
    }
}
