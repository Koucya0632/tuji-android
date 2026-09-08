package app.tuji.android.core.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Tappable, with no ripple.
 *
 * The ripple is Material's answer to "was that press registered", and 紙與墨
 * answers it by changing the ground instead. Passing `indication = null` at each
 * call site is how that rule gets forgotten at the thirty-first one, so it lives
 * here.
 */
fun Modifier.tujiClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() }
    clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}
