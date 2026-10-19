package app.tuji.android.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotAnimationFrameTest {

    @Test fun `a blink closes the eyes only in its middle`() {
        assertEquals(MascotAnimationFrame.Eyes.Half, MascotAnimationFrame.at(1.0, 0.02, null).eyes)
        assertEquals(MascotAnimationFrame.Eyes.Closed, MascotAnimationFrame.at(1.0, 0.1, null).eyes)
        assertEquals(MascotAnimationFrame.Eyes.Half, MascotAnimationFrame.at(1.0, 0.2, null).eyes)
        assertEquals(MascotAnimationFrame.Eyes.Open, MascotAnimationFrame.at(1.0, 0.3, null).eyes)
    }

    @Test fun `a wag settles back to still`() {
        assertEquals(0f, MascotAnimationFrame.at(1.0, null, 0.0).wag, 0.0001f)
        assertEquals(0f, MascotAnimationFrame.at(1.0, null, 2.0).wag, 0.0001f)
        assert(kotlin.math.abs(MascotAnimationFrame.at(1.0, null, 0.12).wag) > 0.3f)
    }

    @Test fun `breathing is one slow cycle`() {
        assertEquals(0f, MascotAnimationFrame.at(0.0, null, null).breath, 0.0001f)
        assertEquals(1f, MascotAnimationFrame.at(1.85, null, null).breath, 0.0001f)
    }
}
