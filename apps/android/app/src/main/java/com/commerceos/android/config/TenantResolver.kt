package com.commerceos.android.config

enum class TenantEnvironment {
    PRODUCTION,
    STAGING,
    DEBUG
}

interface TenantResolver {
    fun resolveTenantId(environment: TenantEnvironment): String
    fun resolveClientConfig(environment: TenantEnvironment, tenantIdOverride: String? = null): ClientConfiguration
}

/**
 * Tenant and Client Resolver supporting Production, Staging, and Debug environments.
 */
class DefaultTenantResolver(
    private val availableConfigs: Map<String, ClientConfiguration> = mapOf(
        ClientConfiguration.DefaultGeneric.identity.clientId to ClientConfiguration.DefaultGeneric,
        ClientConfiguration.PharmacyClient.identity.clientId to ClientConfiguration.PharmacyClient,
        ClientConfiguration.FashionClient.identity.clientId to ClientConfiguration.FashionClient,
        ClientConfiguration.FoodClient.identity.clientId to ClientConfiguration.FoodClient,
        ClientConfiguration.ElectronicsClient.identity.clientId to ClientConfiguration.ElectronicsClient,
        ClientConfiguration.ServicesClient.identity.clientId to ClientConfiguration.ServicesClient
    )
) : TenantResolver {

    override fun resolveTenantId(environment: TenantEnvironment): String {
        return when (environment) {
            TenantEnvironment.PRODUCTION -> System.getProperty("commerceos.tenant.id") ?: "generic_os"
            TenantEnvironment.STAGING -> System.getProperty("commerceos.staging.tenant.id") ?: "rx_pharma"
            TenantEnvironment.DEBUG -> System.getProperty("commerceos.debug.tenant.id") ?: "generic_os"
        }
    }

    override fun resolveClientConfig(environment: TenantEnvironment, tenantIdOverride: String?): ClientConfiguration {
        val targetTenantId = tenantIdOverride ?: resolveTenantId(environment)
        val resolvedConfig = availableConfigs[targetTenantId] ?: availableConfigs["generic_os"] ?: ClientConfiguration.DefaultGeneric
        
        return if (environment == TenantEnvironment.STAGING) {
            // Append staging identifier to app name if in staging mode
            resolvedConfig.copy(
                identity = resolvedConfig.identity.copy(
                    appName = "${resolvedConfig.identity.appName} (Staging)"
                )
            )
        } else {
            resolvedConfig
        }
    }
}
