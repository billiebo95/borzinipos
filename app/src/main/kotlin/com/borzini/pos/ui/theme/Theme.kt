package com.borzini.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.borzini.pos.data.prefs.ThemeMode

// BORZINI palette: cream background, dark coffee text, coffee-brown accents by default.
val CoffeeCreamBackground = Color(0xFFFBF3E7)
val CoffeeCreamSurface = Color(0xFFFFFDFA)
val CoffeeDarkText = Color(0xFF3B2A20)
val CoffeeBrownDefault = Color(0xFF6F4E37)
val CoffeeErrorRed = Color(0xFFB3261E)

private val DarkBackground = Color(0xFF201812)
private val DarkSurface = Color(0xFF2B211A)
private val DarkText = Color(0xFFF3E9DD)

fun parseAccentColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor("#$hex"))
}.getOrDefault(CoffeeBrownDefault)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = accent.copy(alpha = 0.7f),
    background = CoffeeCreamBackground,
    onBackground = CoffeeDarkText,
    surface = CoffeeCreamSurface,
    onSurface = CoffeeDarkText,
    error = CoffeeErrorRed,
)

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    secondary = accent.copy(alpha = 0.8f),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    error = Color(0xFFCF6679),
)

private fun scaledTypography(scale: Float): Typography {
    val base = Typography()
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}

@Composable
fun BorziniTheme(
    themeMode: ThemeMode,
    accentColorHex: String,
    textScale: Float,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = parseAccentColor(accentColorHex)
    val colorScheme = if (useDark) darkScheme(accent) else lightScheme(accent)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(textScale.coerceIn(0.85f, 1.3f)),
        content = content,
    )
}
