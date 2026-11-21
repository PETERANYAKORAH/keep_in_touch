package app.kinkeep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette: dark green + white, tonal greens, and one warm accent family for "needs attention".
object KinColors {
    val GreenDeep = Color(0xFF0B3D2E)
    val Green700 = Color(0xFF1B5E46)
    val Green500 = Color(0xFF2E7D5B)
    val Green300 = Color(0xFF6FB594)
    val Green200 = Color(0xFFA8D5BE)
    val Green50 = Color(0xFFE8F3ED)
    val White = Color(0xFFFFFFFF)
    val NightBg = Color(0xFF0A1F17)
    val NightSurface = Color(0xFF10291F)
    val AccentAmber = Color(0xFFB45F06) // roughly 4.6:1 on white; verify with a contrast checker
    val AccentRust = Color(0xFF8A3B12)
    val Slate = Color(0xFF5E7268)
}

private val LightScheme = lightColorScheme(
    primary = KinColors.GreenDeep, onPrimary = KinColors.White,
    primaryContainer = KinColors.Green50, onPrimaryContainer = KinColors.GreenDeep,
    secondary = KinColors.Green500, onSecondary = KinColors.White,
    background = KinColors.White, onBackground = Color(0xFF0F1F18),
    surface = KinColors.White, onSurface = Color(0xFF0F1F18),
    tertiary = KinColors.AccentAmber, onTertiary = KinColors.White,
)

private val DarkScheme = darkColorScheme(
    primary = KinColors.Green200, onPrimary = KinColors.GreenDeep,
    primaryContainer = KinColors.Green700, onPrimaryContainer = KinColors.White,
    secondary = KinColors.Green300, onSecondary = KinColors.GreenDeep,
    background = KinColors.NightBg, onBackground = KinColors.White,
    surface = KinColors.NightSurface, onSurface = KinColors.White,
    tertiary = Color(0xFFE8A04A), onTertiary = KinColors.NightBg,
)

@Composable
fun KinKeepTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
}
