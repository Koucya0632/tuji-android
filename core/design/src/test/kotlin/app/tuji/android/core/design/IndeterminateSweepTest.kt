package app.tuji.android.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

class IndeterminateSweepTest {

    @Test fun `the fill never walks off its own track`() {
        // The bar is a 35% fill that travels 65%; the two are one decision and
        // the only symptom of getting it wrong is a rule that briefly vanishes
        // off the right edge — which nobody watches an indeterminate bar long
        // enough to catch.
        assertEquals(1f, IndeterminateSweep.FILL + IndeterminateSweep.TRAVEL, 0.0001f)
    }
}
