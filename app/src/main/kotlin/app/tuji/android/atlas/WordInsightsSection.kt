package app.tuji.android.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.WordInsightConfusable
import app.tuji.android.core.model.WordInsights

/**
 * 容易混淆・常見誤用・用法補充 under a word's details — iOS's
 * `WordInsightsSection`.
 *
 * Draws nothing until the server has something to say: under membership v1,
 * for 自製 and 物見 words, and for the many words with no insight at all, the
 * page looks exactly as it did before this section existed.
 */
@Composable
internal fun WordInsightsSection(
    insights: WordInsights?,
    /** Opens a confusable that is a catalogue word. Null draws every term as plain text. */
    onOpenWord: ((String) -> Unit)?,
    /** Whether a catalogue id is one this device can open — an id it cannot is not a link. */
    canOpen: (String) -> Boolean,
    /** Where a lock leads. Null draws the lock without a way through it. */
    onLocked: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (insights == null || insights.isEmpty) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        if (insights.confusables.isNotEmpty()) {
            Title(stringResource(R.string.word_insights_confusables))
            insights.confusables.forEach { item ->
                InsightCard {
                    Term(item, onOpenWord?.takeIf { item.catalogId?.let(canOpen) == true })
                    Text(item.distinction, style = TujiType.bodySm, color = TujiColor.Ink2)
                }
            }
        }
        if (insights.mistakes.isNotEmpty() || insights.lockedMistakesCount > 0) {
            Title(stringResource(R.string.word_insights_mistakes))
            insights.mistakes.forEach { item ->
                InsightCard {
                    Text(
                        item.wrong,
                        style = TujiType.bodySm.copy(textDecoration = TextDecoration.LineThrough),
                        color = TujiColor.Ink3,
                    )
                    Text(item.right, style = TujiType.bodySm, color = TujiColor.Ink)
                    Text(item.why, style = TujiType.bodySm, color = TujiColor.Ink2)
                }
            }
            if (insights.lockedMistakesCount > 0) Locked(insights.lockedMistakesCount, onLocked)
        }
        val usage = insights.usage
        if (usage != null) {
            Title(stringResource(R.string.word_insights_usage))
            InsightCard { Text(usage, style = TujiType.bodySm, color = TujiColor.Ink2) }
        } else if (insights.usageLocked) {
            Title(stringResource(R.string.word_insights_usage))
            Locked(1, onLocked)
        }
    }
}

@Composable
private fun Term(item: WordInsightConfusable, onOpen: ((String) -> Unit)?) {
    val id = item.catalogId
    if (onOpen == null || id == null) {
        Text(item.term, style = TujiType.bodyStrong, color = TujiColor.Ink)
        return
    }
    Row(
        Modifier.tujiClickable { onOpen(id) },
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.term, style = TujiType.bodyStrong, color = TujiColor.BrandSecondary)
        TujiGlyph.ArrowRight(size = 12.dp, tint = TujiColor.BrandSecondary)
    }
}

@Composable
private fun Locked(count: Int, onLocked: (() -> Unit)?) {
    InsightCard(Modifier.then(if (onLocked != null) Modifier.tujiClickable(onClick = onLocked) else Modifier)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TujiGlyph.Lock(tint = TujiColor.BrandSecondary)
            Text(
                stringResource(R.string.word_insights_locked, count),
                style = TujiType.label,
                color = TujiColor.BrandSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (onLocked != null) TujiGlyph.ArrowRight(size = 12.dp, tint = TujiColor.BrandSecondary)
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        style = TujiType.label.copy(letterSpacing = 2.sp),
        color = TujiColor.Ink3,
        modifier = Modifier.padding(top = TujiSpace.S2),
    )
}

@Composable
private fun InsightCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.25f))
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) { content() }
}
