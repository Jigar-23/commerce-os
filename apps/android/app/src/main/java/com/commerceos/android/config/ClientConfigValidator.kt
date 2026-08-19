package com.commerceos.android.config

data class ConfigValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

interface ConfigValidator {
    fun validate(config: ClientConfiguration): ConfigValidationResult
}

/**
 * Default implementation of Client Configuration Validator.
 * Verifies identity schemas, theme contrast, feature rules, and versioning.
 */
class ClientConfigValidator : ConfigValidator {

    override fun validate(config: ClientConfiguration): ConfigValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Identity validation
        if (config.identity.clientId.isBlank()) {
            errors.add("Client ID cannot be blank.")
        }
        if (config.identity.clientName.isBlank()) {
            errors.add("Client name cannot be blank.")
        }
        if (config.identity.appName.isBlank()) {
            errors.add("App name cannot be blank.")
        }
        if (!config.identity.supportEmail.contains("@")) {
            warnings.add("Support email '${config.identity.supportEmail}' does not appear to be valid.")
        }

        // 2. Feature rule validation
        if (config.features.enablePrescriptionUpload && config.domain != CommerceDomain.PHARMACY && config.domain != CommerceDomain.GENERAL_COMMERCE) {
            warnings.add("Prescription upload is enabled for non-pharmacy domain '${config.domain}'.")
        }
        if (config.features.enableServiceBooking && config.domain != CommerceDomain.SERVICES && config.domain != CommerceDomain.GENERAL_COMMERCE) {
            warnings.add("Service booking is enabled for non-services domain '${config.domain}'.")
        }

        // 3. Theme Contrast validation
        val contrastReport = ThemeContrastValidator.validateThemeContrast(config.theme)
        if (!contrastReport.isValid) {
            warnings.addAll(contrastReport.warningMessages)
        }

        // 4. Versioning & Version validation
        if (config.version < 1) {
            errors.add("Configuration version must be >= 1.")
        }

        return ConfigValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
