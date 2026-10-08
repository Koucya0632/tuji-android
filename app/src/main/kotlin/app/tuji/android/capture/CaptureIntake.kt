package app.tuji.android.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.profile.PhotoCodec
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** Where 拍照新增 starts a photo from. */
enum class CaptureSource { Camera, Library }

private sealed interface IntakeStep {
    data object Camera : IntakeStep
    data object Working : IntakeStep
    data class Cropping(val bitmap: Bitmap, val fromCamera: Boolean) : IntakeStep
}

/**
 * iOS's `ImageIntake(encoding: .capture, crop: .freeform)` for 拍照新增: the
 * full-screen camera or the photo library, then the four-corner crop, then the
 * capture encode. The bytes go to [onPhoto]; nothing is uploaded here.
 */
@Composable
fun CaptureIntake(
    source: CaptureSource,
    onPhoto: (ByteArray) -> Unit,
    onFailed: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<IntakeStep>(if (source == CaptureSource.Camera) IntakeStep.Camera else IntakeStep.Working) }

    fun decode(fromCamera: Boolean, block: suspend () -> Bitmap) {
        step = IntakeStep.Working
        scope.launch {
            runCatching { block() }
                .onSuccess { step = IntakeStep.Cropping(it, fromCamera) }
                .onFailure { onFailed(); onClose() }
        }
    }

    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) onClose() else decode(fromCamera = false) { PhotoCodec.decode(context, uri) }
    }
    fun openLibrary() {
        step = IntakeStep.Working
        library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) { if (source == CaptureSource.Library) openLibrary() }

    when (val s = step) {
        IntakeStep.Camera -> CaptureCameraWindow(
            onPhoto = { bytes -> decode(fromCamera = true) { PhotoCodec.decode(bytes) } },
            onLibrary = ::openLibrary,
            onDismiss = onClose,
        )
        IntakeStep.Working -> TujiWindow(onDismiss = {}, darkGround = true) {
            Box(Modifier.fillMaxSize().background(TujiColor.Ink))
        }
        is IntakeStep.Cropping -> FreeformCropWindow(
            bitmap = s.bitmap,
            onConfirm = { l, t, r, b ->
                scope.launch {
                    runCatching { PhotoCodec.captureCropJpeg(s.bitmap, l, t, r, b) }
                        .onSuccess { onPhoto(it); onClose() }
                        .onFailure { onFailed(); onClose() }
                }
            },
            // 重拍: back to the camera that took it; a library pick has nothing to retake.
            onRetake = { if (s.fromCamera) step = IntakeStep.Camera else onClose() },
        )
    }
}

/**
 * iOS's `CameraPicker` — one camera for every photo the app takes (拍照新增,
 * 頭像, 合集頭像), as iOS's `ImageIntake` shares one: full-bleed preview, the close mark in the safe area,
 * 相簿 bottom-left where the system camera keeps its roll, a square shutter,
 * 切換鏡頭 bottom-right, and pinch to zoom.
 */
@Composable
fun CaptureCameraWindow(onPhoto: (ByteArray) -> Unit, onLibrary: () -> Unit, onDismiss: () -> Unit) =
    TujiWindow(onDismiss = onDismiss, darkGround = true) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptics = rememberTujiHaptics()
        val controller = remember { CameraController() }
        var capturing by remember { mutableStateOf(false) }
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        // Asked when the camera is opened, not at launch.
        LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

        val closeLabel = stringResource(R.string.nav_close)
        val shutterLabel = stringResource(R.string.capture_shutter)
        Box(Modifier.fillMaxSize().background(TujiColor.Ink)) {
            if (granted) {
                CameraFrame(
                    controller = controller,
                    modifier = Modifier.fillMaxSize().pointerInput(controller) {
                        detectTransformGestures { _, _, zoom, _ -> controller.setZoom(controller.zoom * zoom) }
                    },
                )
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Box(
                    Modifier
                        .size(56.dp)
                        .tujiClickable(onClick = onDismiss)
                        .semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) { TujiGlyph.Close(size = 20.dp, tint = TujiColor.Paper) }
                Spacer(Modifier.weight(1f))
                if (!granted) {
                    // A camera that cannot start is not a state to sit in — the
                    // flow has a working second source, so offer it.
                    Column(
                        Modifier.fillMaxWidth().padding(TujiSpace.S4),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.capture_camera_denied), style = TujiType.body, color = TujiColor.Paper, textAlign = TextAlign.Center)
                        TujiButton(text = stringResource(R.string.capture_from_library), onClick = onLibrary)
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(start = TujiSpace.S4, end = TujiSpace.S4, bottom = TujiSpace.S5),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(stringResource(R.string.capture_from_library), onLibrary) { LibraryGlyph(22.dp) }
                        Spacer(Modifier.weight(1f))
                        // 72×72 square shutter with an ink inner frame — the
                        // square the rest of the app is built from.
                        Box(
                            Modifier
                                .size(72.dp)
                                .alpha(if (capturing) 0.5f else 1f)
                                .background(TujiColor.Paper)
                                .semantics { contentDescription = shutterLabel }
                                .tujiClickable(enabled = !capturing) {
                                    capturing = true
                                    haptics.firm()
                                    scope.launch {
                                        runCatching { PhotoCodec.captureJpeg(controller.takePhoto(context)) }
                                            .onSuccess(onPhoto)
                                            .onFailure { onDismiss() }
                                        capturing = false
                                    }
                                }
                                .padding(TujiBorder.Bw3 + 2.dp)
                                .border(TujiBorder.Bw3, TujiColor.Ink),
                        )
                        Spacer(Modifier.weight(1f))
                        ControlButton(stringResource(R.string.capture_flip), controller::flip) {
                            TujiGlyph.Refresh(size = 22.dp, tint = TujiColor.Paper)
                        }
                    }
                }
            }
        }
    }

@Composable
private fun ControlButton(label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier.size(56.dp).semantics { contentDescription = label }.tujiClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/** 從相簿選. Two stacked frames, the front one with a hill in it. */
@Composable
fun LibraryGlyph(size: Dp, tint: androidx.compose.ui.graphics.Color = TujiColor.Paper) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.08f)
        drawRect(tint, topLeft = Offset(w * 0.24f, h * 0.12f), size = Size(w * 0.68f, h * 0.56f), style = stroke)
        drawRect(tint, topLeft = Offset(w * 0.08f, h * 0.30f), size = Size(w * 0.68f, h * 0.58f), style = stroke)
        val hill = Path().apply {
            moveTo(w * 0.14f, h * 0.82f)
            lineTo(w * 0.36f, h * 0.56f)
            lineTo(w * 0.52f, h * 0.72f)
            lineTo(w * 0.60f, h * 0.64f)
            lineTo(w * 0.70f, h * 0.82f)
            close()
        }
        drawPath(hill, tint)
    }
}

private enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight }

/**
 * iOS's `ImageCropView`: drag four corners to box the subject, drag inside to
 * move the box. Any aspect — 拍照新增 boxes a subject for AI 辨識, and squaring
 * that would crop the thing being identified. The selection is kept as
 * fractions of the image, and confirming without dragging sends the whole frame.
 */
@Composable
private fun FreeformCropWindow(
    bitmap: Bitmap,
    onConfirm: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onRetake: () -> Unit,
) = TujiWindow(onDismiss = onRetake, darkGround = true) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var crop by remember(bitmap) { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var working by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize().background(TujiColor.Ink).statusBarsPadding().navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Inset so the corner handles at the image's edges keep clear of
            // the screen edges and stay draggable.
            val margin = with(density) { 20.dp.toPx() }
            val boxW = with(density) { maxWidth.toPx() } - margin * 2
            val boxH = with(density) { maxHeight.toPx() } - margin * 2
            val scale = min(boxW / bitmap.width, boxH / bitmap.height)
            val frame = Rect(
                Offset(margin + (boxW - bitmap.width * scale) / 2f, margin + (boxH - bitmap.height * scale) / 2f),
                Size(bitmap.width * scale, bitmap.height * scale),
            )
            val hit = with(density) { 44.dp.toPx() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(bitmap, frame) {
                    var grabbed: Corner? = null
                    var moving = false
                    detectDragGestures(
                        onDragStart = { at ->
                            val window = crop.on(frame)
                            grabbed = Corner.entries.firstOrNull { (window.corner(it) - at).getDistance() <= hit / 2f }
                            moving = grabbed == null && window.contains(at)
                        },
                        onDragEnd = { grabbed = null; moving = false },
                        onDragCancel = { grabbed = null; moving = false },
                    ) { change, drag ->
                        change.consume()
                        val dx = drag.x / frame.width
                        val dy = drag.y / frame.height
                        val corner = grabbed
                        crop = when {
                            corner != null -> crop.resized(corner, dx, dy)
                            moving -> crop.translate(
                                dx.coerceIn(-crop.left, 1f - crop.right),
                                dy.coerceIn(-crop.top, 1f - crop.bottom),
                            )
                            else -> crop
                        }
                    }
                },
            ) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt(), frame.height.roundToInt()),
                )
                val window = crop.on(frame)
                // Dim everything outside the window (even-odd punches the hole).
                drawPath(
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addRect(window)
                    },
                    TujiColor.Scrim,
                )
                // Thirds.
                for (i in 1..2) {
                    val x = window.left + window.width * i / 3f
                    val y = window.top + window.height * i / 3f
                    drawLine(TujiColor.Paper.copy(alpha = 0.3f), Offset(x, window.top), Offset(x, window.bottom), 0.5.dp.toPx())
                    drawLine(TujiColor.Paper.copy(alpha = 0.3f), Offset(window.left, y), Offset(window.right, y), 0.5.dp.toPx())
                }
                // 2dp 瞳黃 frame with thickened corners.
                drawRect(TujiColor.Current, window.topLeft, window.size, style = Stroke(TujiBorder.Bw2.toPx()))
                val arm = 12.dp.toPx()
                val thick = TujiBorder.Bw3.toPx() + 1.dp.toPx()
                Corner.entries.forEach { corner ->
                    val p = window.corner(corner)
                    val sx = if (corner == Corner.TopLeft || corner == Corner.BottomLeft) 1f else -1f
                    val sy = if (corner == Corner.TopLeft || corner == Corner.TopRight) 1f else -1f
                    drawLine(TujiColor.Current, p, Offset(p.x + sx * arm, p.y), thick, StrokeCap.Square)
                    drawLine(TujiColor.Current, p, Offset(p.x, p.y + sy * arm), thick, StrokeCap.Square)
                }
            }
        }
        // 重拍 / 使用 — the two things you can do with a frame you just took.
        Row(
            Modifier.fillMaxWidth().padding(start = TujiSpace.S4, end = TujiSpace.S4, top = TujiSpace.S3, bottom = TujiSpace.S2),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(TujiColor.Paper.copy(alpha = 0.12f))
                    .tujiClickable(enabled = !working, onClick = onRetake),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.capture_retake), style = TujiType.h3, color = TujiColor.Paper) }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(TujiColor.BrandPrimary)
                    .tujiClickable(enabled = !working) {
                        working = true
                        onConfirm(crop.left, crop.top, crop.right, crop.bottom)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (working) R.string.capture_crop_working else R.string.capture_crop_use),
                    style = TujiType.h3,
                    color = TujiColor.Ink,
                )
            }
        }
    }
}

/** Smallest crop side, as a fraction — keeps a handle from crossing the opposite edge. */
private const val MIN_SIDE = 0.12f

private fun Rect.on(frame: Rect) = Rect(
    frame.left + left * frame.width,
    frame.top + top * frame.height,
    frame.left + right * frame.width,
    frame.top + bottom * frame.height,
)

private fun Rect.corner(corner: Corner) = when (corner) {
    Corner.TopLeft -> topLeft
    Corner.TopRight -> topRight
    Corner.BottomLeft -> bottomLeft
    Corner.BottomRight -> bottomRight
}

private fun Rect.resized(corner: Corner, dx: Float, dy: Float): Rect {
    var l = left
    var t = top
    var r = right
    var b = bottom
    when (corner) {
        Corner.TopLeft -> { l = (l + dx).coerceIn(0f, r - MIN_SIDE); t = (t + dy).coerceIn(0f, b - MIN_SIDE) }
        Corner.TopRight -> { r = (r + dx).coerceIn(l + MIN_SIDE, 1f); t = (t + dy).coerceIn(0f, b - MIN_SIDE) }
        Corner.BottomLeft -> { l = (l + dx).coerceIn(0f, r - MIN_SIDE); b = (b + dy).coerceIn(t + MIN_SIDE, 1f) }
        Corner.BottomRight -> { r = (r + dx).coerceIn(l + MIN_SIDE, 1f); b = (b + dy).coerceIn(t + MIN_SIDE, 1f) }
    }
    return Rect(l, t, r, b)
}
