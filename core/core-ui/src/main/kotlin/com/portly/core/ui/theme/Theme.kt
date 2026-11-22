package com.portly.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.core.view.WindowCompat
import com.portly.core.model.ThemeMode

object PortlyBrand {
    val SunsetGold = Color(0xFFFFD18B)
    val SunsetOrange = Color(0xFFFF8277)
    val SunsetCoral = Color(0xFFFF546B)
    val SunsetRed = Color(0xFFFF453A)

    val SunsetGradient = Brush.linearGradient(
        colors = listOf(SunsetGold, SunsetOrange, SunsetCoral),
    )
    val ButtonGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF6B4A), SunsetRed),
    )
    val StatusActiveGreen = Color(0xFF10B981)
    val StatusActiveGreenBg = Color(0x1F10B981)
}

private val PortlyLightColors = lightColorScheme(
    primary = Color(0xFFFF546B), // Portly Sunset Coral
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFECEB), // Soft Coral Tint
    onPrimaryContainer = Color(0xFF881424),
    secondary = Color(0xFFFF8C42), // Warm Sunset Amber
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFF0DC),
    onSecondaryContainer = Color(0xFF7A340F),
    tertiary = Color(0xFFFF7E5F), // Radiant Coral
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val PortlyDarkColors = darkColorScheme(
    primary = Color(0xFFFF6B4A), // Radiant Sunset Orange for Dark Mode
    onPrimary = Color(0xFF2C0A0E),
    primaryContainer = Color(0xFF45151B),
    onPrimaryContainer = Color(0xFFFFD5D8),
    secondary = Color(0xFFFFA880), // Warm Golden Amber
    onSecondary = Color(0xFF331608),
    secondaryContainer = Color(0xFF4A2514),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFFF8277),
    background = Color(0xFF0B0F17), // Cosmic Obsidian
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF121824), // Frosted Dark Slate
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1A2233),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF070A10),
    surfaceContainerLow = Color(0xFF0D121D),
    surfaceContainer = Color(0xFF131A28),
    surfaceContainerHigh = Color(0xFF1C2538),
    surfaceContainerHighest = Color(0xFF26324A),
    outline = Color(0xFF2C3954),
    outlineVariant = Color(0xFF1E283C),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val PortlyAmoledColors = PortlyDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF10131B),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF06080E),
    surfaceContainer = Color(0xFF0B0E16),
    surfaceContainerHigh = Color(0xFF141822),
    surfaceContainerHighest = Color(0xFF1C2230),
    outline = Color(0xFF22293A),
    outlineVariant = Color(0xFF161A26),
)

private val PortlyTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun PortlyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDark
            insetsController.isAppearanceLightNavigationBars = !useDark
        }
    }

    val context = LocalContext.current
    val baseScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> PortlyDarkColors
        else -> PortlyLightColors
    }

    val colorScheme = if (themeMode == ThemeMode.AMOLED) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF09090B),
            surfaceContainer = Color(0xFF101014),
            surfaceContainerHigh = Color(0xFF18181D),
            surfaceContainerHighest = Color(0xFF202026),
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PortlyTypography,
        content = content,
    )
}
