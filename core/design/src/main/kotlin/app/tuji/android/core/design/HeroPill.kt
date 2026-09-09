package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Which of the two grounds a hero button gets. */
enum class HeroPillRole { Primary, Secondary }

/**
 * A button on the ink hero.
 *
 * **Both grounds are clearly lit, and the hierarchy is carried by hue, not by
 * brightness.** The secondary used to be a translucent paper wash on ink — a
 * dark grey block on a near-black block, which is what "switched off" looks
 * like everywhere else in this app. It sat two opacity steps from the genuinely
 * disabled state and read the same, so 學新字 looked untappable on every day
 * that had reviews due, which is most days.
 *
 * So: 瞳黃 means 現在 and marks the recommended action; paper is the same
 * "available but not selected" ground the 圖鑑 chips use. An outline was the
 * other option and the system forbids it — a resting element with a border is
 * a bug.
 */
@Composable
fun HeroPill(
    text: String,
    role: HeroPillRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val ground: Color = when {
        !enabled -> TujiColor.Paper.copy(alpha = 0.12f)
        role == HeroPillRole.Primary ->
            if (pressed) TujiColor.CurrentDeep else TujiColor.Current
        else -> if (pressed) TujiColor.Paper3 else TujiColor.Paper
    }
    val ink = if (enabled) TujiColor.Ink else TujiColor.Paper.copy(alpha = 0.4f)

    Box(
        modifier
            .background(ground)
            .tujiClickable(enabled = enabled, interactionSource = interaction, onClick = onClick)
            .padding(TujiSpace.S3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TujiType.h3, color = ink)
    }
}
