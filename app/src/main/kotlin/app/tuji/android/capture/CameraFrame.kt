package app.tuji.android.capture

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * The viewfinder.
 *
 * A thin wrapper over CameraX rather than a photo picker: 自製圖鑑 is about the
 * thing in front of you, and a gallery picker invites screenshots — which the
 * recogniser can read but which are not what this feature is for.
 */
@Composable
fun CameraFrame(controller: CameraController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }, modifier = modifier) {
        controller.bind(context, lifecycleOwner, previewView)
    }
}

/**
 * Owns the CameraX use cases.
 *
 * Held by the screen rather than the view model: it needs a `Context` and a
 * lifecycle, and putting either in the view model is what makes the flow
 * untestable — the whole reason [CaptureViewModel] takes bytes and not a camera.
 */
class CameraController {

    private var capture: ImageCapture? = null

    private companion object {
        const val TAG = "TujiCamera"
    }

    fun bind(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: PreviewView,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageCapture = ImageCapture.Builder()
                // Latency over resolution: the server downscales anyway, and a
                // shutter that feels slow is the thing people notice.
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            capture = imageCapture
            // Not swallowed. A binding that fails leaves a black rectangle with
            // a shutter button on it, and every reason it can fail — no camera,
            // a selector nothing matches, a lifecycle already destroyed — looks
            // identical from the outside.
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture,
                )
            }.onFailure { error ->
                Log.e(TAG, "camera bind failed; available=" + runCatching {
                    provider.availableCameraInfos.map { info ->
                        CameraSelector.LENS_FACING_BACK.let { _ ->
                            info.lensFacing
                        }
                    }
                }.getOrDefault(emptyList()), error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Take one frame as JPEG bytes. */
    suspend fun takePhoto(context: Context): ByteArray = suspendCoroutine { cont ->
        val imageCapture = capture
        if (imageCapture == null) {
            cont.resumeWithException(IllegalStateException("camera not bound"))
            return@suspendCoroutine
        }
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bytes = image.use { it.toJpegBytes() }
                    cont.resume(bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }
}

/** The frame's bytes. `ImageCapture` hands back JPEG in a single plane. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val out = ByteArrayOutputStream(buffer.remaining())
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    out.write(bytes)
    return out.toByteArray()
}
