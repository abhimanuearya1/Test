package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter

// Material 3 colour roles generated from the seed colour #00696B (teal).
// The same colours are used for the window background in res/values/colors.xml.

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F3),
    onPrimaryContainer = Color(0xFF004F51),
    inversePrimary = Color(0xFF80D4D6),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF324B4C),
    tertiary = Color(0xFF4C5F7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF354863),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceTint = Color(0xFF00696B),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFECF2F1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4FBFA),
    surfaceDim = Color(0xFFD5DBDB),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDDE4E3),
    surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D6),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF1F3),
    inversePrimary = Color(0xFF00696B),
    secondary = Color(0xFFB0CCCC),
    onSecondary = Color(0xFF1B3435),
    secondaryContainer = Color(0xFF324B4C),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFB4C8E9),
    onTertiary = Color(0xFF1D314C),
    tertiaryContainer = Color(0xFF354863),
    onTertiaryContainer = Color(0xFFD4E3FF),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C8),
    surfaceTint = Color(0xFF80D4D6),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3232),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4949),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3A),
    surfaceDim = Color(0xFF0E1415),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainerLowest = Color(0xFF090F0F),
)

/** Light or dark colours, following the phone's setting. */
@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** The formatter for the currency currently in use. Provided at the top of the app. */
val LocalMoney = staticCompositionLocalOf<MoneyFormatter> { error("LocalMoney was not provided") }
