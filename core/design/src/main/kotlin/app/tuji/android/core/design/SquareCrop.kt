package app.tuji.android.core.design

import kotlin.math.min

/** A square in image pixels. */
data class CropSquare(val left: Float, val top: Float, val side: Float)

/**
 * The geometry of a fixed-square cropper — iOS's `AvatarCropView`: pan and
 * pinch a photo under a square window, and the window's contents are what is
 * kept.
 *
 * Pure numbers so the one thing that can go quietly wrong — the saved square
 * drifting from the one on screen — has a test, not a squint.
 */
object SquareCrop {

    const val MAX_ZOOM = 4f

    /** Pixels on screen per image pixel at zoom 1: the photo just fills the window. */
    fun baseScale(imageWidth: Float, imageHeight: Float, viewport: Float): Float =
        viewport / min(imageWidth, imageHeight)

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(1f, MAX_ZOOM)

    /**
     * The pan allowed at this zoom: the photo may move until an edge reaches
     * the window, never past it, so the window is never partly empty.
     */
    fun clampOffset(
        imageWidth: Float,
        imageHeight: Float,
        viewport: Float,
        zoom: Float,
        dx: Float,
        dy: Float,
    ): Pair<Float, Float> {
        val scale = baseScale(imageWidth, imageHeight, viewport) * clampZoom(zoom)
        val maxX = ((imageWidth * scale - viewport) / 2f).coerceAtLeast(0f)
        val maxY = ((imageHeight * scale - viewport) / 2f).coerceAtLeast(0f)
        return dx.coerceIn(-maxX, maxX) to dy.coerceIn(-maxY, maxY)
    }

    /**
     * What the window shows, in image pixels. The offset is the photo's
     * displacement on screen, so moving the photo right shows more of its left.
     */
    fun square(
        imageWidth: Float,
        imageHeight: Float,
        viewport: Float,
        zoom: Float,
        dx: Float,
        dy: Float,
    ): CropSquare {
        val (x, y) = clampOffset(imageWidth, imageHeight, viewport, zoom, dx, dy)
        val scale = baseScale(imageWidth, imageHeight, viewport) * clampZoom(zoom)
        val side = min(viewport / scale, min(imageWidth, imageHeight))
        val left = (imageWidth / 2f - x / scale - side / 2f).coerceIn(0f, imageWidth - side)
        val top = (imageHeight / 2f - y / scale - side / 2f).coerceIn(0f, imageHeight - side)
        return CropSquare(left, top, side)
    }
}
