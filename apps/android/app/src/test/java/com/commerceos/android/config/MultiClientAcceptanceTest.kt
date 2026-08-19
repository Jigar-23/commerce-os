package com.commerceos.android.config

import com.commerceos.android.registry.ClientCardRegistry
import com.commerceos.android.registry.ClientPresentationRegistry
import com.commerceos.android.registry.WorkflowRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 🔴 P0 — MULTI-CLIENT ACCEPTANCE & ZERO LEAKAGE TEST SUITE
 * Verifies full white-label client switching pipeline across all 6 domain verticals:
 * Generic -> Pharmacy -> Fashion -> Food -> Electronics -> Services -> Generic
 */
class MultiClientAcceptanceTest {

    @Before
    fun setUp() {
        ClientConfigProvider.switchClientConfig(ClientConfiguration.DefaultGeneric)
    }

    @Test
    fun testFullMultiClientSwitchSequence_NoBrandingOrTerminologyLeakage() {
        // 1. Start with Generic
        var current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.GENERAL_COMMERCE, current.domain)
        assertEquals("generic_os", current.identity.clientId)
        assertEquals("Cart", current.terminology.cartLabel)
        assertEquals("Place Order", WorkflowRegistry.resolveWorkflowRules(current).checkoutCtaLabel)

        // 2. Generic -> Pharmacy
        ClientConfigProvider.switchClientConfig(ClientConfiguration.PharmacyClient)
        current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.PHARMACY, current.domain)
        assertEquals("rx_pharma", current.identity.clientId)
        assertEquals("MediCare Express", current.identity.clientName)
        assertEquals("Health Basket", current.terminology.cartLabel)
        assertEquals("Search medicines, healthcare & Rx products...", current.terminology.searchPlaceholder)
        assertTrue(current.features.enablePrescriptionUpload)
        assertEquals("#00897B", current.theme.primaryColorHex)
        val rxWorkflow = WorkflowRegistry.resolveWorkflowRules(current)
        assertTrue(rxWorkflow.requiresPrescriptionUpload)
        assertFalse(rxWorkflow.supportsSizeSelector)

        // Verify NO Generic leakage
        assertNotEquals("Cart", current.terminology.cartLabel)
        assertNotEquals("generic_os", current.identity.clientId)

        // 3. Pharmacy -> Fashion
        ClientConfigProvider.switchClientConfig(ClientConfiguration.FashionClient)
        current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.FASHION, current.domain)
        assertEquals("fashion_luxe", current.identity.clientId)
        assertEquals("Vogue OS", current.identity.clientName)
        assertEquals("Shopping Bag", current.terminology.cartLabel)
        assertEquals("Favorites", current.terminology.wishlistLabel)
        assertEquals("#D81B60", current.theme.primaryColorHex)
        val fashionWorkflow = WorkflowRegistry.resolveWorkflowRules(current)
        assertFalse(fashionWorkflow.requiresPrescriptionUpload)
        assertTrue(fashionWorkflow.supportsSizeSelector)

        // Verify NO Pharmacy leakage
        assertFalse(current.features.enablePrescriptionUpload)
        assertNotEquals("Health Basket", current.terminology.cartLabel)
        assertNotEquals("MediCare Express", current.identity.clientName)

        // 4. Fashion -> Food
        ClientConfigProvider.switchClientConfig(ClientConfiguration.FoodClient)
        current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.FOOD, current.domain)
        assertEquals("food_bistro", current.identity.clientId)
        assertEquals("Gourmet OS", current.identity.clientName)
        assertEquals("Food Tray", current.terminology.cartLabel)
        assertEquals("Search dishes, kitchens and cuisines...", current.terminology.searchPlaceholder)
        assertEquals("#E65100", current.theme.primaryColorHex)
        val foodWorkflow = WorkflowRegistry.resolveWorkflowRules(current)
        assertFalse(foodWorkflow.requiresPrescriptionUpload)
        assertFalse(foodWorkflow.supportsSizeSelector)
        assertTrue(foodWorkflow.supportsExpressDelivery)

        // Verify NO Fashion leakage
        assertNotEquals("Shopping Bag", current.terminology.cartLabel)
        assertNotEquals("Vogue OS", current.identity.clientName)

        // 5. Food -> Electronics
        ClientConfigProvider.switchClientConfig(ClientConfiguration.ElectronicsClient)
        current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.ELECTRONICS, current.domain)
        assertEquals("tech_vault", current.identity.clientId)
        assertEquals("TechVault OS", current.identity.clientName)
        assertEquals("Cart", current.terminology.cartLabel)
        assertTrue(current.features.enableProductComparison)
        assertEquals("#1565C0", current.theme.primaryColorHex)
        val electronicsWorkflow = WorkflowRegistry.resolveWorkflowRules(current)
        assertTrue(electronicsWorkflow.supportsProductComparison)

        // Verify NO Food leakage
        assertNotEquals("Food Tray", current.terminology.cartLabel)
        assertNotEquals("Gourmet OS", current.identity.clientName)

        // 6. Electronics -> Services
        ClientConfigProvider.switchClientConfig(ClientConfiguration.ServicesClient)
        current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.SERVICES, current.domain)
        assertEquals("home_services", current.identity.clientId)
        assertEquals("FixIt Pro OS", current.identity.clientName)
        assertEquals("Bookings", current.terminology.cartLabel)
        assertEquals("Saved Services", current.terminology.wishlistLabel)
        assertTrue(current.features.enableServiceBooking)
        assertEquals("#3F51B5", current.theme.primaryColorHex)
        val servicesWorkflow = WorkflowRegistry.resolveWorkflowRules(current)
        assertTrue(servicesWorkflow.supportsTimeSlotBooking)
        assertFalse(servicesWorkflow.supportsExpressDelivery)

        // Verify NO Electronics leakage
        assertNotEquals("tech_vault", current.identity.clientId)
        assertFalse(current.features.enableProductComparison)

        // 7. Services -> Generic
        ClientConfigProvider.switchClientConfig(ClientConfiguration.DefaultGeneric)
        current = ClientConfigProvider.activeConfig()
        assertEquals(CommerceDomain.GENERAL_COMMERCE, current.domain)
        assertEquals("generic_os", current.identity.clientId)
        assertEquals("Commerce OS", current.identity.clientName)
        assertEquals("Cart", current.terminology.cartLabel)
        assertFalse(current.features.enableServiceBooking)
        assertFalse(current.features.enablePrescriptionUpload)

        // Verify NO Services leakage
        assertNotEquals("Bookings", current.terminology.cartLabel)
        assertNotEquals("FixIt Pro OS", current.identity.clientName)
    }

    @Test
    fun testPresentationAndCardRegistry_RespondsToClientSwitching() {
        // Test Pharmacy Presentation
        val pharmaPresentation = ClientPresentationRegistry.resolvePresentation("health", ClientConfiguration.PharmacyClient)
        assertEquals("💊", pharmaPresentation.visualSymbol)
        assertEquals("Healthcare & Rx", pharmaPresentation.catalogHeader)

        // Test Fashion Presentation
        val fashionPresentation = ClientPresentationRegistry.resolvePresentation("style", ClientConfiguration.FashionClient)
        assertEquals("👔", fashionPresentation.visualSymbol)
        assertEquals("Style & Apparel", fashionPresentation.catalogHeader)

        // Test Food Presentation
        val foodPresentation = ClientPresentationRegistry.resolvePresentation("food", ClientConfiguration.FoodClient)
        assertEquals("🍽️", foodPresentation.visualSymbol)
        assertEquals("Cuisines & Dining", foodPresentation.catalogHeader)
    }
}
