package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🟡 P2 — REGISTRY VALIDATOR TEST SUITE
 */
class RegistryValidatorTest {

    @Test
    fun testValidateCardRegistry_ValidConfiguration() {
        val report = RegistryValidator.validateCardRegistry(ClientConfiguration.DefaultGeneric)
        assertTrue(report.isValid)
    }

    @Test
    fun testValidatePresentationRegistry_ValidConfiguration() {
        val report = RegistryValidator.validatePresentationRegistry("pharmacy", ClientConfiguration.PharmacyClient)
        assertTrue(report.isValid)
    }
}
