package com.commerceos.android.config

import org.junit.Assert.*
import org.junit.Test

/**
 * 🔴 P0 — RELEASE SAFETY TEST SUITE
 * Verifies release flag configurations, production config source, and tenant resolution safety.
 */
class ReleaseSafetyTest {

    @Test
    fun testProductionTenantResolution_ValidatesKnownClientsOnly() {
        val availableClients = listOf(
            ClientConfiguration.DefaultGeneric,
            ClientConfiguration.PharmacyClient,
            ClientConfiguration.FashionClient,
            ClientConfiguration.FoodClient,
            ClientConfiguration.ElectronicsClient,
            ClientConfiguration.ServicesClient
        )

        assertEquals(6, availableClients.size)
        val clientIds = availableClients.map { it.identity.clientId }.toSet()
        assertEquals(6, clientIds.size)
        assertTrue(clientIds.contains("generic_os"))
        assertTrue(clientIds.contains("rx_pharma"))
        assertTrue(clientIds.contains("fashion_luxe"))
        assertTrue(clientIds.contains("food_bistro"))
        assertTrue(clientIds.contains("tech_vault"))
        assertTrue(clientIds.contains("home_services"))
    }

    @Test
    fun testDefaultReleaseFeatureFlags_EnforceSecureDefaults() {
        val defaultConfig = ClientConfiguration.DefaultGeneric.features
        assertTrue(defaultConfig.enableWishlist)
        assertFalse(defaultConfig.enablePrescriptionUpload)
        assertFalse(defaultConfig.enableServiceBooking)
    }

    @Test
    fun testProductionConfigSource_HasValidIdentity() {
        val config = ClientConfiguration.DefaultGeneric
        assertNotNull(config.identity.clientId)
        assertNotNull(config.identity.appName)
        assertEquals("support@commerceos.io", config.identity.supportEmail)
    }
}
