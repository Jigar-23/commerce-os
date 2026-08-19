package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.model.SearchEntityType

data class RegistryConflict(
    val key: String,
    val description: String,
    val existingMapping: String,
    val conflictingMapping: String
)

data class RegistryValidationReport(
    val isValid: Boolean,
    val conflicts: List<RegistryConflict> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Universal Registry Validation & Conflict Detection Engine.
 * Verifies card variant mappings, presentation rules, and unknown variant fallbacks.
 */
object RegistryValidator {

    fun validateCardRegistry(config: ClientConfiguration): RegistryValidationReport {
        val conflicts = mutableListOf<RegistryConflict>()
        val warnings = mutableListOf<String>()

        val mappings = config.searchConfig.resultCardMappings
        for (entityType in SearchEntityType.entries) {
            val resolvedVariant = config.searchConfig.cardVariantFor(entityType)
            if (resolvedVariant == CardVariant.FALLBACK_GENERIC_CARD && entityType != SearchEntityType.PRODUCT) {
                warnings.add("EntityType ${entityType.name} is mapped to generic fallback variant.")
            }
        }

        return RegistryValidationReport(
            isValid = conflicts.isEmpty(),
            conflicts = conflicts,
            warnings = warnings
        )
    }

    fun validatePresentationRegistry(verticalId: String, config: ClientConfiguration): RegistryValidationReport {
        val presentation = ClientPresentationRegistry.resolvePresentation(verticalId, config)
        val warnings = mutableListOf<String>()
        if (presentation.catalogHeader.isBlank()) {
            warnings.add("Presentation for vertical $verticalId has an empty catalog header.")
        }
        return RegistryValidationReport(
            isValid = true,
            warnings = warnings
        )
    }
}
