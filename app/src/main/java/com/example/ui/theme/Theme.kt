package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = VermilionPrimary,
    onPrimary = Color.White,
    primaryContainer = VermilionContainer,
    onPrimaryContainer = VermilionOnContainer,
    secondary = JadeSecondary,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = JadeContainer,
    onSecondaryContainer = JadeOnContainer,
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF281B00),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    background = InkBackground,
    onBackground = CreamText,
    surface = InkSurface,
    onSurface = CreamText,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    surfaceContainerHigh = InkSurfaceHighest,
    outline = HairlineOutline,
    outlineVariant = HairlineOutline
)

private val LightColorScheme = lightColorScheme(
    primary = VermilionPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF410006),
    secondary = Color(0xFF007B72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB5F2EB),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFFD99000),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBB8),
    onTertiaryContainer = Color(0xFF2D1C00),
    background = PaperBackground,
    onBackground = InkText,
    surface = PaperSurface,
    onSurface = InkText,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkMutedText,
    surfaceContainerHigh = Color(0xFFE7E2D8),
    outline = Color(0xFFDCD8CE),
    outlineVariant = Color(0xFFE6E1D8)
)

val MangaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MangaLingoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = MangaShapes,
        content = content
    )
}
