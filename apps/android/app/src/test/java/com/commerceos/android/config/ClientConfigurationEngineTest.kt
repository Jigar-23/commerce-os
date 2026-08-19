package com.commerceos.android.config

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying white-label ClientConfiguration engine functionality.
 */
class ClientConfigurationEngineTest {

    @Before
    fun setUp() {
        ClientConfigProvider.lockProductionTenantSwitching(false)
        ClientConfigProvider.switchClientConfig(ClientConfiguration.DefaultGeneric)
    }

    @Test
    fun testDefaultConfiguration_isGenericCommerce() {
        val config = ClientConfigProvider.activeConfig()
        assertEquals("generic_os", config.identity.clientId)
        assertEquals(CommerceDomain.GENERAL_COMMERCE, config.domain)
        assertEquals("Cart", config.terminology.cartLabel)
        assertTrue(config.features.enableWishlist)
        assertFalse(config.features.enablePrescriptionUpload)
    }

    @Test
    fun testPharmacyClientProfileSwitch_updatesBrandingAndTerminology() {
        ClientConfigProvider.switchClientConfig(ClientConfiguration.PharmacyClient)
        val config = ClientConfigProvider.activeConfig()

        assertEquals("rx_pharma", config.identity.clientId)
        assertEquals(CommerceDomain.PHARMACY, config.domain)
        assertEquals("Health Basket", config.terminology.cartLabel)
        assertTrue(config.features.enablePrescriptionUpload)
        assertEquals("#00897B", config.theme.primaryColorHex)
    }

    @Test
    fun testFashionClientProfileSwitch_updatesBrandingAndTerminology() {
        ClientConfigProvider.switchClientConfig(ClientConfiguration.FashionClient)
        val config = ClientConfigProvider.activeConfig()

        assertEquals("fashion_luxe", config.identity.clientId)
        assertEquals(CommerceDomain.FASHION, config.domain)
        assertEquals("Shopping Bag", config.terminology.cartLabel)
        assertEquals("Favorites", config.terminology.wishlistLabel)
        assertFalse(config.features.enablePrescriptionUpload)
        assertEquals("#D81B60", config.theme.primaryColorHex)
    }

    @Test
    fun testFoodClientProfileSwitch_updatesBrandingAndTerminology() {
        ClientConfigProvider.switchClientConfig(ClientConfiguration.FoodClient)
        val config = ClientConfigProvider.activeConfig()

        assertEquals("food_bistro", config.identity.clientId)
        assertEquals(CommerceDomain.FOOD, config.domain)
        assertEquals("Food Tray", config.terminology.cartLabel)
        assertFalse(config.features.enableWishlist)
        assertEquals("#E65100", config.theme.primaryColorHex)
    }

    @Test
    fun testServicesClientProfileSwitch_updatesBrandingAndTerminology() {
        ClientConfigProvider.switchClientConfig(ClientConfiguration.ServicesClient)
        val config = ClientConfigProvider.activeConfig()

        assertEquals("home_services", config.identity.clientId)
        assertEquals(CommerceDomain.SERVICES, config.domain)
        assertEquals("Bookings", config.terminology.cartLabel)
        assertTrue(config.features.enableServiceBooking)
        assertEquals("#3F51B5", config.theme.primaryColorHex)
    }

    @Test
    fun testBootstrap_resolvesConfigAndSetsSuccessState() {
        ClientConfigProvider.bootstrap(TenantEnvironment.STAGING, tenantIdOverride = "rx_pharma") { config ->
            assertEquals("rx_pharma", config.identity.clientId)
        }
        val state = ClientConfigProvider.configState.value
        assertTrue(state is ClientConfigState.Success)
    }

    @Test
    fun testInvalidConfig_fallsBackToLastKnownGood() {
        val invalidConfig = ClientConfiguration.DefaultGeneric.copy(
            identity = ClientConfiguration.DefaultGeneric.identity.copy(clientId = "")
        )
        ClientConfigProvider.switchClientConfig(invalidConfig)
        val state = ClientConfigProvider.configState.value
        assertTrue(state is ClientConfigState.Failure)
        assertEquals("generic_os", ClientConfigProvider.activeConfig().identity.clientId)
    }

    @Test(expected = SecurityException::class)
    fun testProductionSecurityGuard_preventsUnauthorizedSwitching() {
        ClientConfigProvider.setTenantEnvironment(TenantEnvironment.PRODUCTION)
        ClientConfigProvider.lockProductionTenantSwitching(true)
        ClientConfigProvider.switchClientConfig(ClientConfiguration.PharmacyClient)
    }
}
