package app.tuji.android.core.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A person's avatar: their picture in a circle, or the black cat's face.
 *
 * iOS's `ProfileAvatar`. **Only an https URL is a picture** — every other
 * stored value (an old preset key, an empty string, a signed-out guest)
 * collapses to the one built-in default, and a failed download does too, so a
 * broken link never shows as an empty grey disc.
 */
@Composable
fun ProfileAvatar(avatar: String?, size: Dp, modifier: Modifier = Modifier) {
    val url = avatar?.takeIf { it.startsWith("https://") }
    var failed by remember(url) { mutableStateOf(false) }
    val frame = modifier
        .size(size)
        .clip(CircleShape)
        .border(1.dp, TujiColor.Ink.copy(alpha = 0.08f), CircleShape)
    if (url != null && !failed) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = frame.background(TujiColor.BrandSecondary.copy(alpha = 0.12f)),
        )
    } else {
        MascotAvatar(size = size, modifier = modifier)
    }
}

/** The cat's face on a soft 棕 → 瞳黃 ground — the avatar nobody chose. */
@Composable
fun MascotAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(TujiColor.BrandSecondary.copy(alpha = 0.16f), TujiColor.BrandPrimary.copy(alpha = 0.26f)),
                ),
            )
            .border(1.dp, TujiColor.Ink.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(MascotPose.Face.res),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.88f),
        )
    }
}
