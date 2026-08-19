package com.commerceos.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.config.LocalClientConfiguration

/**
 * CommerceOS design tokens. Feature screens consume these tokens so brand/business meaning stays in
 * one place and dynamically adapts to the active ClientTheme.
 */
object CommerceColors {
    // Reference quick-commerce palette cues from example_app (Blinkit/Sushi tokens).
    val QuickGreen: Color = Color(0xFF0DA314)
    val QuickGreenDark: Color = Color(0xFF0C831F)
    val SpeedYellow: Color = Color(0xFFE9BE3A)
    val HomeGrid: Color = Color(0xFFF4F5F7)
    val SushiGrey: Color = Color(0xFFEFEFEF)
    val SushiInk: Color = Color(0xFF1C1C1C)

    // Brand / primary
    val Primary: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.primaryColorHex, Color(0xFF16A34A))
    
    val Secondary: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.secondaryColorHex, Color(0xFF0B132B))
    
    val PrimaryDark: Color = QuickGreenDark
    val PrimarySoft: Color = Color(0xFFEAF8E8)
    val OnPrimary: Color = Color.White

    // Success / positive
    val Success: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.successColorHex, Color(0xFF16A34A))
    val SuccessDark: Color = QuickGreenDark
    val SuccessSoft: Color = Color(0xFFEAF8E8)
    val SuccessBorder: Color = Color(0xFFBEEBBF)

    // Warning / pending
    val Warning: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.warningColorHex, Color(0xFFF59E0B))
    val WarningSoft: Color = Color(0xFFFFF4CD)

    // Danger / error
    val Danger: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.errorColorHex, Color(0xFFE11D48))
    val DangerDark: Color = Color(0xFFBE123C)
    val DangerSoft: Color = Color(0xFFFFE4E6)
    val DangerContainer: Color = Color(0xFFFFF1F2)

    // Info / informational
    val Info: Color = Color(0xFF4F46E5)
    val InfoSoft: Color = Color(0xFFEEF2FF)
    val InfoContainer: Color = Color(0xFFEEF2FF)
    val InfoBorder: Color = Color(0xFFC7D2FE)

    val PrimaryContainer: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.primaryColorHex, Color(0xFF16A34A)).copy(alpha = 0.12f)

    // Surfaces
    val Background: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.backgroundColorHex, HomeGrid)
    val Surface: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.surfaceColorHex, Color.White)
    val SurfaceSubtle: Color = Color(0xFFF8F8F8)
    val Placeholder: Color = Color(0xFFF2F4F7)

    // Text
    val TextPrimary: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.textColorHex, SushiInk)
    val TextSecondary: Color = Color(0xFF565959)
    val TextMuted: Color = Color(0xFF8B8F96)
    val NeutralDark: Color = SushiInk
    val NeutralLight: Color = Color(0xFF8B8F96)

    // Border
    val Border: Color = Color(0xFFE8ECEF)

    // Hero / marketing dark
    val HeroDark: Color = Color(0xFF0B132B)
    val HeroOnDark: Color = Color(0xFFF8FAFC)
    val HeroMutedOnDark: Color = Color(0xFF94A3B8)

    // Ratings / accents
    val Rating: Color = SpeedYellow

    // ---- Commerce-specific semantic tokens (driven by ClientTheme) ----

    // Accent
    val Accent: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.accentColorHex, Color(0xFF4F46E5))

    // Discount / offer
    val Discount: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.discountColorHex, Color(0xFF16A34A))
    val DiscountSoft: Color = Color(0xFFEAF8E8)
    val DiscountBorder: Color = Color(0xFFBEEBBF)

    // Badge
    val Badge: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.badgeColorHex, Color(0xFF16A34A))

    // CTA
    val CTA: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.ctaColorHex, Color(0xFF1E88E5))

    // Delivery promise / ETA
    val Delivery: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.deliveryColorHex, Color(0xFF0284C7))
    val DeliverySoft: Color = Color(0xFFEAF8E8)

    // Vertical Accent
    val VerticalAccent: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.verticalAccentColorHex, Color(0xFF5C6BC0))

    // Savings shown to the customer ("You save ₹X")
    val Savings: Color = QuickGreenDark
    val SavingsSoft: Color = Color(0xFFEAF8E8)

    // Prescription required (Rx)
    val Rx: Color = Color(0xFFDC2626)
    val RxSoft: Color = Color(0xFFFEE2E2)

    // Cold-chain / temperature-controlled fulfillment
    val ColdChain: Color = Color(0xFF0D9488)
    val ColdChainSoft: Color = Color(0xFFCCFBF1)

    // Out-of-stock / unavailable
    val OutOfStock: Color = Color(0xFF64748B)
    val OutOfStockSoft: Color = Color(0xFFF1F5F9)

    // Pharmacist verification / pending review
    val Verification: Color = Color(0xFF7C3AED)
    val VerificationSoft: Color = Color(0xFFEDE9FE)
}

/** Spacing scale. Screens should prefer these tokens over ad-hoc dp values. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
}

/**
 * Radius scale frozen tokens.
 */
object Radius {
    val Micro = 4.dp
    val Controls = 8.dp
    val Chip = 8.dp
    val Button = 10.dp
    val Card = 12.dp
    val CardLarge = 14.dp
    val ImageTile = 10.dp
    val Hero = 18.dp
    val Sheet = 20.dp
    val Pill = 999.dp

    val sm = 8.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 18.dp
}

/**
 * Typography scale.
 */
object CommerceTypography {
    val Display = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
    val Heading = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
    val HeroTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val ProductTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
    val BodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val BodySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val Label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val Meta = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)

    val Price = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
    val PriceLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
}

/** Motion tokens (milliseconds). */
object CommerceMotion {
    const val Fast = 150
    const val Standard = 250
    const val Emphasized = 400
}

/** Elevation / border depth language. */
object CommerceElevation {
    val Flat = 0.dp
    val Raised = 1.dp
    val Floating = 8.dp
}

private val baseTypography = Typography(
    headlineMedium = CommerceTypography.Heading,
    titleLarge = CommerceTypography.Title,
    titleMedium = CommerceTypography.Title.copy(fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = CommerceTypography.Label,
    bodyLarge = CommerceTypography.BodyLarge,
    bodyMedium = CommerceTypography.Body,
    bodySmall = CommerceTypography.BodySmall,
    labelLarge = CommerceTypography.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = CommerceTypography.Caption,
    labelSmall = CommerceTypography.Meta
)

fun parseHexColor(hex: String, defaultColor: Color): Color {
    return try {
        val clean = hex.removePrefix("#").trim()
        val argb = if (clean.length == 6) {
            (0xFF000000.toLong() or clean.toLong(16)).toInt()
        } else if (clean.length == 8) {
            clean.toLong(16).toInt()
        } else return defaultColor
        Color(argb)
    } catch (_: Throwable) {
        defaultColor
    }
}

/** App theme: mapped dynamically to LocalClientConfiguration active client theme tokens. */
@Composable
fun CommerceTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val clientConfig = LocalClientConfiguration.current
    val primaryColor = parseHexColor(clientConfig.theme.primaryColorHex, Color(0xFF16A34A))
    val secondaryColor = parseHexColor(clientConfig.theme.secondaryColorHex, Color(0xFF0B132B))
    val backgroundColor = parseHexColor(clientConfig.theme.backgroundColorHex, CommerceColors.HomeGrid)
    val surfaceColor = parseHexColor(clientConfig.theme.surfaceColorHex, Color.White)
    val textColor = parseHexColor(clientConfig.theme.textColorHex, Color(0xFF1A1A1A))
    val errorColor = parseHexColor(clientConfig.theme.errorColorHex, Color(0xFFDC2626))

    val scheme = if (darkTheme && clientConfig.theme.isDarkModeSupported) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            secondary = secondaryColor,
            onSecondary = Color.White,
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            error = errorColor,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            secondary = secondaryColor,
            onSecondary = Color.White,
            background = backgroundColor,
            surface = surfaceColor,
            error = errorColor,
            onSurface = textColor
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = baseTypography,
        content = content
    )
}
