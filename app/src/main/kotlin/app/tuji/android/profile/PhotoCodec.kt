package app.tuji.android.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import app.tuji.android.core.design.CropSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photos in and out: decoded upright, cropped square, re-encoded small.
 *
 * `ImageDecoder` rather than `BitmapFactory` because it applies the EXIF
 * orientation itself — a camera frame decoded without it comes out lying on
 * its side, and the crop window would square the wrong thing.
 */
object PhotoCodec {

    /** iOS's `ImageIntakeEncoding.profile`: the avatar renders at 104pt, and the server keeps a copy. */
    private const val PROFILE_SIDE = 1200
    private const val PROFILE_QUALITY = 86

    /** Big enough to crop a sharp 1200 from after a 4× zoom, small enough to hold in memory. */
    private const val WORKING_SIDE = 2400

    suspend fun decode(bytes: ByteArray): Bitmap = decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))

    suspend fun decode(context: Context, uri: Uri): Bitmap = decode(ImageDecoder.createSource(context.contentResolver, uri))

    private suspend fun decode(source: ImageDecoder.Source): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software, so the pixels can be cropped; hardware bitmaps cannot.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > WORKING_SIDE) {
                val ratio = WORKING_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * ratio).roundToInt(), (info.size.height * ratio).roundToInt())
            }
        }
    }

    suspend fun profileJpeg(bitmap: Bitmap, square: CropSquare): ByteArray = squareJpeg(bitmap, square, PROFILE_SIDE, PROFILE_QUALITY)

    /** iOS's `ImageIntakeEncoding.collection`: the public shelf tile draws it larger than an avatar. */
    suspend fun collectionJpeg(bitmap: Bitmap, square: CropSquare): ByteArray = squareJpeg(bitmap, square, 1600, 82)

    private suspend fun squareJpeg(bitmap: Bitmap, square: CropSquare, maxSide: Int, quality: Int): ByteArray = withContext(Dispatchers.Default) {
        val left = square.left.roundToInt().coerceIn(0, bitmap.width - 1)
        val top = square.top.roundToInt().coerceIn(0, bitmap.height - 1)
        val side = square.side.roundToInt().coerceAtMost(minOf(bitmap.width - left, bitmap.height - top)).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bitmap, left, top, side, side)
        val out = if (side > maxSide) Bitmap.createScaledBitmap(cropped, maxSide, maxSide, true) else cropped
        ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
    }
}
