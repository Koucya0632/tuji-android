package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.core.model.ReadingLine
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind

/**
 * One word in a grid: its picture, its headword, its reading, its gloss.
 *
 * **There is no card.** The tile used to be a bordered rectangle, which made
 * the most prominent thing in a grid of words the *box* — not the picture and
 * not the word. Content sits directly on the paper.
 *
 * This owns the *container*: the square, the ground, the clip. How the picture
 * meets it belongs to [WordPicture], which every screen showing a word's
 * picture shares. The square lives here and not on the image for a reason —
 * put the aspect ratio on the picture and each one's own proportions leak
 * through, so wide images sit short, tall ones sit long, and the two columns
 * stop lining up.
 */
@Composable
fun WordTile(
    word: Word,
    modifier: Modifier = Modifier,
    /** Fixed image height. Null — the grid case — makes the picture square. */
    height: Dp? = null,
    showLabel: Boolean = true,
    /**
     * The mastery scale, drawn between the picture and the word. A slot rather
     * than a score, because resolving a tier into copy needs the app module's
     * strings and this one has none — and because the tile has no business
     * knowing whether the caller wants mastery shown at all.
     */
    badge: (@Composable () -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (height != null) Modifier.height(height) else Modifier.aspectRatio(1f))
                .background(TujiColor.Paper2),
        ) {
            WordPicture(
                url = word.imageUrl,
                kind = WordImageKind.of(word.category),
                inset = TujiSpace.S2,
                modifier = Modifier.fillMaxSize(),
            )
            SourceDot(word.category, Modifier.align(Alignment.TopEnd))
        }

        if (showLabel) {
            badge?.invoke()
            Text(
                word.word,
                style = TujiType.h3,
                color = TujiColor.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            ReadingLine.shown(word.reading ?: word.pronunciation, word.word)?.let {
                Text(
                    it,
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            word.chinese?.let {
                Text(
                    it,
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * An 8dp square marking where the word came from.
 *
 * 墨 = you made it, 積累 = you took it in from 物見 — teal means accumulation
 * and a saved word is exactly that. Dictionary words carry no mark, because
 * "the dictionary" is the default and does not need saying.
 */
@Composable
private fun SourceDot(category: String?, modifier: Modifier = Modifier) {
    val tint = when (category) {
        "custom" -> TujiColor.Ink
        "community" -> TujiColor.Accumulation
        else -> return
    }
    Box(modifier.padding(TujiSpace.S2).size(8.dp).background(tint))
}
