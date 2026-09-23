package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusEdgeLabel
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.StudyQueueItem
import androidx.compose.ui.res.stringResource

/**
 * The two pieces both completion screens draw, so 學新字 and 複習 celebrate a
 * session the same way. iOS keeps them together in `NewDoneView.swift` for the
 * same reason.
 */

/**
 * Ratings that could not be sent and were parked in the durable outbox.
 *
 * They replay on the next launch or foreground, so this is **reassurance, not
 * an error** — the alert wash says "unfinished", and the sentence says the app
 * is already handling it. Drawn only when there is something parked.
 */
@Composable
fun UnsyncedAnswersNotice(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        modifier
            .fillMaxWidth()
            .background(TujiColor.Alert.copy(alpha = 0.12f))
            .padding(TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.Top,
    ) {
        TujiGlyph.WifiOff(tint = TujiColor.Alert)
        Text(
            stringResource(R.string.study_unsynced_done, count),
            style = TujiType.label,
            color = TujiColor.Ink2,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The session's words, two to a row.
 *
 * **No card and no border.** iOS's note on this tile: it used to be a bordered
 * white box around a white-backdrop cut-out — two nested rectangles, both
 * lighter than the page, framing a picture that was already the only thing
 * there. The 紙2 square *is* the container, and the picture multiplies into it,
 * so what you see is the object and its name.
 *
 * @param mistakeCounts session mistakes by word id. A word with retries gets a
 *   答錯 badge instead of its gloss, so the recap points at what to watch — the
 *   gloss is the thing worth dropping, because it is the one line that is also
 *   on every other screen.
 */
@Composable
fun StudyWordGrid(
    items: List<StudyQueueItem>,
    showChinese: Boolean,
    modifier: Modifier = Modifier,
    mistakeCounts: Map<String, Int> = emptyMap(),
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        items.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                row.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(TujiColor.Paper2),
                        ) {
                            WordPicture(
                                url = item.word.imageUrl,
                                kind = WordImageKind.of(item.word.category),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                        }
                        Text(
                            item.word.word,
                            style = TujiType.h3,
                            color = TujiColor.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TujiSpace.S2),
                        )
                        val wrongs = mistakeCounts[item.word.id] ?: 0
                        if (wrongs > 0) {
                            TujiStatusEdgeLabel(
                                text = stringResource(R.string.study_wrong_times, wrongs),
                                edge = TujiColor.Alert,
                                modifier = Modifier.padding(top = TujiSpace.S1),
                            )
                        } else if (showChinese) {
                            Text(
                                item.word.chinese,
                                style = TujiType.bodySm,
                                color = TujiColor.Ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                // An odd count leaves the last tile half the row wide, not the
                // whole of it.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
