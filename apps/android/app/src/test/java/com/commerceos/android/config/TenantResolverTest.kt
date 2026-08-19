package com.commerceos.android.config

import org.junit.Assert.*
import org.junit.Test

class TenantResolverTest {

    private val resolver = DefaultTenantResolver()

    @Test
    fun testProductionResolution_returnsDefaultOrSystemTenant() {
        val config = resolver.resolveClientConfig(TenantEnvironment.PRODUCTION)
        assertEquals("generic_os", config.identity.clientId)
    }

    @Test
    fun testStagingResolution_appendsStagingFlagToAppName() {
        val config = resolver.resolveClientConfig(TenantEnvironment.STAGING, tenantIdOverride = "rx_pharma")
        assertEquals("rx_pharma", config.identity.clientId)
        assertTrue(config.identity.appName.contains("(Staging)"))
    }

    @Test
    fun testTenantOverride_resolvesSpecificClientProfile() {
        val config = resolver.resolveClientConfig(TenantEnvironment.PRODUCTION, tenantIdOverride = "fashion_luxe")
        assertEquals("fashion_luxe", config.identity.clientId)
        assertEquals("Vogue OS", config.identity.clientName)
    }
}
