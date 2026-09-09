package app.tuji.android.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.tuji.android.core.study.MasteryLevel

/**
 * The mastery ladder's colours. This is the one place in the system where a
 * colour acts as a **category** rather than as a meaning — mastery is the most
 * important data in the app, so it gets a scale of its own.
 *
 * The ladder climbs through teal because mastery *is* 積累. An earlier one on
 * iOS ran grey → rose → amber → sky → green, which spent the alert colour on
 * 知道 and the "now" colour on 熟悉: two tiers of ordinary progress wearing
 * signals that mean something else entirely.
 *
 * 精通 is the only 墨底＋瞳字 pairing in the whole app. That is what gives it
 * weight — it does not need a colour of its own.
 */
val MasteryLevel.ground: Color
    get() = when (this) {
        MasteryLevel.NotLearned -> TujiColor.Paper3
        MasteryLevel.Know -> TujiColor.AccumulationSoft
        MasteryLevel.Familiar -> TujiColor.Accumulation
        MasteryLevel.Proficient -> TujiColor.AccumulationDeep
        MasteryLevel.Expert -> TujiColor.Ink
    }

val MasteryLevel.onGround: Color
    get() = when (this) {
        MasteryLevel.NotLearned -> TujiColor.Ink3
        MasteryLevel.Know -> TujiColor.AccumulationDeep
        MasteryLevel.Familiar, MasteryLevel.Proficient -> TujiColor.Paper
        MasteryLevel.Expert -> TujiColor.Current
    }

/**
 * A five-segment scale, for a grid of tiles.
 *
 * **No text, on purpose.** Every tile in a grid would repeat the same word
 * (未學, 未學, 未學…) and a pill would sit on top of the picture it describes. A
 * bar under the image lets a whole screenful be read at once — the amount of
 * teal *is* the answer.
 *
 * Because the shape carries what a word used to carry, and a bare shape is
 * nothing to a screen reader, [label] and [spoken] are not decoration: they are
 * the only thing keeping the information available at all. They arrive as
 * resolved strings because copy lives in the app module, where the localisation
 * gate can see it.
 */
@Composable
fun MasteryBadge(
    level: MasteryLevel,
    label: String,
    spoken: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(40.dp)
            .height(SEGMENT_HEIGHT)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = spoken
            },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(SEGMENTS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        when {
                            index >= level.filledSegments -> TujiColor.Paper3
                            // 全精通 fills in ink rather than teal — the same
                            // "this one is complete" inversion the rest of the
                            // system uses for a chosen state.
                            level == MasteryLevel.Expert -> TujiColor.Ink
                            else -> TujiColor.Accumulation
                        },
                    ),
            )
        }
    }
}

/**
 * Tier name, score and a bar — for 單字詳情, the one screen given over to a
 * single word, and so the only place mastery is worth spelling out in words.
 *
 * @param value what sits on the right: the number, or 尚無紀錄 when there is no
 *   row. One parameter rather than two states, because the caller is the only
 *   layer that can say either in the reader's language.
 * @param nextReview the countdown line, or null when nothing is scheduled.
 */
@Composable
fun MasteryBar(
    score: Int?,
    levelLabel: String,
    value: String,
    modifier: Modifier = Modifier,
    nextReview: String? = null,
) {
    val level = MasteryLevel.of(score)
    val ratio by animateFloatAsState(
        targetValue = ((score ?: 0).coerceIn(0, 100)) / 100f,
        animationSpec = TujiMotion.ease(TujiMotion.D3),
        label = "mastery",
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                levelLabel,
                style = TujiType.label,
                color = level.onGround,
                modifier = Modifier
                    .background(level.ground)
                    .padding(horizontal = TujiSpace.S2, vertical = 4.dp),
            )
            Box(Modifier.weight(1f))
            Text(
                value,
                style = if (score != null) TujiType.monoLabel else TujiType.label,
                color = if (score != null) TujiColor.Ink2 else TujiColor.Ink3,
            )
        }
        TujiProgressBar(
            progress = ratio.toDouble(),
            track = TujiColor.Paper3,
            fill = TujiColor.Accumulation,
        )
        if (nextReview != null) {
            Text(nextReview, style = TujiType.label, color = TujiColor.Ink3)
        }
    }
}

/** 2dp is unreadable at arm's length; the bar's height is its own value. */
private val SEGMENT_HEIGHT = 4.dp
private const val SEGMENTS = 5
