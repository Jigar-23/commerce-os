package com.commerceos.android

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.CommerceDomain
import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.registry.CardVariant
import com.commerceos.android.registry.ClientCardRegistry
import com.commerceos.android.registry.ClientPresentationRegistry
import com.commerceos.android.registry.WorkflowRegistry
import org.junit.Assert.*
import org.junit.Test

/**
 * 🟡 P2 — CODEBASE CLEANUP & MODULE ARCHITECTURE TEST SUITE
 * Verifies modular package separation (Configuration, Presentation Registry, Workflow Registry)
 * and card variant resolution dispatch without giant hardcoded when blocks.
 */
class CodebaseCleanupAuditTest {

    @Test
    fun testModularPackageStructure_ConfigurationModule() {
        val config = ClientConfiguration.PharmacyClient
        assertEquals(CommerceDomain.PHARMACY, config.domain)
        assertNotNull(config.identity)
        assertNotNull(config.theme)
        assertNotNull(config.terminology)
        assertNotNull(config.features)
    }

    @Test
    fun testModularPackageStructure_PresentationRegistryModule() {
        val presentation = ClientPresentationRegistry.resolvePresentation("health", ClientConfiguration.PharmacyClient)
        assertEquals("💊", presentation.visualSymbol)
    }

    @Test
    fun testModularPackageStructure_WorkflowRegistryModule() {
        val workflow = WorkflowRegistry.resolveWorkflowRules(ClientConfiguration.PharmacyClient)
        assertTrue(workflow.requiresPrescriptionUpload)
        assertEquals("Health Basket", workflow.cartTypeLabel)
    }

    @Test
    fun testCardRegistryDispatch_ReplacesGiantWhenBlocks() {
        val prodEntity = CommerceEntity.ProductItem(CommerceProduct(id = "p1", sku = "s1", name = "Shirt", price = 600.0, sellingPrice = 500.0))

        val fashionVariant = ClientCardRegistry.resolveCardVariant(prodEntity, ClientConfiguration.FashionClient)
        assertEquals(CardVariant.FASHION_PRODUCT, fashionVariant)

        val pharmaVariant = ClientCardRegistry.resolveCardVariant(prodEntity, ClientConfiguration.PharmacyClient)
        assertEquals(CardVariant.PHARMACY_PRODUCT, pharmaVariant)

        val techVariant = ClientCardRegistry.resolveCardVariant(prodEntity, ClientConfiguration.ElectronicsClient)
        assertEquals(CardVariant.ELECTRONICS_PRODUCT, techVariant)

        val genericVariant = ClientCardRegistry.resolveCardVariant(prodEntity, ClientConfiguration.DefaultGeneric)
        assertEquals(CardVariant.GENERIC_PRODUCT, genericVariant)
    }
}
