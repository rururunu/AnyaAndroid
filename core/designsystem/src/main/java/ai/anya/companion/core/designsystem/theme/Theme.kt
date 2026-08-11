package ai.anya.companion.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AnyaColors.AccentLight,
    onPrimary = Color.White,
    secondary = AnyaColors.MutedLight,
    onSecondary = Color.White,
    tertiary = AnyaColors.Info,
    background = AnyaColors.CanvasLight,
    onBackground = AnyaColors.InkLight,
    surface = AnyaColors.SurfaceLight,
    onSurface = AnyaColors.InkLight,
    surfaceVariant = AnyaColors.ListActiveLight,
    onSurfaceVariant = AnyaColors.MutedLight,
    outline = AnyaColors.BorderLight,
    error = AnyaColors.Danger,
)

private val DarkColors = darkColorScheme(
    primary = AnyaColors.AccentDark,
    onPrimary = AnyaColors.CanvasDark,
    secondary = AnyaColors.MutedDark,
    onSecondary = AnyaColors.CanvasDark,
    tertiary = AnyaColors.Info,
    background = AnyaColors.CanvasDark,
    onBackground = AnyaColors.InkDark,
    surface = AnyaColors.SurfaceDark,
    onSurface = AnyaColors.InkDark,
    surfaceVariant = AnyaColors.ListActiveDark,
    onSurfaceVariant = AnyaColors.MutedDark,
    outline = AnyaColors.BorderDark,
    error = AnyaColors.Danger,
)

@Composable
public fun AnyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AnyaTypography,
        content = content,
    )
}