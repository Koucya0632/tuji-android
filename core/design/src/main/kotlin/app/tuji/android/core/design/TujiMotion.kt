package app.tuji.android.core.design

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
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
