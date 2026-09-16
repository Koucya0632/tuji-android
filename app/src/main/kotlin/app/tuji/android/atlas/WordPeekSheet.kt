package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind
import app.tuji.android.core.model.asHeadworded
import app.tuji.android.core.model.headwordDisplay
import app.tuji.android.core.catalog.CardsSourceRules

/**
 * A look at one word without leaving the grid — what a long press raises.
 *
 * The picture at the size the tile could not give it, the headword with its
 * reading, the gloss, 書籤, and the way in. Everything here is already in the
 * row the grid is drawing, so it opens in the frame the finger let go on;
 * there is no request behind it and nothing to wait for.
 *
 * **No 發音 button, unlike iOS's.** Saying a word means choosing between its
 * recording and a synthetic voice, and that choice lives in the view models
 * that own a player. A third copy of it in a composable is how the three drift
 * apart — and the full page, which has the button, is the one tap this sheet is
 * offering anyway.
 */
@Composable
internal fun WordPeekSheet(
    word: Word,
    session: TargetLanguage,
    showChinese: Boolean,
    bookmarked: Boolean,
    onBookmark: (() -> Unit)?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    TujiWindow(onDismiss = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TujiColor.Scrim)
                .tujiClickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TujiColor.Paper)
                    // The 3dp top edge is a selection indicator, which is the
                    // one thing that weight means in this system.
                    .padding(top = TujiBorder.Bw3)
                    // Swallows taps, so a tap on the sheet does not reach the
                    // scrim underneath and dismiss it.
                    .tujiClickable {}
                    .navigationBarsPadding()
                    .padding(TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(TujiColor.Paper2),
                ) {
                    WordPicture(
                        url = word.imageUrl,
                        kind = WordImageKind.of(word.category),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                    ) {
                        val head = word.asHeadworded().headwordDisplay(session)
                        if (head is HeadwordDisplay.Ruby) {
                            FuriganaHeadword(segments = head.segments)
                        } else {
                            Text(word.word, style = TujiType.h2, color = TujiColor.Ink)
                        }
                        // Kana over the word, or a line under it — never both,
                        // which is the one thing `headwordDisplay` exists to
                        // decide.
                        if (head is HeadwordDisplay.Line) {
                            Text(head.text, style = TujiType.label, color = TujiColor.Ink3)
                        }
                        if (showChinese) {
                            word.chinese?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = TujiType.bodySm, color = TujiColor.Ink2)
                            }
                        }
                    }
                    // No star on a 自製 card: 書籤 filters the catalogue, which
                    // has never heard of it, so the mark would go nowhere.
                    if (onBookmark != null && !CardsSourceRules.isCustom(word.id)) {
                        TujiIconButton(
                            label = stringResource(R.string.word_bookmark),
                            onClick = onBookmark,
                        ) {
                            TujiGlyph.Star(filled = bookmarked, tint = TujiColor.Ink)
                        }
                    }
                }
                TujiButton(
                    text = stringResource(R.string.gloss_see_detail),
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
