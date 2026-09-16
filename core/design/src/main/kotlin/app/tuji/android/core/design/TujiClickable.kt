package app.tuji.android.core.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

/**
 * The same, plus a long press.
 *
 * Separate rather than a nullable parameter on [tujiClickable]: a long press
 * costs every tap a delay before it is delivered as a tap, and 145 call sites
 * should not pay that for the handful that want one.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tujiClickable(
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() }
    combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
