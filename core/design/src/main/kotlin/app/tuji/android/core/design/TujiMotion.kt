package app.tuji.android.core.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Motion — 3 durations and exactly one curve. Ported from
 * `Tuji/Core/Theme/Motion.swift`.
 *
 * The direction is carried by rhythm rather than decoration, so motion has to
 * be held down: without a ceiling it drifts toward the bouncy,
 * reward-animation register the design explicitly rules out.
 *
 * **One curve: easeOut. No spring, no bounce, no overshoot.** On Android that
 * also means not reaching for `spring()`, which is what most Compose sample
 * code hands you by default.
 *
 * Durations are `Int` milliseconds here where iOS spells them as fractional
 * seconds; 0.12s is 120ms and the two are the same number.
 */
object TujiMotion {
    /** State change: press, selection, chip inversion. */
    const val D1 = 120

    /** Enter/exit: sheets, navigation pushes, title fade-in. */
    const val D2 = 220

    /**
     * The only animation allowed to be *watched*: a progress value changing,
     * mastery climbing, a skeleton breathing.
     */
    const val D3 = 400

    /** Compose's `FastOutSlowIn` is a different curve; this is CSS/SwiftUI easeOut. */
    val EaseOut: Easing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

    fun <T> ease(durationMillis: Int): AnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    /**
     * [D3] motion is the only kind a user is meant to follow, so it is also the
     * only kind that must be suppressed rather than shortened when the system
     * animation scale is off — the value should jump, not race.
     *
     * Returns null to mean "do not animate", matching the iOS signature; call
     * sites treat null as `snapTo`.
     */
    fun <T> ease(durationMillis: Int, reduceMotion: Boolean): AnimationSpec<T>? =
        if (reduceMotion) null else ease(durationMillis)
}
