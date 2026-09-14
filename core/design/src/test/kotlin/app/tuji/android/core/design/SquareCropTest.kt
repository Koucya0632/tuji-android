package app.tuji.android.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

class SquareCropTest {

    private fun assertSquare(expected: CropSquare, actual: CropSquare) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.side, actual.side, 0.01f)
    }

    @Test fun `untouched, the middle square of the photo is kept`() {
        assertSquare(CropSquare(left = 500f, top = 0f, side = 2000f), SquareCrop.square(3000f, 2000f, viewport = 300f, zoom = 1f, dx = 0f, dy = 0f))
    }

    @Test fun `zooming in keeps a smaller square around the same centre`() {
        val crop = SquareCrop.square(3000f, 2000f, viewport = 300f, zoom = 2f, dx = 0f, dy = 0f)
        assertEquals(1000f, crop.side, 0.01f)
        assertEquals(1000f, crop.left, 0.01f)
        assertEquals(500f, crop.top, 0.01f)
    }

    /** Moving the photo right on screen brings its left edge into the window. */
    @Test fun `panning is clamped at the photo's edge`() {
        val crop = SquareCrop.square(3000f, 2000f, viewport = 300f, zoom = 1f, dx = 10_000f, dy = 10_000f)
        assertSquare(CropSquare(left = 0f, top = 0f, side = 2000f), crop)
        val (x, y) = SquareCrop.clampOffset(3000f, 2000f, 300f, 1f, 10_000f, 10_000f)
        assertEquals(75f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test fun `zoom stays between filling the window and four times that`() {
        assertEquals(1f, SquareCrop.clampZoom(0.3f))
        assertEquals(SquareCrop.MAX_ZOOM, SquareCrop.clampZoom(9f))
    }
}
