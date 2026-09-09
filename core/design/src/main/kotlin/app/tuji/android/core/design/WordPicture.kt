package app.tuji.android.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.tuji.android.core.model.WordImageKind
import coil3.compose.AsyncImage

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
 */
@Composable
fun WordPicture(
    url: String?,
    kind: WordImageKind,
    modifier: Modifier = Modifier,
    inset: Dp = TujiSpace.S3,
    ground: Color = TujiColor.Paper2,
) {
    Box(modifier) {
        AsyncImage(
            model = url,
            contentDescription = null,
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
