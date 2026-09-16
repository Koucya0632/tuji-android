package app.tuji.android.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The four taps this app answers with — ported from `StudyHaptics.swift`.
 *
 * Touch is part of the design system for the same reason colour and motion
 * are: 紙與墨 answers "did that register" by changing the ground, and on a
 * phone the other half of that answer is felt rather than seen. Before this
 * existed the whole app buzzed in exactly two places (the tab bar and the
 * prompt), so every right answer, wrong answer and bookmark landed silently.
 *
 * **An interface rather than calls to `LocalHapticFeedback` at each site**,
 * because the two study flows fire these from their view models — the moment an
 * answer *resolves* is not a moment any composable can name — and a view model
 * cannot read a composition local. It also lets a test assert the sequence,
 * which is the half that can be wrong without a screenshot showing it.
 *
 * There is no `prime()`. iOS holds and warms its generators because a
 * `UIImpactFeedbackGenerator` built at the tap has to wake the Taptic Engine
 * first and the buzz lands late; Android's vibrator has no such warm-up to ask
 * for, and [HapticFeedback] is a view-level call with nothing to hold.
 */
interface TujiHaptics {
    /** A tap that landed: a right answer, a rating, a tile placed, a button. */
    fun soft()

    /** A tap that did not: a wrong option, a ruled-out pick. */
    fun firm()

    /** A stage cleared. */
    fun success()

    /** A stage missed. */
    fun warning()

    /** Nowhere to buzz — tests, previews, and the view models' default. */
    object None : TujiHaptics {
        override fun soft() = Unit
        override fun firm() = Unit
        override fun success() = Unit
        override fun warning() = Unit
    }
}

/**
 * The haptics of the window this composable is in.
 *
 * **On minSdk 29 this still buzzes**, which is not the usual story for a
 * constant introduced in API 30 — see the blur that silently draws nothing
 * below 31. `Confirm` and `Reject` map to `HapticFeedbackConstants.CONFIRM`
 * (16) and `REJECT` (17), both API 30, but Compose performs them through
 * `ViewCompat`, whose `HapticFeedbackConstantsCompat` table substitutes below
 * 30: CONFIRM → VIRTUAL_KEY, REJECT → LONG_PRESS. Both are real effects, so a
 * 29 device feels a shorter version of the same distinction rather than
 * nothing at all.
 */
@Composable
fun rememberTujiHaptics(): TujiHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { ViewHaptics(feedback) }
}

/**
 * iOS's four impacts, in the closest thing the platform names.
 *
 * `ContextClick` is the light tick the tab bar was already using, `LongPress`
 * is the heavier thud a miss deserves, and Confirm/Reject exist on Android as
 * first-class "that worked" / "that did not" effects — which is exactly what
 * `UINotificationFeedbackGenerator` means by success and warning.
 */
private class ViewHaptics(private val feedback: HapticFeedback) : TujiHaptics {
    override fun soft() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    override fun firm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    override fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    override fun warning() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}
