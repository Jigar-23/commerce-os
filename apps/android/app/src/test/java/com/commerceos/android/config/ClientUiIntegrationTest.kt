package com.commerceos.android.config

import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.navigation.AppDestinationRouter
import com.commerceos.android.registry.CardVariant
import com.commerceos.android.registry.ClientCardRegistry
import com.commerceos.android.registry.ClientPresentationRegistry
import com.commerceos.android.registry.WorkflowRegistry
import com.commerceos.android.ui.navigation.Screen
import com.commerceos.android.ui.theme.parseHexColor
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/**
 * 🔴 P0 — INTEGRATION TEST SUITE
 * Verifies integration between ClientConfigProvider, Theme colors, Terminology labels,
 * Feature flags, Card Registry, Presentation Registry, Workflow Registry, and navigation routing.
 */
class ClientUiIntegrationTest {

    @Test
    fun testClientConfigProvider_ToComposeThemeColors() {
        val config = ClientConfiguration.PharmacyClient
        val primaryColor = parseHexColor(config.theme.primaryColorHex, Color.Red)
        val backgroundColor = parseHexColor(config.theme.backgroundColorHex, Color.White)

        assertEquals("MediCare Express", config.identity.clientName)
        assertNotNull(primaryColor)
        assertNotNull(backgroundColor)
    }

    @Test
    fun testTerminologyConfig_ToActualLabels() {
        val fashionTerms = ClientConfiguration.FashionClient.terminology
        assertEquals("Shopping Bag", fashionTerms.cartLabel)
        assertEquals("Add to Bag", fashionTerms.productCtaLabel)

        val pharmaTerms = ClientConfiguration.PharmacyClient.terminology
        assertEquals("Health Basket", pharmaTerms.cartLabel)
        assertEquals("Prescriptions", pharmaTerms.prescriptionLabel)
    }

    @Test
    fun testFeatureFlags_ToActualComponentVisibility() {
        val genericFeatures = ClientConfiguration.DefaultGeneric.features
        assertFalse(genericFeatures.enablePrescriptionUpload)
        assertFalse(genericFeatures.enableServiceBooking)

        val pharmaFeatures = ClientConfiguration.PharmacyClient.features
        assertTrue(pharmaFeatures.enablePrescriptionUpload)
        assertFalse(pharmaFeatures.enableServiceBooking)

        val servicesFeatures = ClientConfiguration.ServicesClient.features
        assertFalse(servicesFeatures.enablePrescriptionUpload)
        assertTrue(servicesFeatures.enableServiceBooking)
    }

    @Test
    fun testCardRegistry_ToProductionCards() {
        val product = CommerceEntity.ProductItem(CommerceProduct(id = "p1", sku = "s1", name = "Jeans", price = 100.0, sellingPrice = 80.0))
        val fashionCard = ClientCardRegistry.resolveCardVariant(product, ClientConfiguration.FashionClient)
        val techCard = ClientCardRegistry.resolveCardVariant(product, ClientConfiguration.ElectronicsClient)

        assertEquals(CardVariant.FASHION_PRODUCT, fashionCard)
        assertEquals(CardVariant.ELECTRONICS_PRODUCT, techCard)
    }

    @Test
    fun testPresentationRegistry_ToVerticalHome() {
        val presentation = ClientPresentationRegistry.resolvePresentation("fashion", ClientConfiguration.FashionClient)
        assertEquals("👔", presentation.visualSymbol)
        assertEquals("Style & Apparel", presentation.catalogHeader)
    }

    @Test
    fun testWorkflowRegistry_ToNavigation() {
        val router = AppDestinationRouter()

        val genericScreen = router.resolve(com.commerceos.android.model.HomeDestination.Prescriptions, ClientConfiguration.DefaultGeneric)
        assertTrue(genericScreen is Screen.FeatureDisabled)

        val pharmaScreen = router.resolve(com.commerceos.android.model.HomeDestination.Prescriptions, ClientConfiguration.PharmacyClient)
        assertEquals(Screen.Prescriptions, pharmaScreen)
    }

    @Test
    fun testClientSwitch_ToCompleteUiTransformation() {
        val profileSequence = listOf(
            ClientConfiguration.DefaultGeneric,
            ClientConfiguration.PharmacyClient,
            ClientConfiguration.FashionClient,
            ClientConfiguration.FoodClient,
            ClientConfiguration.ElectronicsClient,
            ClientConfiguration.ServicesClient,
            ClientConfiguration.DefaultGeneric
        )

        for (clientConfig in profileSequence) {
            ClientConfigProvider.switchClientConfig(clientConfig)
            val active = ClientConfigProvider.activeConfig()

            assertEquals(clientConfig.identity.clientId, active.identity.clientId)
            assertEquals(clientConfig.domain, active.domain)
            assertNotNull(WorkflowRegistry.resolveWorkflowRules(active))
        }
    }
}
