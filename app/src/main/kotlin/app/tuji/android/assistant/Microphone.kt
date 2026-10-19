package app.tuji.android.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The microphone, asked for the moment 跟讀 or 語音輸入 is first tapped — not at
 * launch, where a permission is asked before anyone knows what it is for.
 *
 * Returns a function that runs [granted] once the permission is held, asking
 * first when it is not; a refusal goes to [refused].
 */
@Composable
fun rememberMicrophone(refused: () -> Unit): (granted: () -> Unit) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val then = waiting
        waiting = null
        if (ok) then?.invoke() else refused()
    }
    return { granted ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            granted()
        } else {
            waiting = granted
            ask.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** 打開設定: the app's own page in system settings, where a refused permission is turned back on. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
