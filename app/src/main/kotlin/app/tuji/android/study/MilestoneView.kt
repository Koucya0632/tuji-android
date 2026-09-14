package app.tuji.android.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.MascotFigure
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.study.MilestoneLine

/**
 * A streak milestone — iOS's `MilestoneView`. It wins over the session summary
 * when a session crosses one: it happens a few times a year, and the summary
 * is still in the numbers on 今日.
 */
@Composable
fun MilestoneView(streak: Int, topPadding: Dp, bottomPadding: Dp, onFinish: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Ink)
            .padding(top = topPadding, bottom = bottomPadding + TujiSpace.S5)
            .padding(horizontal = TujiSpace.S4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        MascotFigure(pose = MascotPose.Cheer, size = 88.dp)
        Column(
            Modifier.fillMaxWidth().padding(top = TujiSpace.S3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            Text(stringResource(R.string.milestone_title, streak), style = TujiType.h2, color = TujiColor.Paper, textAlign = TextAlign.Center)
            Text("$streak", style = TujiType.display, color = TujiColor.AccumulationSoft)
            Text(stringResource(R.string.milestone_label), style = TujiType.label, color = TujiColor.Paper.copy(alpha = 0.6f))
            Text(
                stringResource(
                    when (MilestoneLine.of(streak)) {
                        MilestoneLine.Month -> R.string.milestone_month
                        MilestoneLine.Hundred -> R.string.milestone_hundred
                        MilestoneLine.Year -> R.string.milestone_year
                        MilestoneLine.KeepGoing -> R.string.milestone_keep_going
                    },
                ),
                style = TujiType.body,
                color = TujiColor.Paper,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = TujiSpace.S4),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(56.dp).background(TujiColor.Current).tujiClickable(onClick = onFinish),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.milestone_continue), style = TujiType.h3, color = TujiColor.Ink)
        }
    }
}
