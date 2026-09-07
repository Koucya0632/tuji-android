package app.tuji.android.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The one place a Tuji screen gets its type, colour and shape from.
 *
 * Material 3 is present but held down, not adopted: its colour scheme is filled
 * with 紙與墨 values so a stray `MaterialTheme.colorScheme.surface` cannot open a
 * hole in the design, and its whole [Shapes] set is pinned to
 * [TujiRadius.Shape0] so a stock `Button` or `Card` cannot introduce a rounded
 * corner. iOS makes the same argument the other way round — zeroing the radius
 * is what stops the app reading as a stock platform app.
 *
 * There is no dark scheme yet. iOS 1.1.2 has none either: 紙與墨 is a light
 * design and a mechanical inversion of it would not be the same design. It is
 * on the M6 打磨 list as real work, not a switch to flip, so this deliberately
 * ignores [isSystemInDarkTheme] rather than pretending.
 */
val LocalTujiTypography: ProvidableCompositionLocal<TujiTypography> =
    staticCompositionLocalOf { error("TujiTypography requested outside TujiTheme") }

val LocalTujiFace: ProvidableCompositionLocal<TujiFace> =
    staticCompositionLocalOf { TujiFace.TW }

@Composable
fun TujiTheme(
    face: TujiFace = TujiFace.TW,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val typography = remember(context, face) {
        TujiTypography(
            regular = TujiTypefaces.family(context, LatinFace.Regular, face),
            semiBold = TujiTypefaces.family(context, LatinFace.SemiBold, face),
            bold = TujiTypefaces.family(context, LatinFace.Bold, face),
            extraBold = TujiTypefaces.family(context, LatinFace.ExtraBold, face),
            mono = TujiTypefaces.mono,
        )
    }

    val colors = remember {
        lightColorScheme(
            primary = TujiColor.Current,
            onPrimary = TujiColor.Ink,
            secondary = TujiColor.Accumulation,
            onSecondary = TujiColor.Paper,
            background = TujiColor.Paper,
            onBackground = TujiColor.Ink,
            surface = TujiColor.Paper,
            onSurface = TujiColor.Ink,
            surfaceVariant = TujiColor.Paper2,
            onSurfaceVariant = TujiColor.Ink2,
            outline = TujiColor.Rule,
            outlineVariant = TujiColor.Rule,
            error = TujiColor.Alert,
            onError = TujiColor.Paper,
            scrim = TujiColor.Scrim,
        )
    }

    val shapes = remember {
        Shapes(
            extraSmall = TujiRadius.Shape0,
            small = TujiRadius.Shape0,
            medium = TujiRadius.Shape0,
            large = TujiRadius.Shape0,
            extraLarge = TujiRadius.Shape0,
        )
    }

    CompositionLocalProvider(
        LocalTujiTypography provides typography,
        LocalTujiFace provides face,
        LocalContentColor provides TujiColor.Ink,
    ) {
        MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
    }
}

/** `TujiType.body` reads better at a call site than the CompositionLocal does. */
val TujiType: TujiTypography
    @Composable get() = LocalTujiTypography.current
