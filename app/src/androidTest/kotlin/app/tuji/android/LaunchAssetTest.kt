package app.tuji.android

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.tuji.android.core.design.LockupEntrance
import app.tuji.android.core.design.TujiBrandLockup
import app.tuji.android.core.design.TujiColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The native launch image, rendered from the composable it has to hand over to.
 *
 * `windowBackground` is what the window shows before Compose has drawn a single
 * frame — about **1.1 seconds** on this project's debug build, measured on a
 * LIO-L29. A flat colour there means the app opens on an empty page and the
 * mark arrives late; iOS has never done that, because `UILaunchScreen` names
 * `LaunchLockupPeekStart`, which is the *exact* first frame of the entrance.
 *
 * iOS produces that asset with `ImageRenderer` over the same SwiftUI view
 * (`LaunchAssetTests.swift`). This is the same move: draw the real
 * [TujiBrandLockup] at [LockupEntrance.Start] and write the pixels out, so the
 * static frame and the animation that continues from it cannot drift apart.
 *
 * Not a pass/fail test — a generator, which is why it is skipped by the normal
 * `./gradlew build`. Run it only when the mark changes:
 *
 * ```
 * ANDROID_SERIAL=<a 480dpi device> ./scripts/launch-asset.sh
 * ```
 */
class LaunchAssetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renderLaunchLockupPeekStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.densityDpi
        // The asset is checked in at xxhdpi, so rendering it anywhere else
        // would bake the wrong scale into the file. Fail loudly rather than
        // quietly writing a 2x image into a 3x folder.
        assertEquals("render this on a 480dpi (xxhdpi) device", 480, density)

        compose.setContent {
            Box(
                Modifier
                    .testTag(TAG)
                    // Rendered *on* paper rather than on transparency: the
                    // layer-list paints the same paper underneath, so the two
                    // meet with no seam and the mark's antialiased edges are
                    // blended against the colour they will actually sit on.
                    .background(TujiColor.Paper)
                    .size(width = 232.dp, height = 230.dp),
            ) {
                TujiBrandLockup(entrance = LockupEntrance.Start)
            }
        }
        compose.waitForIdle()

        val bitmap = compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
        assertEquals(232 * 3, bitmap.width)
        assertEquals(230 * 3, bitmap.height)

        val out = File(context.getExternalFilesDir(null), "launch_lockup_peek_start.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("LAUNCH_ASSET=${out.absolutePath}")
    }
}

private const val TAG = "launch-lockup"
