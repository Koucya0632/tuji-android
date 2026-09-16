package app.tuji.android.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.core.model.WordImageKind
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * How a word's picture meets its container — the one place that decides it.
 *
 * Ten screens were hand-writing the same three-part decision (fit or fill, how
 * far to inset, whether to blend) and every one of them got the third part
 * wrong in the same direction: a catalogue cut-out is a **white-backdrop**
 * product shot, and drawn as-is it puts a white rectangle on warm paper —
 * a box around the one thing the screen is asking about.
 *
 * White × any ground is that ground, so multiplying makes the backdrop vanish
 * and leaves the object its own colour. A photograph has no backdrop to remove
 * and multiplying one would only darken it, which is why [WordImageKind] is a
 * parameter and not an assumption.
 *
 * **Why a tint rather than a layer blend.** SwiftUI can say `.blendMode(
 * .multiply)` and have it composite against whatever is behind. Compose has no
 * modifier for that, but it does not need one here: the ground is a flat
 * colour, and multiplying by a constant is the same arithmetic. That is also
 * the constraint to remember — [ground] must be the colour actually behind the
 * picture, or the backdrop reappears as a rectangle in the wrong shade.
 *
 * **A picture that has not arrived is a [TujiImagePlaceholder], not a hole.**
 * The container is already the right size, so the skeleton holds the grid still
 * while the pictures land; before this it was blank paper and every tile popped
 * in. One that never arrives gets the camera glyph — "there is no picture" and
 * "the picture is coming" are different answers, and blank was giving both.
 */
@Composable
fun WordPicture(
    url: String?,
    kind: WordImageKind,
    modifier: Modifier = Modifier,
    inset: Dp = TujiSpace.S3,
    ground: Color = TujiColor.Paper2,
    /** The "no picture" glyph, sized to its container: a 48dp thumb wants less. */
    glyphSize: Dp = 24.dp,
) {
    var state: AsyncImagePainter.State by remember(url) {
        mutableStateOf(AsyncImagePainter.State.Empty)
    }
    Box(modifier) {
        if (state is AsyncImagePainter.State.Loading) TujiImagePlaceholder()
        if (url.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TujiGlyph.Camera(size = glyphSize, tint = TujiColor.Ink3)
            }
        }
        AsyncImage(
            model = url,
            contentDescription = null,
            onState = { state = it },
            // Fitted whole, never cropped. 拍照新增 lets the user box the
            // subject at any aspect ratio on purpose, and the server stores
            // what they framed; filling the container's shape would take that
            // back and cut the thing being identified.
            contentScale = ContentScale.Fit,
            colorFilter = if (kind == WordImageKind.Cutout) {
                ColorFilter.tint(ground, BlendMode.Multiply)
            } else {
                null
            },
            modifier = Modifier.fillMaxSize().padding(inset),
        )
    }
}
