package com.commerceos.android.config

import org.junit.Assert.*
import org.junit.Test

class ThemeContrastValidatorTest {

    @Test
    fun testContrastRatio_blackOnWhite_isMaximum() {
        val contrast = ThemeContrastValidator.validateContrastPair("#000000", "#FFFFFF", "Black on White")
        assertTrue(contrast.contrastRatio >= 20.0)
        assertTrue(contrast.isWcagAaCompliant)
        assertTrue(contrast.isWcagAaaCompliant)
    }

    @Test
    fun testContrastRatio_whiteOnWhite_isMinimum() {
        val contrast = ThemeContrastValidator.validateContrastPair("#FFFFFF", "#FFFFFF", "White on White")
        assertEquals(1.0, contrast.contrastRatio, 0.1)
        assertFalse(contrast.isWcagAaCompliant)
    }

    @Test
    fun testValidateThemeContrast_defaultGenericTheme_isValid() {
        val report = ThemeContrastValidator.validateThemeContrast(ClientConfiguration.DefaultGeneric.theme)
        assertTrue(report.isValid)
    }

    @Test
    fun testValidateThemeContrast_lowContrastTheme_flagsWarning() {
        val lowContrastTheme = ClientTheme(
            textColorHex = "#EEEEEE",
            surfaceColorHex = "#FFFFFF"
        )
        val report = ThemeContrastValidator.validateThemeContrast(lowContrastTheme)
        assertFalse(report.isValid)
        assertTrue(report.warningMessages.isNotEmpty())
    }
}
