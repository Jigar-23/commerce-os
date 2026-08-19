package com.commerceos.android.config

import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResult
import com.commerceos.android.registry.CardVariant
import org.junit.Assert.*
import org.junit.Test

/**
 * 🔴 P0 — SEARCH CONFIG & DATA INTEGRITY TEST SUITE
 * Verifies ClientSearchConfiguration properties, history rules, capabilities, and strict search data integrity.
 */
class SearchConfigurationTest {

    @Test
    fun testSearchConfiguration_DefaultsAndCapabilities() {
        val searchConfig = ClientSearchConfiguration(
            searchPlaceholder = "Search medicine & health needs...",
            voiceCapabilityEnabled = true,
            imageCapabilityEnabled = false,
            barcodeCapabilityEnabled = true
        )

        assertEquals("Search medicine & health needs...", searchConfig.searchPlaceholder)
        assertTrue(searchConfig.voiceCapabilityEnabled)
        assertFalse(searchConfig.imageCapabilityEnabled)
        assertTrue(searchConfig.barcodeCapabilityEnabled)
        assertTrue(searchConfig.isEntityEnabled(SearchEntityType.PRODUCT))
    }

    @Test
    fun testSearchDataIntegrity_StrictExpressEligibility() {
        val expressResult = SearchResult(entityId = "1", title = "Express Item", entityType = SearchEntityType.PRODUCT, isExpressEligible = true)
        val nonExpressResult = SearchResult(entityId = "2", title = "Standard Item", entityType = SearchEntityType.PRODUCT, isExpressEligible = false)
        val unknownExpressResult = SearchResult(entityId = "3", title = "Unknown Item", entityType = SearchEntityType.PRODUCT, isExpressEligible = null)

        assertEquals(true, expressResult.isExpressEligible)
        assertEquals(false, nonExpressResult.isExpressEligible)
        assertNull(unknownExpressResult.isExpressEligible)
    }

    @Test
    fun testDataIntegrity_MissingFieldsOmitted() {
        val result = SearchResult(
            entityId = "p101",
            title = "Raw Medicine",
            entityType = SearchEntityType.PRODUCT,
            rating = null,
            cuisine = null,
            providerId = null,
            etaLabel = null
        )

        assertNull(result.rating)
        assertNull(result.cuisine)
        assertNull(result.providerId)
        assertNull(result.etaLabel)
    }
}
