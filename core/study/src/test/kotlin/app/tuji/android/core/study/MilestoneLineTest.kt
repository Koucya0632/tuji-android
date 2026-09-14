package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneLineTest {
    @Test fun `the three thresholds each have their own line and anything else keeps going`() {
        assertEquals(MilestoneLine.Month, MilestoneLine.of(30))
        assertEquals(MilestoneLine.Hundred, MilestoneLine.of(100))
        assertEquals(MilestoneLine.Year, MilestoneLine.of(365))
        assertEquals(MilestoneLine.KeepGoing, MilestoneLine.of(7))
    }
}
