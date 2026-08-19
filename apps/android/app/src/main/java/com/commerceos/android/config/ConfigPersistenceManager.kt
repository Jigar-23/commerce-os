package com.commerceos.android.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistence, caching, version tracking, last-known-good storage,
 * config expiry, and migration for ClientConfiguration.
 */
class ConfigPersistenceManager(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("commerceos_client_config_prefs", Context.MODE_PRIVATE)

    // Memory cache fallback for unit test environments where Context is null
    private var inMemoryActiveClientId: String? = null
    private var inMemoryConfigVersion: Int = 1
    private var inMemoryExpiryTimestamp: Long = 0L
    private var inMemoryLastKnownGood: ClientConfiguration? = null
    private var inMemoryCachedConfig: ClientConfiguration? = null

    companion object {
        const val KEY_ACTIVE_CLIENT_ID = "active_client_id"
        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_EXPIRY_TIMESTAMP = "config_expiry_timestamp"
        const val DEFAULT_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 Hours
    }

    fun saveActiveClientId(clientId: String) {
        inMemoryActiveClientId = clientId
        prefs?.edit()?.putString(KEY_ACTIVE_CLIENT_ID, clientId)?.apply()
    }

    fun getActiveClientId(): String? {
        return prefs?.getString(KEY_ACTIVE_CLIENT_ID, null) ?: inMemoryActiveClientId
    }

    fun saveConfigVersion(version: Int) {
        inMemoryConfigVersion = version
        prefs?.edit()?.putInt(KEY_CONFIG_VERSION, version)?.apply()
    }

    fun getConfigVersion(): Int {
        return prefs?.getInt(KEY_CONFIG_VERSION, 1) ?: inMemoryConfigVersion
    }

    fun saveLastKnownGood(config: ClientConfiguration) {
        inMemoryLastKnownGood = config
        saveActiveClientId(config.identity.clientId)
        saveConfigVersion(config.version)
    }

    fun getLastKnownGood(): ClientConfiguration? {
        return inMemoryLastKnownGood
    }

    fun cacheConfig(config: ClientConfiguration, durationMs: Long = DEFAULT_CACHE_DURATION_MS) {
        inMemoryCachedConfig = config
        inMemoryExpiryTimestamp = System.currentTimeMillis() + durationMs
        prefs?.edit()?.putLong(KEY_EXPIRY_TIMESTAMP, inMemoryExpiryTimestamp)?.apply()
        saveLastKnownGood(config)
    }

    fun getCachedConfig(): ClientConfiguration? {
        if (isCacheExpired()) return null
        return inMemoryCachedConfig ?: inMemoryLastKnownGood
    }

    fun isCacheExpired(): Boolean {
        val expiry = prefs?.getLong(KEY_EXPIRY_TIMESTAMP, 0L) ?: inMemoryExpiryTimestamp
        if (expiry == 0L) return false
        return System.currentTimeMillis() > expiry
    }

    /**
     * Migration support for configuration versions.
     */
    fun migrateIfNeeded(config: ClientConfiguration): ClientConfiguration {
        val currentStoredVersion = getConfigVersion()
        if (config.version < currentStoredVersion) {
            // Rollback or preserve newer migration schema
            return inMemoryLastKnownGood ?: config
        }
        if (config.version > currentStoredVersion) {
            // Apply migration updates (e.g. updating version number)
            saveConfigVersion(config.version)
        }
        return config
    }

    fun clearCache() {
        inMemoryCachedConfig = null
        inMemoryExpiryTimestamp = 0L
        prefs?.edit()?.remove(KEY_EXPIRY_TIMESTAMP)?.apply()
    }
}
