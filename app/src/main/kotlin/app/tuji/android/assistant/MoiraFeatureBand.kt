package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * Moira's capabilities, in the order offered on 今日 — iOS's
 * `AssistantHomeFeature`.
 *
 * There used to be a whole 助手 page around these: a 236dp cat, a greeting,
 * four rows. It went the way iOS's did — 今日 already greets the user by name,
 * so the page's only own content was the four rows, and they moved onto 今日.
 */
enum class AssistantHomeFeature { Lookup, Grammar, Story, TopicChat }

private val AssistantHomeFeature.title: Int
    get() = when (this) {
        AssistantHomeFeature.Lookup -> R.string.assistant_feature_lookup
        AssistantHomeFeature.Grammar -> R.string.assistant_feature_grammar
        AssistantHomeFeature.Story -> R.string.assistant_feature_story
        AssistantHomeFeature.TopicChat -> R.string.assistant_feature_topic_chat
    }

private val AssistantHomeFeature.detail: Int
    get() = when (this) {
        AssistantHomeFeature.Lookup -> R.string.assistant_feature_lookup_detail
        AssistantHomeFeature.Grammar -> R.string.assistant_feature_grammar_detail
        AssistantHomeFeature.Story -> R.string.assistant_feature_story_detail
        AssistantHomeFeature.TopicChat -> R.string.assistant_feature_topic_chat_detail
    }

/**
 * The four ways in, as one full-width band — iOS's `MoiraFeatureBand`.
 *
 * Side by side there is no room for each one's line of detail (it would make
 * the cells three times as tall), and the cat sits right above them saying
 * whose they are. The detail is not dropped: TalkBack reads it as what a
 * double tap does.
 */
@Composable
fun MoiraFeatureBand(
    onOpen: (AssistantHomeFeature) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 試用點數 left on a free account ([app.tuji.android.account.CheckInStore.Snapshot.trialPoints]).
     * The tag is the only place a free account learns it can try these: no popup, no extra card.
     */
    trialPoints: Int? = null,
) {
    Box(modifier.fillMaxWidth()) {
        FeatureCells(onOpen)
        if (trialPoints != null) {
            Text(
                stringResource(R.string.assistant_trial_points, trialPoints),
                style = TujiType.label,
                color = TujiColor.Ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -TujiSpace.S3, y = (-10).dp)
                    .clip(CircleShape)
                    .background(TujiColor.Paper)
                    .border(TujiBorder.Bw1, TujiColor.Rule, CircleShape)
                    .padding(horizontal = TujiSpace.S2, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FeatureCells(onOpen: (AssistantHomeFeature) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(TujiColor.Paper2)
            .border(TujiBorder.Bw1, TujiColor.Rule),
    ) {
        AssistantHomeFeature.entries.forEachIndexed { index, feature ->
            if (index > 0) {
                Box(Modifier.width(TujiBorder.Bw1).fillMaxHeight().background(TujiColor.Rule))
            }
            val title = stringResource(feature.title)
            val detail = stringResource(feature.detail)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 88.dp)
                    .tujiClickable { onOpen(feature) }
                    .clearAndSetSemantics {
                        contentDescription = title
                        role = Role.Button
                        onClick(label = detail) { onOpen(feature); true }
                    }
                    .padding(horizontal = TujiSpace.S1, vertical = TujiSpace.S3),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterVertically),
            ) {
                when (feature) {
                    AssistantHomeFeature.Lookup -> TujiGlyph.Lookup(size = 22.dp)
                    AssistantHomeFeature.Grammar -> TujiGlyph.GrammarBook(size = 22.dp)
                    AssistantHomeFeature.Story -> TujiGlyph.StoryBook(size = 22.dp)
                    AssistantHomeFeature.TopicChat -> TujiGlyph.ChatBubbles(size = 22.dp)
                }
                Text(title, style = TujiType.label, color = TujiColor.Ink, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/**
 * 點數明細's way in — iOS's `AssistantCreditLink`. The can rides 今日's top
 * row beside the flame: both are "your counters", one of days, one of points.
 */
@Composable
fun AssistantCreditLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.credit_statement_title)
    Box(
        modifier
            .clip(CircleShape)
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.3f), CircleShape)
            .tujiClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(TujiSpace.S2 - 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.credit_can), null, Modifier.size(18.dp), tint = TujiColor.Ink)
    }
}
