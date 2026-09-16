package app.tuji.android.core.design

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

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

    /**
     * SwiftUI's `.easeOut`, which is the CSS curve `cubic-bezier(0, 0, 0.58, 1)`.
     *
     * Compose's `FastOutSlowInEasing` is Material's own curve and lands
     * somewhere else entirely. This was also a quadratic `1-(1-t)²` for a
     * while, which is the shape people reach for when they mean "ease out" —
     * but it is not the same curve: half way through, the quadratic is at
     * 0.75 and this is at 0.70.
     */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /**
     * SwiftUI's `.easeInOut` — `cubic-bezier(0.42, 0, 0.58, 1)`.
     *
     * Not part of the three-durations-one-curve rule, and deliberately so: it
     * exists because three places on iOS ask for it by name (the offline
     * banner, 學新字's stage dots, and the reveal card's turn under Reduce
     * Motion), and this app's job is to be the same app.
     */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /**
     * `FiniteAnimationSpec` rather than `AnimationSpec`, because the transition
     * APIs — `fadeIn`, `Crossfade`, `SizeTransform` — will only take a spec
     * that is known to end. Every spec here is a tween, so it always was one.
     */
    fun <T> ease(durationMillis: Int): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    /**
     * [D3] motion is the only kind a user is meant to follow, so it is also the
     * only kind that must be suppressed rather than shortened when the system
     * animation scale is off — the value should jump, not race.
     *
     * Returns null to mean "do not animate", matching the iOS signature; call
     * sites treat null as `snapTo`.
     */
    fun <T> ease(durationMillis: Int, reduceMotion: Boolean): FiniteAnimationSpec<T>? =
        if (reduceMotion) null else ease(durationMillis)

    fun <T> easeInOut(durationMillis: Int): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseInOut)

    /**
     * SwiftUI's `.spring(duration:bounce:)`, in Compose's parameters.
     *
     * The two platforms describe the same physical spring from opposite ends.
     * SwiftUI takes a *perceptual duration* and a bounce; Compose takes a
     * stiffness and a damping ratio. Converting is two lines of algebra rather
     * than a judgement call, so it lives here once instead of being eyeballed
     * per call site — see [SwiftSpring].
     *
     * @param durationSeconds SwiftUI's `duration:`, in seconds, because that is
     *   what the iOS source says and a translation that renames its inputs is
     *   a translation nobody can check.
     */
    fun <T> spring(durationSeconds: Float, bounce: Float = 0f): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = SwiftSpring.dampingRatio(bounce),
            stiffness = SwiftSpring.stiffness(durationSeconds),
        )
}

/**
 * The algebra behind [TujiMotion.spring], kept apart so it can be tested
 * without a composition.
 *
 * SwiftUI defines `Spring(duration:bounce:)` as a unit-mass spring with
 * `stiffness = (2π / duration)²` and `damping = 4π(1 - bounce) / duration`.
 * Compose's damping *ratio* is `damping / (2√(stiffness · mass))`, and with
 * mass 1 that whole expression collapses to `1 - bounce`: the duration cancels
 * out. So a SwiftUI bounce is a Compose damping ratio read backwards, and
 * nothing else about the spring depends on it.
 */
object SwiftSpring {
    /** `(2π / duration)²`. */
    fun stiffness(durationSeconds: Float): Float {
        val omega = (2.0 * Math.PI / durationSeconds).toFloat()
        return omega * omega
    }

    /**
     * `1 - bounce`, clamped to Compose's no-bounce ceiling.
     *
     * A bounce of 0 is critically damped, which is what SwiftUI gives you when
     * `bounce:` is left out — five of iOS's eight springs are this, and they do
     * not bounce at all. They are still springs: a critically damped spring
     * arrives with a different velocity profile than any tween.
     */
    fun dampingRatio(bounce: Float): Float =
        (1f - bounce).coerceIn(0.05f, Spring.DampingRatioNoBouncy)
}

/**
 * Whether the system is asking for less motion.
 *
 * Android has no single "Reduce Motion" switch the way iOS does. What it has is
 * **移除動畫** under Accessibility, which sets the three animation scales to 0 —
 * so reading the animator scale is the honest question to ask, and 0 is the
 * only value that means it.
 *
 * Observed rather than read once. It is a *system* setting: a user who turns it
 * on mid-session does so because something on screen is making them unwell, and
 * an app that only notices at its next cold start has answered them tomorrow.
 * (The same defect shape as reading the device locale once into a `val` — the
 * setting moves, the composable does not.)
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var reduced by remember(resolver) { mutableStateOf(animatorScaleIsZero(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animatorScaleIsZero(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animatorScaleIsZero(resolver: android.content.ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
