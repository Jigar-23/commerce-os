package com.commerceos.android.config

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfigPersistenceManagerTest {

    private lateinit var persistenceManager: ConfigPersistenceManager

    @Before
    fun setUp() {
        persistenceManager = ConfigPersistenceManager()
        persistenceManager.clearCache()
    }

    @Test
    fun testSaveAndGetActiveClientId() {
        persistenceManager.saveActiveClientId("fashion_luxe")
        assertEquals("fashion_luxe", persistenceManager.getActiveClientId())
    }

    @Test
    fun testSaveAndGetLastKnownGood() {
        val config = ClientConfiguration.PharmacyClient
        persistenceManager.saveLastKnownGood(config)
        
        val lkg = persistenceManager.getLastKnownGood()
        assertNotNull(lkg)
        assertEquals("rx_pharma", lkg?.identity?.clientId)
        assertEquals("rx_pharma", persistenceManager.getActiveClientId())
    }

    @Test
    fun testCacheConfigAndExpiry() {
        val config = ClientConfiguration.FoodClient
        persistenceManager.cacheConfig(config, durationMs = 1000)
        
        assertFalse(persistenceManager.isCacheExpired())
        assertEquals("food_bistro", persistenceManager.getCachedConfig()?.identity?.clientId)
    }

    @Test
    fun testConfigMigration_upgradesVersionNumber() {
        persistenceManager.saveConfigVersion(1)
        val newerConfig = ClientConfiguration.DefaultGeneric.copy(version = 2)
        
        val migrated = persistenceManager.migrateIfNeeded(newerConfig)
        assertEquals(2, migrated.version)
        assertEquals(2, persistenceManager.getConfigVersion())
    }
}
