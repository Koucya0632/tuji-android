package app.tuji.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * Whether the settings on screen are the account's.
 *
 * Until they are, the controls that change them are shown but inert — see
 * `SettingsWrite` for what a change made against the seed values did.
 */
enum class SettingsReadiness {
    Ready,
    Loading,
    Failed,
    ;

    companion object {
        fun of(isGuest: Boolean, loaded: Boolean, loadFailed: Boolean): SettingsReadiness = when {
            isGuest || loaded -> Ready
            loadFailed -> Failed
            else -> Loading
        }
    }
}

/** The line that says why the controls below it do nothing, and the way out. */
@Composable
internal fun SettingsReadinessLine(
    readiness: SettingsReadiness,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** The rows' own inset in 設定; the themes grid already pads its content. */
    inset: Dp = TujiSpace.S4,
) {
    when (readiness) {
        SettingsReadiness.Ready -> Unit
        // Stays a line of words, not a skeleton. Nothing is *arriving* here —
        // the controls below are already on screen and already greyed; this
        // says why. A block in their place would claim the settings page had
        // not loaded, which is the opposite of what happened.
        SettingsReadiness.Loading -> Text(
            stringResource(R.string.settings_loading),
            style = TujiType.label,
            color = TujiColor.Ink3,
            modifier = modifier.padding(horizontal = inset, vertical = TujiSpace.S3),
        )
        SettingsReadiness.Failed -> Row(
            modifier.fillMaxWidth().padding(horizontal = inset, vertical = TujiSpace.S1),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_load_failed),
                style = TujiType.label,
                color = TujiColor.Alert,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.height(44.dp).tujiClickable(onClick = onRetry), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.retry), style = TujiType.bodySmStrong, color = TujiColor.BrandSecondary)
            }
        }
    }
}
