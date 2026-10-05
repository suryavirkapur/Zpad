package com.zalthar.zpad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle

val themeNames = listOf("Classic", "Slate", "Gruvbox", "Nord", "Sepia")
val fontNames = listOf("Monospace", "Sans serif", "Serif")
val appearanceNames = listOf("System", "Light", "Dark")
fun noteFont(name: String) = when (name) {
    "Serif" -> FontFamily.Serif
    "Sans serif" -> FontFamily.SansSerif
    else -> FontFamily.Monospace
}

private data class Palette(val background: Long, val text: Long, val accent: Long, val muted: Long, val container: Long)

fun noteColors(theme: String, dark: Boolean): ColorScheme {
    val palette = when (theme) {
        "Slate" -> if (dark) Palette(0xFF161B22, 0xFFE6EDF3, 0xFF9CBFFF, 0xFFADB9C8, 0xFF242D3A)
            else Palette(0xFFF6F8FC, 0xFF202B3A, 0xFF325B91, 0xFF526174, 0xFFE3EAF5)
        "Gruvbox" -> if (dark) Palette(0xFF282828, 0xFFEBDBB2, 0xFFB8BB26, 0xFFD5C4A1, 0xFF3C3836)
            else Palette(0xFFFBF1C7, 0xFF3C3836, 0xFF626600, 0xFF665C54, 0xFFEBDBB2)
        "Nord" -> if (dark) Palette(0xFF2E3440, 0xFFECEFF4, 0xFF88C0D0, 0xFFD8DEE9, 0xFF3B4252)
            else Palette(0xFFECEFF4, 0xFF2E3440, 0xFF356677, 0xFF4C566A, 0xFFD8DEE9)
        "Sepia" -> if (dark) Palette(0xFF241E18, 0xFFF0E2C5, 0xFFD6B58D, 0xFFCCBAA0, 0xFF382E24)
            else Palette(0xFFF4ECD8, 0xFF46392C, 0xFF795548, 0xFF685846, 0xFFE8DCC5)
        else -> if (dark) Palette(0xFF151716, 0xFFE1E3DF, 0xFF9FD4AD, 0xFFBBC8BC, 0xFF28312A)
            else Palette(0xFFFAFBF8, 0xFF20231F, 0xFF2E6B40, 0xFF526354, 0xFFE3EBDF)
    }
    val background = Color(palette.background)
    val text = Color(palette.text)
    val accent = Color(palette.accent)
    val container = Color(palette.container)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = background,
        primaryContainer = container, onPrimaryContainer = text,
        secondary = accent, onSecondary = background,
        secondaryContainer = container, onSecondaryContainer = text,
        tertiary = accent, onTertiary = background,
        tertiaryContainer = container, onTertiaryContainer = text,
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = container, onSurfaceVariant = Color(palette.muted),
        surfaceTint = accent,
        surfaceDim = container, surfaceBright = background,
        // Graded containers so cards and fields sit gently above the page.
        surfaceContainerLowest = background, surfaceContainerLow = lerp(background, container, 0.5f),
        surfaceContainer = lerp(background, container, 0.75f), surfaceContainerHigh = container,
        surfaceContainerHighest = lerp(container, text, 0.06f),
        inverseSurface = text, inverseOnSurface = background, inversePrimary = background,
        outline = Color(palette.muted), outlineVariant = container,
    )
}

@Composable
fun ZpadTheme(theme: String = "Classic", font: String = "Monospace", dark: Boolean = false, textSize: Float = 16f, content: @Composable () -> Unit) {
    val scheme = noteColors(theme, dark)
    val family = noteFont(font)
    val base = Typography
    fun TextStyle.scaled() = copy(fontFamily = family, fontSize = fontSize * (textSize / 16f), lineHeight = lineHeight * (textSize / 16f))
    fun TextStyle.strong() = scaled().copy(fontWeight = FontWeight.SemiBold)
    MaterialTheme(colorScheme = scheme, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
    ), typography = base.copy(
        displayLarge = base.displayLarge.scaled(), displayMedium = base.displayMedium.scaled(), displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.strong(), headlineMedium = base.headlineMedium.strong(), headlineSmall = base.headlineSmall.strong(),
        titleLarge = base.titleLarge.strong(), titleMedium = base.titleMedium.strong(), titleSmall = base.titleSmall.strong(),
        bodyLarge = base.bodyLarge.scaled(), bodyMedium = base.bodyMedium.scaled(), bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(), labelMedium = base.labelMedium.scaled(), labelSmall = base.labelSmall.scaled()
    ), content = content)
}
