package app.tuji.android

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import app.tuji.android.core.model.UiLanguage
import java.util.Locale

/**
 * Draw everything inside in [language], whatever the device is set to.
 *
 * 設定 → 語言 is an **account** setting on both platforms: iOS looks the key up
 * in an explicit `.lproj` bundle rather than trusting the device. Android's
 * equivalent is a `Context` whose configuration names the locale, because
 * `stringResource` reads its `Resources` from [LocalContext] — providing
 * [LocalConfiguration] alone only triggers recomposition and would leave every
 * string exactly as it was, which looks like the setting doing nothing.
 *
 * Not `AppCompatDelegate.setApplicationLocales`: that is the platform's answer
 * and it is the better one for an app that wants the choice to appear in
 * Android's own per-app language screen, but it needs `appcompat` on the
 * classpath, a manifest service, and an Activity recreation on every change.
 * This app has one Activity, no appcompat, and a setting that should apply
 * between one frame and the next.
 *
 * Scoped to the signed-in shell on purpose. The account is where the choice
 * lives, and the signed-out screens hand [LocalContext] to Credential Manager,
 * which wants the Activity it was given rather than a wrapper of it.
 *
 * **The context provided is still the Activity underneath.** It used to be
 * `createConfigurationContext(...)` itself, which is a sibling of the Activity
 * rather than a wrapper around it — so everything below that walks
 * `ContextWrapper.baseContext` looking for the Activity found nothing. That is
 * how `rememberLauncherForActivityResult` in 拍照做卡 crashed on every open
 * with "No ActivityResultRegistryOwner", while the note above had already
 * named the rule and applied it to only one of its callers. Permissions, the
 * camera, the photo picker and billing all look for the Activity the same way.
 * Only [Resources] come from the localised context.
 */
@Composable
fun ProvideAppLanguage(language: UiLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localised = remember(language, configuration, context) {
        val localisedConfig = Configuration(configuration).apply { setLocale(language.locale) }
        val localisedResources = context.createConfigurationContext(localisedConfig).resources
        localisedConfig to LocalisedContext(context, localisedResources)
    }
    CompositionLocalProvider(
        LocalConfiguration provides localised.first,
        LocalContext provides localised.second,
        content = content,
    )
}

/**
 * The Activity, answering [getResources] in another language.
 *
 * A wrapper rather than a configuration context so the base chain still ends
 * at the Activity — see [ProvideAppLanguage].
 */
private class LocalisedContext(base: Context, private val localised: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localised
}

/**
 * The language the *device* is in.
 *
 * Read from [LocalConfiguration] rather than `Locale.getDefault()`: the latter
 * is not observable state, so a composable that reads it keeps drawing the old
 * language after the user changes theirs — which lint says out loud, and which
 * is a real bug on a screen that lives as long as this app's root.
 *
 * It is the answer for every frame before the account's own is known: the
 * splash, the sign-in screens, and the first frames after sign-in.
 */
@Composable
fun rememberDeviceLanguage(): UiLanguage {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
        UiLanguage.of(locale.language, locale.script, locale.country)
    }
}
