package app.tuji.android.assistant

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Moira on 助手's home: breathing, blinking, and wagging her tail the moment a
 * finger lands — iOS's `AnimatedMascot`.
 *
 * The only looping motion on the assistant home. It stops while the page is not
 * resumed and under 移除動畫, where a touch is answered with an announcement
 * instead.
 */
@Composable
fun AnimatedMascot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val artwork = remember { MascotArtwork.load(context.resources) }
    val reduceMotion = rememberReduceMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember { mutableStateOf(MascotAnimationFrame.Rest) }
    var started by remember { mutableLongStateOf(0L) }
    var blinkStarted by remember { mutableStateOf<Long?>(null) }
    var wagStarted by remember { mutableStateOf<Long?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    var animating by remember { mutableStateOf(false) }
    val petted = stringResource(R.string.assistant_pet_done)

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            animating = true
            try {
                started = withFrameNanos { it }
                blinkStarted = null
                wagStarted = null
                launch {
                    delay(2_100)
                    while (isActive) {
                        blinkStarted = clock
                        delay((Random.nextDouble(3.2, 6.0) * 1000).toLong())
                    }
                }
                while (isActive) {
                    clock = withFrameNanos { it }
                    frame = MascotAnimationFrame.at(
                        elapsed = (clock - started) / 1e9,
                        blinkElapsed = blinkStarted?.let { (clock - it) / 1e9 },
                        wagElapsed = wagStarted?.let { (clock - it) / 1e9 },
                    )
                }
            } finally {
                animating = false
                frame = MascotAnimationFrame.Rest
            }
        }
    }

    fun wag() {
        if (animating) wagStarted = clock
    }

    fun pet() {
        // A touch already wagged on press; TalkBack's activation never presses.
        if (wagStarted?.let { (clock - it) / 1e9 > MascotAnimationFrame.WAG_SECONDS } != false) wag()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        if (reduceMotion || manager?.isTouchExplorationEnabled == true) view.announceForAccessibility(petted)
    }

    val label = stringResource(R.string.assistant_pet)
    val hint = stringResource(R.string.assistant_pet_hint)
    Canvas(
        modifier
            // No press dimming: the cat answers by wagging, the moment a finger lands.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    wag()
                }
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = hint) { pet(); true }
            },
    ) {
        artwork?.draw(this, frame)
    }
}

/** Pure time-to-pose mapping, shared by the canvas and deterministic frame checks. */
data class MascotAnimationFrame(val breath: Float, val wag: Float, val eyes: Eyes) {
    enum class Eyes { Open, Half, Closed }

    companion object {
        const val WAG_SECONDS = 1.65
        val Rest = MascotAnimationFrame(0f, 0f, Eyes.Open)

        fun at(elapsed: Double, blinkElapsed: Double?, wagElapsed: Double?): MascotAnimationFrame {
            val breath = (1 - cos(maxOf(0.0, elapsed) * PI * 2 / 3.7)) / 2
            val wag = if (wagElapsed != null && wagElapsed >= 0 && wagElapsed < WAG_SECONDS) {
                sin(wagElapsed * 13) * sin(min(wagElapsed / 0.12, 1.0) * PI / 2) * (1 - wagElapsed / WAG_SECONDS).pow(1.3)
            } else {
                0.0
            }
            val eyes = when {
                blinkElapsed == null || blinkElapsed < 0 || blinkElapsed >= 0.22 -> Eyes.Open
                blinkElapsed >= 0.055 && blinkElapsed < 0.14 -> Eyes.Closed
                else -> Eyes.Half
            }
            return MascotAnimationFrame(breath.toFloat(), wag.toFloat(), eyes)
        }
    }
}

/**
 * The atlas, decoded and cropped once, outside the frame loop. Coordinates are
 * the calibrated 1254-unit source iOS's `AnimatedMascotArtwork` uses.
 */
private class MascotArtwork(
    private val body: ImageBitmap,
    private val tail: ImageBitmap,
    private val halfEyes: List<Patch>,
    private val closedEyes: List<Patch>,
) {
    class Patch(val image: ImageBitmap, val rect: Rect)

    fun draw(scope: DrawScope, frame: MascotAnimationFrame) = with(scope) {
        val fit = min(size.width / 600f, size.height / 580f)
        withTransform({
            translate(size.width / 2, (size.height - 580 * fit) / 2)
            scale(fit, fit, pivot = Offset.Zero)
        }) {
            drawOval(TujiColor.Ink.copy(alpha = 0.08f), topLeft = Offset(-145f, 495f), size = Size(270f, 20f))
            withTransform({
                translate(82f, 461f)
                rotate(Math.toDegrees(frame.wag * 0.36).toFloat(), pivot = Offset.Zero)
            }) {
                image(tail, Rect(-50.4f, -268.8f, -50.4f + 192f, -268.8f + 307.2f))
            }
            withTransform({
                translate(-30f, 510f)
                scale(1 + frame.breath * 0.007f, 1 + frame.breath * 0.012f, pivot = Offset.Zero)
                rotate(Math.toDegrees(frame.wag * 0.006).toFloat(), pivot = Offset.Zero)
                translate(30f, -510f)
            }) {
                image(body, Rect(-215f, 5f, -215f + 370.5f, 5f + 501.6f))
                val patches = when (frame.eyes) {
                    MascotAnimationFrame.Eyes.Open -> emptyList()
                    MascotAnimationFrame.Eyes.Half -> halfEyes
                    MascotAnimationFrame.Eyes.Closed -> closedEyes
                }
                patches.forEach { image(it.image, it.rect) }
            }
        }
    }

    /** Draws [bitmap] stretched into [rect] without rounding the destination to whole units. */
    private fun DrawScope.image(bitmap: ImageBitmap, rect: Rect) {
        withTransform({
            translate(rect.left, rect.top)
            scale(rect.width / bitmap.width, rect.height / bitmap.height, pivot = Offset.Zero)
        }) {
            drawImage(bitmap)
        }
    }

    companion object {
        fun load(resources: Resources): MascotArtwork? {
            val atlas = BitmapFactory.decodeResource(
                resources,
                R.drawable.mascot_interactive_atlas,
                BitmapFactory.Options().apply { inScaled = false },
            ) ?: return null
            val factor = atlas.width / 1254f
            fun crop(x: Float, y: Float, w: Float, h: Float): Bitmap = Bitmap.createBitmap(
                atlas,
                (x * factor).toInt(),
                (y * factor).toInt(),
                (w * factor).toInt(),
                (h * factor).toInt(),
            )
            val centers = listOf(Offset(-84.185f, 198.515f), Offset(44.065f, 207.635f))
            fun patch(crop: Rect, anchor: Offset, scaleX: Float, scaleY: Float, center: Offset): Patch {
                val source = crop(crop.left, crop.top, crop.width, crop.height)
                val masked = source.copy(Bitmap.Config.ARGB_8888, true)
                val local = Offset(anchor.x - crop.left, anchor.y - crop.top)
                // Keep the eye and fade its edge, so the patch melts into the face.
                val canvas = android.graphics.Canvas(masked)
                canvas.scale(masked.width / crop.width, masked.height / crop.height)
                canvas.translate(local.x, local.y)
                canvas.scale(100f, 90f)
                canvas.drawRect(
                    -crop.width, -crop.height, crop.width, crop.height,
                    Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        shader = RadialGradient(
                            0f, 0f, 1f,
                            intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                            floatArrayOf(0f, 0.78f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    },
                )
                return Patch(
                    masked.asImageBitmap(),
                    Rect(
                        offset = Offset(center.x - local.x * scaleX, center.y - local.y * scaleY),
                        size = Size(crop.width * scaleX, crop.height * scaleY),
                    ),
                )
            }
            val closed = listOf(
                patch(Rect(80f, 960f, 310f, 1150f), Offset(190.5f, 1067.5f), 0.5f, 0.5f, centers[0]),
                patch(Rect(350f, 960f, 580f, 1150f), Offset(463f, 1067f), 0.5f, 0.5f, centers[1]),
            )
            val half = listOf(
                patch(Rect(680f, 960f, 920f, 1150f), Offset(804f, 1053f), 109 * 0.57f / 130, 103 * 0.57f / 130, centers[0]),
                patch(Rect(955f, 960f, 1190f, 1150f), Offset(1076f, 1053.5f), 113 * 0.57f / 130, 105 * 0.57f / 130, centers[1]),
            )
            return MascotArtwork(
                body = crop(0f, 0f, 650f, 880f).asImageBitmap(),
                tail = crop(820f, 180f, 400f, 640f).asImageBitmap(),
                halfEyes = half,
                closedEyes = closed,
            )
        }
    }
}
