package com.commerceos.android.config

/**
 * Client Theme & Color Branding Configuration.
 * Fully drives CommerceTheme and semantic token mapping across the app.
 * Aligned with the authoritative Commerce OS Universal Design System.
 */
data class ClientTheme(
    val primaryColorHex: String = "#16A34A",
    val secondaryColorHex: String = "#0B132B",
    val accentColorHex: String = "#4F46E5",
    val backgroundColorHex: String = "#F4F5F7",
    val surfaceColorHex: String = "#FFFFFF",
    val textColorHex: String = "#1C1C1C",
    val successColorHex: String = "#16A34A",
    val warningColorHex: String = "#E9BE3A",
    val errorColorHex: String = "#E11D48",
    val discountColorHex: String = "#16A34A",
    val badgeColorHex: String = "#16A34A",
    val ctaColorHex: String = "#16A34A",
    val deliveryColorHex: String = "#16A34A",
    val verticalAccentColorHex: String = "#4F46E5",
    val isDarkModeSupported: Boolean = true
)
