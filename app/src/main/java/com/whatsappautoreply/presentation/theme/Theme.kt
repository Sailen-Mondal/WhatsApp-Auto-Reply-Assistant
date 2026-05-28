package com.whatsappautoreply.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── WhatsApp-inspired palette ───────────────────────────────────────────────

// Greens
private val WaGreenDark   = Color(0xFF00A884)  // WhatsApp teal (dark mode)
private val WaGreenLight  = Color(0xFF008069)  // WhatsApp teal (light mode)
private val WaAccentGreen = Color(0xFF25D366)  // Vibrant action green

// Dark mode surfaces (WhatsApp dark bg)
private val DarkBg        = Color(0xFF111B21)
private val DarkSurface   = Color(0xFF1F2C34)
private val DarkSurface2  = Color(0xFF202C33)
private val DarkText      = Color(0xFFE9EDEF)
private val DarkSubtext   = Color(0xFF8696A0)
private val DarkContainer = Color(0xFF025144)

// Light mode surfaces
private val LightBg       = Color(0xFFF0F2F5)
private val LightSurface  = Color(0xFFFFFFFF)
private val LightText     = Color(0xFF111B21)
private val LightSubtext  = Color(0xFF54656F)
private val LightContainer= Color(0xFFD0F4F0)

private val DarkColorScheme = darkColorScheme(
    primary            = WaGreenDark,
    onPrimary          = Color.Black,
    primaryContainer   = DarkContainer,
    onPrimaryContainer = Color(0xFFB7F0E0),
    secondary          = WaAccentGreen,
    onSecondary        = Color.Black,
    secondaryContainer = Color(0xFF003D2E),
    onSecondaryContainer = Color(0xFFB2F4D0),
    tertiary           = Color(0xFF6EC6F5),
    onTertiary         = Color.Black,
    tertiaryContainer  = Color(0xFF004D6E),
    onTertiaryContainer= Color(0xFFCDE7FF),
    error              = Color(0xFFFF6B6B),
    onError            = Color.Black,
    background         = DarkBg,
    onBackground       = DarkText,
    surface            = DarkSurface,
    onSurface          = DarkText,
    surfaceVariant     = DarkSurface2,
    onSurfaceVariant   = DarkSubtext,
    outline            = Color(0xFF3D5460),
    outlineVariant     = Color(0xFF2A3942)
)

private val LightColorScheme = lightColorScheme(
    primary            = WaGreenLight,
    onPrimary          = Color.White,
    primaryContainer   = LightContainer,
    onPrimaryContainer = Color(0xFF00201A),
    secondary          = WaAccentGreen,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFCCF5DD),
    onSecondaryContainer = Color(0xFF00210E),
    tertiary           = Color(0xFF0066A0),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFCCE5FF),
    onTertiaryContainer= Color(0xFF001D36),
    error              = Color(0xFFD32F2F),
    onError            = Color.White,
    background         = LightBg,
    onBackground       = LightText,
    surface            = LightSurface,
    onSurface          = LightText,
    surfaceVariant     = Color(0xFFE9ECEF),
    onSurfaceVariant   = LightSubtext,
    outline            = Color(0xFF8696A0),
    outlineVariant     = Color(0xFFD0D8DC)
)

@Composable
fun WhatsAppAutoReplyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: transparent status bar, let compose draw behind it
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
