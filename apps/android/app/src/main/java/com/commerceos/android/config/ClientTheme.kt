package com.commerceos.android.config

/**
 * Client Theme & Color Branding Configuration.
 * Fully drives CommerceTheme and semantic token mapping across the app.
 * Aligned with the authoritative Commerce OS Universal Design System.
 */
data class ClientTheme(
    val primaryColorHex: String = "#0C831F",
    val secondaryColorHex: String = "#0B132B",
    val accentColorHex: String = "#4F46E5",
    val backgroundColorHex: String = "#F8F9FA",
    val surfaceColorHex: String = "#FFFFFF",
    val textColorHex: String = "#111827",
    val successColorHex: String = "#0C831F",
    val warningColorHex: String = "#F59E0B",
    val errorColorHex: String = "#DC2626",
    val discountColorHex: String = "#0C831F",
    val badgeColorHex: String = "#0C831F",
    val ctaColorHex: String = "#0C831F",
    val deliveryColorHex: String = "#0C831F",
    val verticalAccentColorHex: String = "#4F46E5",
    val isDarkModeSupported: Boolean = true
)
