package com.commerceos.rider.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RiderColors {
    // Primary Brand Colors (Clean Commerce OS Emerald & White Aesthetic)
    val Primary = Color(0xFF059669) // Rich Emerald matching Customer App
    val PrimaryVariant = Color(0xFF047857)
    val PrimaryDark = Color(0xFF064E3B)
    val PrimarySoft = Color(0xFFECFDF5) // Soft mint green for chips & badges
    val PrimarySoftText = Color(0xFF065F46)

    // Dark Header & Captain Cockpit
    val DarkHeaderBg = Color(0xFF0F172A) // Deep Slate 900
    val DarkHeaderCard = Color(0xFF1E293B) // Slate 800

    // Backgrounds & Surfaces (Crisp White / Light Mode)
    val Background = Color(0xFFF8FAFC) // Slate 50 ultra clean background
    val Surface = Color(0xFFFFFFFF) // Crisp white card background
    val SurfaceSubtle = Color(0xFFF1F5F9) // Slate 100
    val CardBackground = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFE2E8F0) // Subtle clean border
    val Divider = Color(0xFFF1F5F9)

    // Typography
    val TextPrimary = Color(0xFF0F172A) // Deep Slate 900 for ultra sharp readability
    val TextSecondary = Color(0xFF475569) // Slate 600
    val TextMuted = Color(0xFF64748B) // Slate 500
    val TextLight = Color(0xFF94A3B8) // Slate 400

    // Accents & Status Indicators
    val SpeedAccent = Color(0xFF2563EB) // Royal Blue SLA
    val SpeedAccentSoft = Color(0xFFEFF6FF)
    val AccentBlue = Color(0xFF0284C7) // Sky 600
    val AccentBlueSoft = Color(0xFFE0F2FE)
    val AccentBlueText = Color(0xFF0369A1)

    val Success = Color(0xFF059669)
    val SuccessSoft = Color(0xFFECFDF5)
    val SuccessText = Color(0xFF065F46)

    val Warning = Color(0xFFD97706) // Amber 600
    val WarningSoft = Color(0xFFFEF3C7)
    val WarningText = Color(0xFF92400E)

    val Gold = Color(0xFFF59E0B)
    val GoldSoft = Color(0xFFFEF3C7)
    val GoldText = Color(0xFFB45309)

    val Danger = Color(0xFFDC2626) // Red 600
    val DangerSoft = Color(0xFFFEE2E2)
    val DangerText = Color(0xFF991B1B)

    val Border = Color(0xFFE2E8F0)
    val BorderSubtle = Color(0xFFF1F5F9)
}

@Composable
fun RiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RiderColors.Primary,
            onPrimary = Color.White,
            primaryContainer = RiderColors.PrimarySoft,
            onPrimaryContainer = RiderColors.PrimarySoftText,
            secondary = RiderColors.PrimaryDark,
            onSecondary = Color.White,
            tertiary = RiderColors.SpeedAccent,
            background = RiderColors.Background,
            onBackground = RiderColors.TextPrimary,
            surface = RiderColors.Surface,
            onSurface = RiderColors.TextPrimary,
            surfaceVariant = RiderColors.SurfaceSubtle,
            onSurfaceVariant = RiderColors.TextSecondary,
            outline = RiderColors.Border
        ),
        content = content
    )
}
