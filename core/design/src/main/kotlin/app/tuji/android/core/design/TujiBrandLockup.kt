package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「Tuji.」 with the cat behind it — the app's mark, and the first thing anyone
 * sees.
 *
 * Ported from `Tuji/Components/TujiBrandLockup.swift`. Only one piece of it is
 * an image (`mascot-peek`); the portal and the card are drawn, so the numbers
 * are copied rather than re-derived: an ellipse of 176×48 at a specific offset
 * is artwork, and "close enough" is visible.
 *
 * **This is where the radius rule has its exception.** [TujiRadius] is zero
 * everywhere in the app, but the wordmark card is 24dp round on both platforms
 * — because it is a drawn mark, not a UI surface. The same applies to the ink
 * plate behind it, which is a *drawn* shadow rather than an elevation.
 */
@Composable
fun TujiBrandLockup(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    // peek: visibleHeightRatio 0.89 × 150dp cat, less a 16dp overlap, is how far
    // down the card sits — the paws have to land *on* it.
    val catSize = 150.dp
    val lift = catSize * MascotPose.Peek.visibleHeightRatio - 16.dp

    Box(
        modifier.size(width = 232.dp * scale, height = 230.dp * scale),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                // `requiredSize`, not `size`: the outer frame is already the
                // scaled-down box, so a plain `size` gets clamped to it *before*
                // graphicsLayer scales — which shrank the card twice and left it
                // at 179dp against iOS's 195pt.
                .requiredSize(width = 232.dp, height = 230.dp)
                // The whole mark scales, not just the box around it. Sizing the
                // outer frame alone left every inner dimension at full size —
                // the card measured 204dp against iOS's 195pt, which is the
                // kind of "nearly right" that only a measurement finds.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Portal(Modifier.offset(y = lift - 30.dp))

            MascotFigure(
                pose = MascotPose.Peek,
                size = catSize,
                modifier = Modifier.offset(y = 0.dp),
            )

            WordmarkCard(Modifier.offset(y = lift))
        }
    }
}

/** The hole the cat leans out of. */
@Composable
private fun Portal(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 176.dp, height = 48.dp)
            .drawBehind {
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(
                            TujiColor.BrandSecondary.copy(alpha = 0.78f),
                            TujiColor.BrandSecondary,
                        )
                    ),
                    size = Size(size.width, size.height),
                    style = Fill,
                )
            }
    )
}

@Composable
private fun WordmarkCard(modifier: Modifier = Modifier) {
    // The one place GenSenRounded's own Latin is the face we *want*.
    //
    // iOS sets the wordmark in `Font.system(size: 54, weight: .black, design:
    // .rounded)` — SF Rounded, which Android has no equivalent of; Roboto is
    // not round and Plus Jakarta is not either. GenSenRounded's Latin is a
    // rounded Source Sans derivative, and it is already bundled. Everywhere
    // else in the app it is the wrong answer and ADR-0003 exists to keep it out
    // (see TujiTypeface); a drawn logotype is the exception, because here the
    // face *is* the artwork rather than a step on a scale.
    val rounded = remember { FontFamily(Font(R.font.gensenrounded2tw_b)) }

    Box(modifier.size(width = 224.dp, height = 78.dp), contentAlignment = Alignment.Center) {
        // A drawn plate, not an elevation. 紙與墨 has no shadow token; this is
        // part of the mark.
        Box(
            Modifier
                .offset(y = 5.dp)
                .size(width = 220.dp, height = 76.dp)
                .background(TujiColor.Ink.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
        )
        Box(
            Modifier
                .size(width = 224.dp, height = 78.dp)
                .background(TujiColor.BrandSecondary, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tuji",
                    fontFamily = rounded,
                    fontSize = 54.sp,
                    letterSpacing = (-2.5).sp,
                    color = TujiColor.BrandPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    ".",
                    fontFamily = rounded,
                    fontSize = 54.sp,
                    letterSpacing = (-2.5).sp,
                    color = TujiColor.Alert,
                )
            }
        }
    }
}

