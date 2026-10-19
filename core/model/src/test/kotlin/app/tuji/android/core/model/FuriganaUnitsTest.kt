package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FuriganaUnitsTest {

    @Test fun `a line never starts with closing punctuation or small kana`() {
        val units = FuriganaUnits.of(
            listOf(FuriganaSegment("勉強", "べんきょう"), FuriganaSegment("しょう。")),
        )
        assertEquals(
            listOf(
                FuriganaUnits.Unit("勉強", "べんきょう"),
                FuriganaUnits.Unit("しょ", null),
                FuriganaUnits.Unit("う。", null),
            ),
            units,
        )
    }
}
