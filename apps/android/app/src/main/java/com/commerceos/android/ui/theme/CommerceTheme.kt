package com.commerceos.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.R
import com.commerceos.android.config.LocalClientConfiguration

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Light),
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold)
)

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

/**
 * CommerceOS design tokens. Feature screens consume these tokens so brand/business meaning stays in
 * one place and dynamically adapts to the active ClientTheme.
 */
object CommerceColors {
    // Reference quick-commerce & healthcare palette tokens (Blinkit, Tata 1mg, Truemeds, PharmEasy).
    val QuickGreen: Color = Color(0xFF0C831F)
    val QuickGreenDark: Color = Color(0xFF096818)
    val QuickGreenLight: Color = Color(0xFFE8F5E9)
    val SpeedYellow: Color = Color(0xFFF59E0B)
    val HomeGrid: Color = Color(0xFFF8F9FA)
    val SushiGrey: Color = Color(0xFFF3F4F6)
    val SushiInk: Color = Color(0xFF111827)

    // Brand / primary
    val Primary: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.primaryColorHex, QuickGreen)
    
    val Secondary: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.secondaryColorHex, Color(0xFF0B132B))
    
    val PrimaryDark: Color = QuickGreenDark
    val PrimarySoft: Color = Color(0xFFE8F5E9)
    val OnPrimary: Color = Color.White

    // Success / positive
    val Success: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.successColorHex, QuickGreen)
    val SuccessDark: Color = QuickGreenDark
    val SuccessSoft: Color = Color(0xFFE8F5E9)
    val SuccessBorder: Color = Color(0xFFA7F3D0)

    // Warning / pending
    val Warning: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.warningColorHex, Color(0xFFF59E0B))
    val WarningSoft: Color = Color(0xFFFEF3C7)

    // Danger / error
    val Danger: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.errorColorHex, Color(0xFFDC2626))
    val DangerDark: Color = Color(0xFFB91C1C)
    val DangerSoft: Color = Color(0xFFFEE2E2)
    val DangerContainer: Color = Color(0xFFFFF1F2)

    // Info / informational
    val Info: Color = Color(0xFF2563EB)
    val InfoSoft: Color = Color(0xFFEFF6FF)
    val InfoContainer: Color = Color(0xFFEFF6FF)
    val InfoBorder: Color = Color(0xFFBFDBFE)

    val PrimaryContainer: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.primaryColorHex, QuickGreen).copy(alpha = 0.12f)

    // Surfaces
    val Background: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.backgroundColorHex, HomeGrid)
    val Surface: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.surfaceColorHex, Color.White)
    val SurfaceSubtle: Color = Color(0xFFF8F9FA)
    val Placeholder: Color = Color(0xFFF1F5F9)

    // Text
    val TextPrimary: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.textColorHex, SushiInk)
    val TextSecondary: Color = Color(0xFF4B5563)
    val TextMuted: Color = Color(0xFF9CA3AF)
    val NeutralDark: Color = SushiInk
    val NeutralLight: Color = Color(0xFF9CA3AF)

    // Border
    val Border: Color = Color(0xFFE5E7EB)

    // Hero / marketing dark
    val HeroDark: Color = Color(0xFF0F172A)
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
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.discountColorHex, QuickGreen)
    val DiscountSoft: Color = Color(0xFFE8F5E9)
    val DiscountBorder: Color = Color(0xFFA7F3D0)

    // Badge
    val Badge: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.badgeColorHex, QuickGreen)

    // CTA
    val CTA: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.ctaColorHex, QuickGreen)

    // Delivery promise / ETA
    val Delivery: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.deliveryColorHex, Color(0xFF0284C7))
    val DeliverySoft: Color = Color(0xFFE0F2FE)

    // Vertical Accent
    val VerticalAccent: Color
        @Composable get() = parseHexColor(LocalClientConfiguration.current.theme.verticalAccentColorHex, Color(0xFF5C6BC0))

    // Savings shown to the customer ("You save ₹X")
    val Savings: Color = QuickGreenDark
    val SavingsSoft: Color = Color(0xFFE8F5E9)

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
 * Typography scale powered by Plus Jakarta Sans.
 * Premium, geometric, ultra-legible typography matching Blinkit, Tata 1mg, and modern quick commerce.
 */
object CommerceTypography {
    val Display = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    )
    val Heading = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    )
    val HeroTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    )
    val Title = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    )
    val ProductTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp
    )
    val BodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    )
    val Body = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )
    val BodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )
    val Label = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp
    )
    val Caption = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 15.sp
    )
    val Meta = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp
    )

    val Price = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    )
    val PriceLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    )
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
    displayLarge = CommerceTypography.Display,
    displayMedium = CommerceTypography.Display.copy(fontSize = 26.sp, lineHeight = 32.sp),
    displaySmall = CommerceTypography.Heading,
    headlineLarge = CommerceTypography.Heading,
    headlineMedium = CommerceTypography.HeroTitle,
    headlineSmall = CommerceTypography.Title,
    titleLarge = CommerceTypography.Title,
    titleMedium = CommerceTypography.Title.copy(fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = CommerceTypography.Label,
    bodyLarge = CommerceTypography.BodyLarge,
    bodyMedium = CommerceTypography.Body,
    bodySmall = CommerceTypography.BodySmall,
    labelLarge = CommerceTypography.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
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

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            val isDark = darkTheme && clientConfig.theme.isDarkModeSupported
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = baseTypography,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides TextStyle(
                    fontFamily = PlusJakartaSans,
                    color = textColor
                ),
                content = content
            )
        }
    )
}
