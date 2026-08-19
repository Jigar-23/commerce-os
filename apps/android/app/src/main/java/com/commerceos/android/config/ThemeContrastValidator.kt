package com.commerceos.android.config

data class ContrastResult(
    val contrastRatio: Double,
    val isWcagAaCompliant: Boolean,
    val isWcagAaaCompliant: Boolean,
    val pairName: String
)

data class ThemeContrastReport(
    val isValid: Boolean,
    val results: List<ContrastResult>,
    val warningMessages: List<String> = emptyList()
)

/**
 * Utility to calculate relative luminance and contrast ratio for color pairs.
 * Ensures ClientTheme complies with WCAG accessibility guidelines in pure Kotlin without Android framework dependencies.
 */
object ThemeContrastValidator {

    fun parseColorHex(hex: String, fallback: Int = 0xFF000000.toInt()): Int {
        return try {
            val clean = hex.removePrefix("#").trim()
            if (clean.length == 6) {
                (0xFF000000.toLong() or clean.toLong(16)).toInt()
            } else if (clean.length == 8) {
                clean.toLong(16).toInt()
            } else fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    /**
     * Calculates relative luminance of an RGB color according to WCAG 2.1 specifications.
     */
    fun calculateLuminance(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0

        val rL = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        val gL = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        val bL = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)

        return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
    }

    /**
     * Calculates WCAG contrast ratio between two colors (returns value between 1.0 and 21.0).
     */
    fun calculateContrastRatio(color1: Int, color2: Int): Double {
        val l1 = calculateLuminance(color1)
        val l2 = calculateLuminance(color2)
        val lighter = Math.max(l1, l2)
        val darker = Math.min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun validateContrastPair(fgHex: String, bgHex: String, pairName: String, minAaRatio: Double = 4.5): ContrastResult {
        val fg = parseColorHex(fgHex, 0xFF000000.toInt())
        val bg = parseColorHex(bgHex, 0xFFFFFFFF.toInt())
        val ratio = calculateContrastRatio(fg, bg)
        val isAa = ratio >= minAaRatio
        val isAaa = ratio >= 7.0
        return ContrastResult(
            contrastRatio = ratio,
            isWcagAaCompliant = isAa,
            isWcagAaaCompliant = isAaa,
            pairName = pairName
        )
    }

    fun validateThemeContrast(theme: ClientTheme): ThemeContrastReport {
        val results = mutableListOf<ContrastResult>()
        val warnings = mutableListOf<String>()

        // 1. Text vs Surface
        val textOnSurface = validateContrastPair(theme.textColorHex, theme.surfaceColorHex, "Text on Surface", 4.5)
        results.add(textOnSurface)
        if (!textOnSurface.isWcagAaCompliant) {
            warnings.add("Contrast ratio for Text (${theme.textColorHex}) on Surface (${theme.surfaceColorHex}) is ${String.format("%.2f", textOnSurface.contrastRatio)}:1, which is below 4.5:1 WCAG AA standard.")
        }

        // 2. Text vs Background
        val textOnBg = validateContrastPair(theme.textColorHex, theme.backgroundColorHex, "Text on Background", 4.5)
        results.add(textOnBg)
        if (!textOnBg.isWcagAaCompliant) {
            warnings.add("Contrast ratio for Text (${theme.textColorHex}) on Background (${theme.backgroundColorHex}) is ${String.format("%.2f", textOnBg.contrastRatio)}:1, which is below 4.5:1 WCAG AA standard.")
        }

        // 3. CTA text vs CTA Background
        val ctaOnWhite = validateContrastPair("#FFFFFF", theme.ctaColorHex, "White Text on CTA", 3.0)
        results.add(ctaOnWhite)

        val isValid = warnings.isEmpty()
        return ThemeContrastReport(
            isValid = isValid,
            results = results,
            warningMessages = warnings
        )
    }
}
