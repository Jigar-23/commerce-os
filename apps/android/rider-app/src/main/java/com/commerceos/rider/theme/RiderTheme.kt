package com.commerceos.rider.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RiderColors {
    val Primary = Color(0xFF16A34A) // Commerce OS Emerald Vitality
    val PrimaryVariant = Color(0xFF15803D)
    val PrimaryDark = Color(0xFF0B132B) // Midnight Navy
    val SpeedAccent = Color(0xFF4F46E5) // Electric Indigo SLA
    val PrimarySoft = Color(0xFFF0FDF4)
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF1F5F9)
    val TextPrimary = Color(0xFF0F172A)
    val TextMuted = Color(0xFF64748B)
    val Success = Color(0xFF16A34A)
    val SuccessSoft = Color(0xFFDCFCE7)
    val Warning = Color(0xFFF59E0B)
    val WarningSoft = Color(0xFFFEF3C7)
    val Danger = Color(0xFFE11D48)
    val Border = Color(0xFFE2E8F0)
}

@Composable
fun RiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RiderColors.Primary,
            secondary = RiderColors.PrimaryDark,
            tertiary = RiderColors.SpeedAccent,
            background = RiderColors.Background,
            surface = RiderColors.Surface
        ),
        content = content
    )
}
