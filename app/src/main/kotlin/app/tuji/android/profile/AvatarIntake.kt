package app.tuji.android.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.tuji.android.R
import app.tuji.android.capture.CameraController
import app.tuji.android.capture.CameraFrame
import app.tuji.android.core.design.CropSquare
import app.tuji.android.core.design.SquareCrop
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private sealed interface Intake {
    data object Sources : Intake
    data object Camera : Intake
    data object Decoding : Intake
    data class Cropping(val bitmap: Bitmap) : Intake
    data object Failed : Intake
}

/**
 * 更換頭像 — iOS's `ImageIntake` for a profile photo: where it comes from, a
 * square crop under a circle, and a re-encode. Nothing is uploaded here; the
 * bytes go to the form, and 儲存 sends them.
 *
 * Every step is its own window, so the shell's back arrow and the system back
 * gesture close the step rather than the screen behind it.
 */
/** The preview mask. The saved image is square either way. */
enum class CropMask { Circle, Square }

@Composable
fun AvatarIntake(
    hasCustomAvatar: Boolean,
    onImage: (ByteArray) -> Unit,
    onUseDefault: () -> Unit,
    onClose: () -> Unit,
    title: String = stringResource(R.string.avatar_change),
    mask: CropMask = CropMask.Circle,
    encode: suspend (Bitmap, CropSquare) -> ByteArray = PhotoCodec::profileJpeg,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Intake>(Intake.Sources) }

    fun decode(block: suspend () -> Bitmap) {
        step = Intake.Decoding
        scope.launch {
            step = runCatching { block() }.fold(onSuccess = { Intake.Cropping(it) }, onFailure = { Intake.Failed })
        }
    }

    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) onClose() else decode { PhotoCodec.decode(context, uri) }
    }

    when (val s = step) {
        Intake.Sources -> SourceSheet(
            title = title,
            hasCustomAvatar = hasCustomAvatar,
            onCamera = { step = Intake.Camera },
            onLibrary = { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onUseDefault = { onUseDefault(); onClose() },
            onDismiss = onClose,
        )
        Intake.Camera -> CameraWindow(
            onPhoto = { bytes -> decode { PhotoCodec.decode(bytes) } },
            onDismiss = onClose,
        )
        Intake.Decoding -> TujiWindow(onDismiss = {}) {
            Box(Modifier.fillMaxSize().background(TujiColor.Ink))
        }
        is Intake.Cropping -> CropWindow(
            bitmap = s.bitmap,
            mask = mask,
            onConfirm = { square ->
                step = Intake.Decoding
                scope.launch {
                    runCatching { encode(s.bitmap, square) }
                        .onSuccess { onImage(it); onClose() }
                        .onFailure { step = Intake.Failed }
                }
            },
            onCancel = onClose,
        )
        Intake.Failed -> TujiWindow(onDismiss = onClose) {
            Column(
                Modifier.fillMaxSize().background(TujiColor.Ink).padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S4, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.avatar_load_failed), style = TujiType.body, color = TujiColor.Paper, textAlign = TextAlign.Center)
                TujiButton(text = stringResource(R.string.atlas_back), onClick = onClose)
            }
        }
    }
}

@Composable
private fun SourceSheet(
    title: String,
    hasCustomAvatar: Boolean,
    onCamera: () -> Unit,
    onLibrary: () -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) = TujiWindow(onDismiss = onDismiss) {
    Box(
        Modifier.fillMaxSize().background(TujiColor.Scrim).tujiClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                // Swallows taps, so touching the sheet does not dismiss it
                // through the scrim underneath.
                .tujiClickable {}
                .navigationBarsPadding()
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(title, style = TujiType.h3, color = TujiColor.Ink)
            listOfNotNull(
                R.string.avatar_camera to onCamera,
                R.string.avatar_library to onLibrary,
                (R.string.avatar_use_default to onUseDefault).takeIf { hasCustomAvatar },
            ).forEach { (label, action) ->
                Text(
                    stringResource(label),
                    style = TujiType.body,
                    color = TujiColor.Ink,
                    modifier = Modifier.fillMaxWidth().tujiClickable(onClick = action).padding(vertical = TujiSpace.S2),
                )
            }
            TujiButton(
                text = stringResource(R.string.cancel),
                style = TujiButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The same viewfinder 拍照收字 uses, for one frame. */
@Composable
private fun CameraWindow(onPhoto: (ByteArray) -> Unit, onDismiss: () -> Unit) = TujiWindow(onDismiss = onDismiss) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { CameraController() }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(TujiColor.Ink)) {
        if (granted) {
            CameraFrame(controller = controller, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = TujiSpace.S6)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(TujiColor.Paper)
                    .tujiClickable {
                        scope.launch { runCatching { controller.takePhoto(context) }.onSuccess(onPhoto) }
                    },
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.capture_permission), style = TujiType.body, color = TujiColor.Paper, textAlign = TextAlign.Center)
                TujiButton(text = stringResource(R.string.capture_permission_grant), onClick = { ask.launch(Manifest.permission.CAMERA) })
            }
        }
        Text(
            stringResource(R.string.cancel),
            style = TujiType.bodyStrong,
            color = TujiColor.Paper,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .tujiClickable(onClick = onDismiss)
                .padding(TujiSpace.S4),
        )
    }
}

/**
 * iOS's `AvatarCropView`: drag to move, pinch to zoom, a circle over a square.
 * The circle is only the preview — the saved image is the square around it.
 */
@Composable
private fun CropWindow(bitmap: Bitmap, mask: CropMask, onConfirm: (CropSquare) -> Unit, onCancel: () -> Unit) = TujiWindow(onDismiss = onCancel) {
    val image: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    var zoom by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(TujiColor.Ink).statusBarsPadding().navigationBarsPadding()) {
        // One number for the drawing and for 使用照片, so what is saved is
        // exactly what was on screen. The toolbar takes 72dp of the height.
        val viewport = with(density) {
            min(min(maxWidth.toPx() - 48.dp.toPx(), maxHeight.toPx() - 168.dp.toPx()), 360.dp.toPx())
                .coerceAtLeast(120.dp.toPx())
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap, viewport) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom = SquareCrop.clampZoom(zoom * zoomChange)
                                val (x, y) = SquareCrop.clampOffset(w, h, viewport, zoom, dx + pan.x, dy + pan.y)
                                dx = x
                                dy = y
                            }
                        },
                ) {
                    val scale = SquareCrop.baseScale(w, h, viewport) * zoom
                    val drawn = Size(w * scale, h * scale)
                    val centre = Offset(size.width / 2f + dx, size.height / 2f + dy)
                    drawImage(
                        image = image,
                        dstOffset = IntOffset((centre.x - drawn.width / 2f).roundToInt(), (centre.y - drawn.height / 2f).roundToInt()),
                        dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
                    )
                    val window = Rect(Offset(size.width / 2f, size.height / 2f), viewport / 2f)
                    val shade = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        if (mask == CropMask.Circle) addOval(window) else addRect(window)
                    }
                    drawPath(shade, Color.Black.copy(alpha = 0.58f))
                    if (mask == CropMask.Circle) {
                        drawOval(Color.White, topLeft = window.topLeft, size = window.size, style = Stroke(width = 2.dp.toPx()))
                    } else {
                        drawRect(Color.White, topLeft = window.topLeft, size = window.size, style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Text(
                    stringResource(R.string.avatar_crop_hint),
                    style = TujiType.label,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = TujiSpace.S4)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S1),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = TujiSpace.S4),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.cancel),
                    style = TujiType.bodyStrong,
                    color = Color.White,
                    modifier = Modifier.tujiClickable(onClick = onCancel).padding(vertical = TujiSpace.S2),
                )
                Text(
                    stringResource(R.string.avatar_crop_reset),
                    style = TujiType.bodyStrong,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.tujiClickable { zoom = 1f; dx = 0f; dy = 0f }.padding(vertical = TujiSpace.S2),
                )
                Spacer(Modifier.weight(1f))
                // Sized to its words, as on iOS: a full-width bar here would
                // crowd 取消 and 重設 into the corner.
                Row(
                    Modifier
                        .height(48.dp)
                        .background(TujiColor.BrandPrimary)
                        .tujiClickable { onConfirm(SquareCrop.square(w, h, viewport, zoom, dx, dy)) }
                        .padding(horizontal = TujiSpace.S4),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TujiGlyph.Check(size = 14.dp, tint = TujiColor.Ink)
                    Text(stringResource(R.string.avatar_use_photo), style = TujiType.bodyStrong, color = TujiColor.Ink)
                }
            }
        }
    }
}
