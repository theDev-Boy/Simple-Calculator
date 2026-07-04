package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    background = DarkBackground,
    surface = DarkBackground,
    onPrimary = DarkTextPrimary,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    error = DestructiveRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightBackground,
    onPrimary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    error = DestructiveRed
)

// Reusable custom modifier for Glassmorphism as requested by the specifications
// NOTE: We do NOT use Modifier.blur(20.dp) directly on the container modifier chain because in Compose,
// .blur() blurs the container's contents (all child texts, icons, switches, etc.), making them completely unreadable.
// Instead, we use elegant, high-contrast, semi-transparent backgrounds with refined shadows and subtle, precise
// borders to achieve a gorgeous, functional frosted-glass depth effect while keeping all text and elements sharp.
fun Modifier.glassmorphic(
    darkMode: Boolean,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 8.dp
): Modifier {
    val shadowShape = RoundedCornerShape(cornerRadius)
    val bgColor = if (darkMode) {
        // Deep elegant translucent card background for premium contrast
        Color(0xFF16161A).copy(alpha = 0.85f)
    } else {
        // Soft bright translucent card background
        Color(0xFFFFFFFF).copy(alpha = 0.92f)
    }
    val borderColor = if (darkMode) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    
    return this
        .shadow(elevation = elevation, shape = shadowShape)
        .background(
            color = bgColor,
            shape = shadowShape
        )
        .border(
            width = 0.8.dp,
            color = borderColor,
            shape = shadowShape
        )
}

@Composable
fun SimpleCalculatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
            
            // True immersive full screen: hide status bar and bottom navigation bar
            windowInsetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
